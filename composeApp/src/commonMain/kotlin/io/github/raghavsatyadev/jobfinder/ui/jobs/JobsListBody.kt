@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_stale
import io.github.raghavsatyadev.jobfinder.resources.jobs_caught_up_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_caught_up_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_clear_filters
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_placeholder_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_placeholder_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_filtered_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_filtered_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_new_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_new_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_starred_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_starred_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_tab_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_tab_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_end_hint_new
import io.github.raghavsatyadev.jobfinder.resources.jobs_end_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_loading_more
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.ui.components.ConfettiBurst
import io.github.raghavsatyadev.jobfinder.ui.components.EmptyState
import io.github.raghavsatyadev.jobfinder.ui.components.ListEndCelebration
import io.github.raghavsatyadev.jobfinder.ui.components.StaggeredEntrance
import io.github.raghavsatyadev.jobfinder.ui.components.icon
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.components.stale
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.jetbrains.compose.resources.stringResource

/** How close to the end of the list the next page starts loading. */
private const val LOAD_MORE_THRESHOLD = 5

/** Items that cascade in on the first open; the rest are off screen anyway. */
private const val ENTRANCE_ITEMS = 8

@Composable
internal fun EmptyJobs(
  state: JobsListUiState,
  onEvent: (JobsListEvent) -> Unit,
  /** Set when the user just emptied the New tab: a new value plays the confetti again. */
  caughtUp: Int? = null,
) {
  when {
    state.isFiltered ->
      EmptyState(
        icon = Icons.Rounded.SearchOff,
        title = stringResource(Res.string.jobs_empty_filtered_title),
        hint = stringResource(Res.string.jobs_empty_filtered_hint),
        action = {
          FilledTonalButton(
            onClick = { onEvent(JobsListEvent.ClearFilters) },
            shapes = ButtonDefaults.shapes(),
          ) {
            Text(stringResource(Res.string.jobs_clear_filters))
          }
        },
      )
    state.starred ->
      EmptyState(
        icon = Icons.Rounded.StarOutline,
        title = stringResource(Res.string.jobs_empty_starred_title),
        hint = stringResource(Res.string.jobs_empty_starred_hint),
      )
    state.status == JobStatus.NEW && caughtUp != null ->
      Box(Modifier.fillMaxSize()) {
        EmptyState(
          icon = Icons.Rounded.Celebration,
          title = stringResource(Res.string.jobs_caught_up_title),
          hint = stringResource(Res.string.jobs_caught_up_hint),
        )
        ConfettiBurst(key = caughtUp)
      }
    state.status == JobStatus.NEW ->
      EmptyState(
        icon = Icons.Rounded.Radar,
        title = stringResource(Res.string.jobs_empty_new_title),
        hint = stringResource(Res.string.jobs_empty_new_hint),
      )
    else ->
      EmptyState(
        icon = Icons.Rounded.Inbox,
        title = stringResource(Res.string.jobs_empty_tab_title),
        hint = stringResource(Res.string.jobs_empty_tab_hint),
      )
  }
}

@Composable
internal fun JobList(
  state: JobsListUiState,
  jobs: List<Job>,
  selectedJobId: Long?,
  now: Long,
  listState: LazyListState,
  onOpen: (Job) -> Unit,
  onArchive: (Job) -> Unit,
  onDelete: (Job) -> Unit,
  onStar: (Job) -> Unit,
  onLoadMore: () -> Unit,
) {
  val siteNames = remember(state.sites) { state.sites.associate { it.id to it.name } }
  var playEntrance by rememberSaveable { mutableStateOf(true) }
  val animate = animationsEnabled()
  // Previews draw one frame: an entrance would leave the cards invisible in it.
  val inspection = LocalInspectionMode.current
  LaunchedEffect(Unit) {
    delay(600)
    playEntrance = false
  }
  // A reload drops a load-more asked meanwhile: once it ends, ask again if still at the end.
  LaunchedEffect(listState, jobs.size, state.loading, state.nextPageToken) {
    snapshotFlow {
      val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
      last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
    }
      .distinctUntilChanged()
      .filter { it }
      .collect { onLoadMore() }
  }
  val staleReason = stringResource(Res.string.common_stale)
  LazyColumn(
    state = listState,
    contentPadding =
      PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp + scrollBottom()),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize().stale(state.isStale, staleReason),
  ) {
    itemsIndexed(jobs, key = { _, job -> job.id }, contentType = { _, _ -> "job" }) { index, job ->
      StaggeredEntrance(
        index = index,
        play = playEntrance && index < ENTRANCE_ITEMS && !inspection,
        modifier = if (animate) Modifier.animateItem() else Modifier,
      ) {
        JobCard(
          job = job,
          siteName = siteNames[job.siteId],
          selected = job.id == selectedJobId,
          now = now,
          onOpen = { onOpen(job) },
          onArchive = { onArchive(job) },
          onDelete = { onDelete(job) },
          onStar = { onStar(job) },
          showStatus = state.starred,
        )
      }
    }
    if (state.nextPageToken == null && !state.loadingMore && jobs.size >= END_MIN_JOBS) {
      item(
        key = "list-end-${if (state.starred) "starred" else state.status.wire}",
        contentType = "end",
      ) {
        ListEndCelebration(
          title = stringResource(Res.string.jobs_end_title),
          hint =
            if (state.status == JobStatus.NEW && !state.starred)
              stringResource(Res.string.jobs_end_hint_new)
            else null,
        )
      }
    }
    if (state.loadingMore) {
      item(key = "loading-more", contentType = "loading") {
        val description = stringResource(Res.string.jobs_loading_more)
        Box(
          Modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = description },
          contentAlignment = Alignment.Center,
        ) {
          LoadingIndicator(Modifier.size(40.dp))
        }
      }
    }
  }
}

/** The detail pane before a job is picked. */
@Composable
internal fun JobDetailPlaceholderContent() {
  EmptyState(
    icon = Icons.Rounded.TouchApp,
    title = stringResource(Res.string.jobs_detail_placeholder_title),
    hint = stringResource(Res.string.jobs_detail_placeholder_hint),
  )
}

/** Shorter lists need no end-of-list celebration. */
private const val END_MIN_JOBS = 3
