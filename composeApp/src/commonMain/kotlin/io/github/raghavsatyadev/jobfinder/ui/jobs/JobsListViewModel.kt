package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The job feed: one status tab, its jobs page by page, the filters and the tab counts. */
@Immutable
data class JobsListUiState(
  val status: JobStatus = JobStatus.NEW,
  val counts: ImmutableMap<JobStatus, Int> = persistentMapOf(),
  /** `null` until the first page arrives, so the empty state never flashes. */
  val jobs: ImmutableList<Job>? = null,
  val loading: Boolean = true,
  val loadingMore: Boolean = false,
  /** A pull-to-refresh reload is running. */
  val refreshing: Boolean = false,
  /** The last load failed. With [jobs] present the old jobs stay on screen, drawn as stale. */
  val failed: Boolean = false,
  val nextPageToken: String? = null,
  val searchText: String = "",
  /** The sheet's filters; the search text is kept apart in [searchText]. */
  val filter: JobFilter = JobFilter(),
  val sites: ImmutableList<Site> = persistentListOf(),
  val links: ImmutableList<Link> = persistentListOf(),
  val scan: ScanState = ScanState(),
) {
  /** How many of the sheet's filter groups are in use, for the filter button badge. */
  val activeFilterCount: Int
    get() =
      listOf(
          filter.siteIds,
          filter.linkIds,
          filter.labels,
          filter.workModes,
          filter.applyMethods,
        )
        .count { it.isNotEmpty() }

  val isFiltered: Boolean
    get() = activeFilterCount > 0 || searchText.isNotBlank()

  /** Old jobs shown while a reload runs or after it failed. */
  val isStale: Boolean
    get() = jobs != null && (failed || (loading && !loadingMore))
}

sealed interface JobsListEvent {
  data class SelectTab(val status: JobStatus) : JobsListEvent

  data class Search(val text: String) : JobsListEvent

  data class ApplyFilter(val filter: JobFilter) : JobsListEvent

  data object ClearFilters : JobsListEvent

  data class SetSort(val sort: JobSort) : JobsListEvent

  data object LoadMore : JobsListEvent

  /** Reloads the tab and its counts (the old "Refresh"). */
  data object Refresh : JobsListEvent

  /** Starts a scan of every search. */
  data object ScanAll : JobsListEvent

  data object ArchiveAll : JobsListEvent

  data object DeleteAll : JobsListEvent

  data class ChangeStatus(val jobId: Long, val to: JobStatus) : JobsListEvent
}

/** A one-shot message for the snackbar. */
sealed interface JobsMessage {
  data class StatusChanged(val to: JobStatus) : JobsMessage

  data class ArchivedAll(val count: Int) : JobsMessage

  data class DeletedAll(val count: Int) : JobsMessage

  data object ScanStarted : JobsMessage

  data object ScanAlreadyRunning : JobsMessage

  data object Failed : JobsMessage
}

/** How long typing pauses before the search runs. */
internal const val SEARCH_DEBOUNCE_MILLIS = 300L

