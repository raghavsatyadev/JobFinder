package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.TimeSource
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
          // A score or a translation arrived: show it on its card without reloading the page.
          is ServerEvent.AtsScored -> patchCard(event.jobId) { it.copy(ats = event.summary) }
          is ServerEvent.TranslationReady ->
            patchCard(event.jobId) { it.copy(translation = event.translation) }
          else -> Unit
        }
      }
    }
    viewModelScope.launch { changes.flow.collect(::onJobChange) }
  }

  private fun patchCard(jobId: Long, change: (Job) -> Job) {
    _state.update { s ->
      s.copy(jobs = s.jobs?.map { if (it.id == jobId) change(it) else it }?.toImmutableList())
    }
  }

  fun onEvent(event: JobsListEvent) {
    when (event) {
      is JobsListEvent.SelectTab -> {
        if (event.status == _state.value.status && !_state.value.starred) return
        _state.update {
          it.copy(
            status = event.status,
            starred = false,
            jobs = null,
            nextPageToken = null,
            failed = false,
          )
        }
        load()
      }
      JobsListEvent.SelectStarred -> {
        if (_state.value.starred) return
        _state.update { it.copy(starred = true, jobs = null, nextPageToken = null, failed = false) }
        load()
      }
      is JobsListEvent.ToggleStar -> toggleStar(event.jobId)
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
        checkJobSites()
      }
      JobsListEvent.Reload -> {
        load()
        loadFilterOptions()
      }
      JobsListEvent.ArchiveAll -> changeAll(JobStatus.ARCHIVED)
      JobsListEvent.DeleteAll -> changeAll(JobStatus.DELETED)
      is JobsListEvent.ChangeStatus -> changeStatus(event.jobId, event.to)
    }
  }

  private fun effectiveFilter(s: JobsListUiState): JobFilter =
    s.filter.copy(search = s.searchText.trim().ifBlank { null })

  /** The filter of the open tab's list: the Starred tab lists only starred jobs. */
  private fun listFilter(s: JobsListUiState): JobFilter =
    effectiveFilter(s).copy(starred = s.starred)

  /** Starred jobs for [filter], over the tabs they show from. */
  private suspend fun starredCount(filter: JobFilter): Int {
    val counts = jobs.countJobs(filter.copy(starred = true))
    return JobFilter.STARRED_STATUSES.sumOf { counts[it] }
  }

  /** Loads the first page of the current tab and every tab count. Old jobs stay until it ends. */
  private fun load() {
    loadJob?.cancel()
    _state.update { it.copy(loading = true, loadingMore = false) }
    val started = TimeSource.Monotonic.markNow()
    loadJob = viewModelScope.launch {
      val s = _state.value
      val filter = effectiveFilter(s)
      try {
        // One after the other: both are local or LAN calls, and a load that needs no wait then
        // ends in the frame that started it.
        val result = jobs.listJobs(s.status, listFilter(s), pageSize)
        val counts = jobs.countJobs(filter)
        val starred = starredCount(filter)
        // A local reload ends in a frame: keep the spinner up long enough to be seen.
        if (s.refreshing) delay(MIN_REFRESH_MILLIS - started.elapsedNow().inWholeMilliseconds)
        _state.update {
          it.copy(
            jobs = result.jobs.toImmutableList(),
            nextPageToken = result.nextPageToken,
            counts = counts.byStatus.toImmutableMap(),
            starredCount = starred,
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
        val filter = effectiveFilter(_state.value)
        val counts = jobs.countJobs(filter)
        val starred = starredCount(filter)
        _state.update { it.copy(counts = counts.byStatus.toImmutableMap(), starredCount = starred) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The counts stay as they were; the next load fixes them.
      }
    }
  }

  /**
   * Asks the computer to scan every saved search. It refuses while a scan runs or within 4 hours of
   * the last Refresh (job sites block IP addresses that read them too often); say which.
   */
  private fun checkJobSites() {
    viewModelScope.launch {
      try {
        if (scan.startScanAll()) {
          _messages.trySend(JobsMessage.RefreshStarted)
          return@launch
        }
        val now = Clock.System.now().toEpochMilliseconds()
        val wait = scan.state.value.refreshAllowedAt?.minus(now) ?: 0
        _messages.trySend(
          when {
            scan.state.value.scanning -> JobsMessage.RefreshRunning
            wait > 0 -> JobsMessage.RefreshCooldown(wait)
            else -> JobsMessage.Failed
          }
        )
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _messages.trySend(JobsMessage.Failed)
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
        val result = jobs.listJobs(s.status, listFilter(s), pageSize, after = token)
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
    moveInList(jobId, to)
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

  /**
   * A job moved to [to]: it leaves its status tab. In the Starred tab it stays, with its new
   * status, unless it was deleted.
   */
  private fun moveInList(jobId: Long, to: JobStatus) {
    if (!_state.value.starred || to !in JobFilter.STARRED_STATUSES) {
      removeFromList(jobId)
      return
    }
    _state.update { s ->
      val list = s.jobs ?: return@update s
      val moved = list.firstOrNull { it.id == jobId } ?: return@update s
      val counts = s.counts.toMutableMap()
      counts[moved.status] = ((counts[moved.status] ?: 0) - 1).coerceAtLeast(0)
      counts[to] = (counts[to] ?: 0) + 1
      s.copy(
        jobs = list.map { if (it.id == jobId) it.copy(status = to) else it }.toImmutableList(),
        counts = counts.toImmutableMap(),
      )
    }
  }

  private fun toggleStar(jobId: Long) {
    val job = _state.value.jobs?.firstOrNull { it.id == jobId } ?: return
    val changed = job.copy(labels = job.labelsStarred(!job.starred))
    showStar(changed)
    viewModelScope.launch {
      try {
        val updated = jobs.updateLabels(jobId, changed.labels) ?: changed
        changes.publish(JobChange.Updated(updated))
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _messages.trySend(JobsMessage.Failed)
        load()
      }
    }
  }

  /**
   * Shows [job]'s star at once: on its card, in the Starred count, in or out of the Starred tab.
   */
  private fun showStar(job: Job) {
    _state.update { s ->
      val list = s.jobs ?: return@update s
      val old = list.firstOrNull { it.id == job.id } ?: return@update s
      if (old.starred == job.starred) return@update s
      val count = (s.starredCount + if (job.starred) 1 else -1).coerceAtLeast(0)
      val jobs =
        if (s.starred && !job.starred) list.filterNot { it.id == job.id }
        else list.map { if (it.id == job.id) job.copy(description = null) else it }
      s.copy(jobs = jobs.toImmutableList(), starredCount = count)
    }
  }

  /** Like the old app: when moving jobs out leaves less than half a page, load the next one. */
  private fun topUp() {
    val s = _state.value
    if ((s.jobs?.size ?: 0) < pageSize / 2 && s.nextPageToken != null) loadMore()
  }

  private fun onJobChange(change: JobChange) {
    when (change) {
      is JobChange.StatusChanged -> {
        val s = _state.value
        moveInList(change.jobId, change.to)
        if (change.to == s.status && !s.starred) load() else refreshCounts()
      }
      is JobChange.Updated -> {
        val s = _state.value
        // Starred from the detail while the Starred tab is open: it joins the list.
        if (s.starred && change.job.starred && s.jobs?.none { it.id == change.job.id } != false) {
          load()
          return
        }
        showStar(change.job)
        _state.update { s ->
          s.copy(
            jobs =
              s.jobs
                ?.map { if (it.id == change.job.id) change.job.copy(description = null) else it }
                ?.toImmutableList()
          )
        }
      }
      is JobChange.BulkChanged -> load()
    }
  }

  private fun changeAll(to: JobStatus) {
    val from = _state.value.status
    if (from == to || _state.value.starred) return
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
