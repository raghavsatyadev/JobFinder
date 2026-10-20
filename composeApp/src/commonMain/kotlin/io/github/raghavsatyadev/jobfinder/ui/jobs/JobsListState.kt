package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.runtime.Immutable
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/**
 * The job feed: one status tab (or the Starred tab), its jobs page by page, the filters and the tab
 * counts.
 */
@Immutable
data class JobsListUiState(
  val status: JobStatus = JobStatus.NEW,
  /** The Starred tab is open: starred jobs of every status; [status] is the tab to go back to. */
  val starred: Boolean = false,
  val counts: ImmutableMap<JobStatus, Int> = persistentMapOf(),
  val starredCount: Int = 0,
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

  data object SelectStarred : JobsListEvent

  /** Stars the job, or takes its star away. */
  data class ToggleStar(val jobId: Long) : JobsListEvent

  data class Search(val text: String) : JobsListEvent

  data class ApplyFilter(val filter: JobFilter) : JobsListEvent

  data object ClearFilters : JobsListEvent

  data class SetSort(val sort: JobSort) : JobsListEvent

  data object LoadMore : JobsListEvent

  /** Reloads the tab and its counts (the old "Refresh"). */
  data object Refresh : JobsListEvent

  data object ArchiveAll : JobsListEvent

  data object DeleteAll : JobsListEvent

  data class ChangeStatus(val jobId: Long, val to: JobStatus) : JobsListEvent
}

/** A one-shot message for the snackbar. */
sealed interface JobsMessage {
  data class StatusChanged(val to: JobStatus) : JobsMessage

  data class ArchivedAll(val count: Int) : JobsMessage

  data class DeletedAll(val count: Int) : JobsMessage

  data object Failed : JobsMessage
}

/** How long typing pauses before the search runs. */
internal const val SEARCH_DEBOUNCE_MILLIS = 300L