class JobsListViewModel(
  private val jobs: JobsRepository,
  private val sites: SitesRepository,
  private val links: LinksRepository,
  private val scan: ScanRepository,
  private val changes: JobChanges,
  private val filterStore: JobFilterStore = NoJobFilterStore,
  private val pageSize: Int = JobsRepository.DEFAULT_PAGE_SIZE,
) : ViewModel() {
  // The sort and filters chosen last time, on this device.
  private val _state = MutableStateFlow(JobsListUiState(filter = filterStore.load()))
  val state: StateFlow<JobsListUiState> = _state.asStateFlow()

  private val _messages = Channel<JobsMessage>(Channel.BUFFERED)
  val messages: Flow<JobsMessage> = _messages.receiveAsFlow()

  private var loadJob: CoroutineJob? = null
  private var searchJob: CoroutineJob? = null

  init {
    load()
    loadFilterOptions()
    viewModelScope.launch {
      var wasScanning = scan.state.value.scanning
      scan.state.collect { scanState ->
        _state.update { it.copy(scan = scanState) }
        // A finished scan brings new jobs: reload quietly.
        if (wasScanning && !scanState.scanning) load()
        wasScanning = scanState.scanning
      }
    }
    viewModelScope.launch {
      scan.events.collect { event ->
        when (event) {
          is ServerEvent.NewJobs,
          ServerEvent.CopyRefreshed,
          ServerEvent.ResumeChanged -> load()
          // A score arrived: show it on its card without reloading the page.
          is ServerEvent.AtsScored ->
            _state.update { s ->
              s.copy(
                jobs =
                  s.jobs
                    ?.map { if (it.id == event.jobId) it.copy(ats = event.summary) else it }
                    ?.toImmutableList()
              )
            }
          else -> Unit
        }
      }
    }
    viewModelScope.launch { changes.flow.collect(::onJobChange) }
  }

  fun onEvent(event: JobsListEvent) {
    when (event) {
      is JobsListEvent.SelectTab -> {
        if (event.status == _state.value.status) return
        _state.update {
          it.copy(status = event.status, jobs = null, nextPageToken = null, failed = false)
        }
        load()
      }
      is JobsListEvent.Search -> {
        _state.update { it.copy(searchText = event.text) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
          delay(SEARCH_DEBOUNCE_MILLIS)
          load()
        }
      }
      is JobsListEvent.ApplyFilter -> {
        // The sheet's filters; the order is the sort menu's.
        _state.update {
          it.copy(filter = event.filter.copy(search = null, sort = it.filter.sort))
        }
        filterStore.save(_state.value.filter)
        load()
      }
      JobsListEvent.ClearFilters -> {
        searchJob?.cancel()
        _state.update { it.copy(filter = JobFilter(sort = it.filter.sort), searchText = "") }
        filterStore.save(_state.value.filter)
        load()
      }
      is JobsListEvent.SetSort -> {
        if (event.sort == _state.value.filter.sort) return
        _state.update { it.copy(filter = it.filter.copy(sort = event.sort), nextPageToken = null) }
        filterStore.save(_state.value.filter)
        load()
      }
      JobsListEvent.LoadMore -> loadMore()
      JobsListEvent.Refresh -> {
        _state.update { it.copy(refreshing = true) }
        load()
        loadFilterOptions()
      }
      JobsListEvent.ScanAll -> scanAll()
      JobsListEvent.ArchiveAll -> changeAll(JobStatus.ARCHIVED)
      JobsListEvent.DeleteAll -> changeAll(JobStatus.DELETED)
      is JobsListEvent.ChangeStatus -> changeStatus(event.jobId, event.to)
    }
  }

  private fun effectiveFilter(s: JobsListUiState): JobFilter =
    s.filter.copy(search = s.searchText.trim().ifBlank { null })

  /** Loads the first page of the current tab and every tab count. Old jobs stay until it ends. */
  private fun load() {
    loadJob?.cancel()
    _state.update { it.copy(loading = true, loadingMore = false) }
    loadJob = viewModelScope.launch {
      val s = _state.value
      val filter = effectiveFilter(s)
      try {
        // One after the other: both are local or LAN calls, and a load that needs no wait then
        // ends in the frame that started it.
        val result = jobs.listJobs(s.status, filter, pageSize)
        val counts = jobs.countJobs(filter)
        _state.update {
          it.copy(
            jobs = result.jobs.toImmutableList(),
            nextPageToken = result.nextPageToken,
            counts = counts.byStatus.toImmutableMap(),
            loading = false,
            refreshing = false,
            failed = false,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loading = false, refreshing = false, failed = true) }
      }
    }
  }

  private fun refreshCounts() {
    viewModelScope.launch {
      try {
        val counts = jobs.countJobs(effectiveFilter(_state.value))
        _state.update { it.copy(counts = counts.byStatus.toImmutableMap()) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The counts stay as they were; the next load fixes them.
      }
    }
  }

  private fun loadMore() {
    val s = _state.value
    val token = s.nextPageToken ?: return
    if (s.loading || s.loadingMore) return
    _state.update { it.copy(loadingMore = true) }
    loadJob = viewModelScope.launch {
      try {
        val result = jobs.listJobs(s.status, effectiveFilter(s), pageSize, after = token)
        _state.update { current ->
          val known = current.jobs.orEmpty().map { it.id }.toSet()
          current.copy(
            jobs =
              (current.jobs.orEmpty() + result.jobs.filter { it.id !in known }).toImmutableList(),
            nextPageToken = result.nextPageToken,
            loadingMore = false,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loadingMore = false) }
        _messages.trySend(JobsMessage.Failed)
      }
    }
  }

  private fun loadFilterOptions() {
    viewModelScope.launch {
      try {
        val siteList = sites.listSites()
        val linkList = links.listLinks()
        val before = _state.value.filter
        _state.update {
          it.copy(
            sites = siteList.toImmutableList(),
            links = linkList.toImmutableList(),
            // A kept filter may name boards or searches deleted since; drop them.
            filter =
              if (siteList.isEmpty()) it.filter
              else
                it.filter.copy(
                  siteIds = it.filter.siteIds.filter { id -> siteList.any { s -> s.id == id } },
                  linkIds = it.filter.linkIds.filter { id -> linkList.any { l -> l.id == id } },
                ),
          )
        }
        val after = _state.value.filter
        if (after != before && siteList.isNotEmpty()) {
          filterStore.save(after)
          load()
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The sheet shows only the groups it has data for.
      }
    }
  }

  private fun changeStatus(jobId: Long, to: JobStatus) {
    val job = _state.value.jobs?.firstOrNull { it.id == jobId } ?: return
    if (job.status == to) return
    removeFromList(jobId)
    viewModelScope.launch {
      try {
        jobs.updateStatus(jobId, to)
        changes.publish(JobChange.StatusChanged(jobId, job.status, to))
        _messages.trySend(JobsMessage.StatusChanged(to))
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _messages.trySend(JobsMessage.Failed)
        load()
      }
    }
  }

  private fun removeFromList(jobId: Long) {
    _state.update { s ->
      val list = s.jobs ?: return@update s
      val removed = list.firstOrNull { it.id == jobId } ?: return@update s
      val counts = s.counts.toMutableMap()
      counts[removed.status] = ((counts[removed.status] ?: 0) - 1).coerceAtLeast(0)
      s.copy(
        jobs = list.filterNot { it.id == jobId }.toImmutableList(),
        counts = counts.toImmutableMap(),
      )
    }
    topUp()
  }

  /** Like the old app: when moving jobs out leaves less than half a page, load the next one. */
  private fun topUp() {
    val s = _state.value
    if ((s.jobs?.size ?: 0) < pageSize / 2 && s.nextPageToken != null) loadMore()
  }

  private fun onJobChange(change: JobChange) {
    when (change) {
      is JobChange.StatusChanged -> {
        removeFromList(change.jobId)
        if (change.to == _state.value.status) load() else refreshCounts()
      }
      is JobChange.Updated ->
        _state.update { s ->
          s.copy(
            jobs =
              s.jobs
                ?.map { if (it.id == change.job.id) change.job.copy(description = null) else it }
                ?.toImmutableList()
          )
        }
      is JobChange.BulkChanged -> load()
    }
  }

  private fun scanAll() {
    viewModelScope.launch {
      try {
        val started = scan.startScanAll()
        _messages.trySend(if (started) JobsMessage.ScanStarted else JobsMessage.ScanAlreadyRunning)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _messages.trySend(JobsMessage.Failed)
      }
    }
  }

  private fun changeAll(to: JobStatus) {
    val from = _state.value.status
    if (from == to) return
    viewModelScope.launch {
      try {
        val count = jobs.changeAllStatuses(from, to)
        changes.publish(JobChange.BulkChanged(from, to))
        _messages.trySend(
          if (to == JobStatus.DELETED) JobsMessage.DeletedAll(count)
          else JobsMessage.ArchivedAll(count)
        )
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _messages.trySend(JobsMessage.Failed)
      }
    }
  }
}
