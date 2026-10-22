@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_archive_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_refresh
import io.github.raghavsatyadev.jobfinder.resources.jobs_filters
import io.github.raghavsatyadev.jobfinder.resources.jobs_filters_active
import io.github.raghavsatyadev.jobfinder.resources.jobs_more_actions
import io.github.raghavsatyadev.jobfinder.resources.jobs_reading_progress
import io.github.raghavsatyadev.jobfinder.resources.jobs_scanning
import io.github.raghavsatyadev.jobfinder.resources.jobs_scanning_progress
import io.github.raghavsatyadev.jobfinder.resources.jobs_search_clear
import io.github.raghavsatyadev.jobfinder.resources.jobs_search_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_tab_starred
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.ui.components.JobTabs
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.components.icon
import io.github.raghavsatyadev.jobfinder.ui.components.label
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ListHeader(
  state: JobsListUiState,
  onEvent: (JobsListEvent) -> Unit,
  onOpenFilters: () -> Unit,
  onConfirmBulk: (JobStatus) -> Unit,
  onSearchFocus: (Boolean) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = state.searchText,
      onValueChange = { onEvent(JobsListEvent.Search(it)) },
      placeholder = { Text(stringResource(Res.string.jobs_search_hint), maxLines = 1) },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon =
        if (state.searchText.isNotEmpty()) {
          {
            IconButton(onClick = { onEvent(JobsListEvent.Search("")) }) {
              Icon(Icons.Rounded.Clear, stringResource(Res.string.jobs_search_clear))
            }
          }
        } else null,
      singleLine = true,
      shape = CircleShape,
      modifier = Modifier.weight(1f).onFocusChanged { onSearchFocus(it.isFocused) },
    )
    Spacer(Modifier.width(4.dp))
    val filterLabel =
      if (state.activeFilterCount > 0)
        stringResource(Res.string.jobs_filters_active, state.activeFilterCount)
      else stringResource(Res.string.jobs_filters)
    FilledTonalIconButton(
      onClick = onOpenFilters,
      shapes = IconButtonDefaults.shapes(),
      modifier = Modifier.semantics { contentDescription = filterLabel },
    ) {
      BadgedBox(
        badge = {
          if (state.activeFilterCount > 0) Badge { Text("${state.activeFilterCount}") }
        }
      ) {
        Icon(Icons.Rounded.FilterList, contentDescription = null)
      }
    }
    SortMenu(state.filter.sort) { onEvent(JobsListEvent.SetSort(it)) }
    OverflowMenu(state, onEvent, onConfirmBulk)
  }
}

@Composable
private fun OverflowMenu(
  state: JobsListUiState,
  onEvent: (JobsListEvent) -> Unit,
  onConfirmBulk: (JobStatus) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  // Changes to every job at once need the computer.
  val online = !LocalComputerOffline.current
  Box {
    IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
      Icon(Icons.Rounded.MoreVert, stringResource(Res.string.jobs_more_actions))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.jobs_action_refresh)) },
        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
        onClick = {
          open = false
          onEvent(JobsListEvent.Refresh)
        },
      )
      // The Starred tab mixes every status: no bulk moves there.
      if (!state.starred) HorizontalDivider()
      if (state.status != JobStatus.ARCHIVED && !state.starred) {
        DropdownMenuItem(
          text = { Text(stringResource(Res.string.jobs_action_archive_all)) },
          leadingIcon = { Icon(Icons.Outlined.Archive, null) },
          enabled = online && (state.counts[state.status] ?: 0) > 0,
          onClick = {
            open = false
            onConfirmBulk(JobStatus.ARCHIVED)
          },
        )
      }
      if (!state.starred)
        DropdownMenuItem(
          text = {
            Text(
              stringResource(Res.string.jobs_action_delete_all),
              color = MaterialTheme.colorScheme.error,
            )
          },
          leadingIcon = {
            Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
          },
          enabled = online && (state.counts[state.status] ?: 0) > 0,
          onClick = {
            open = false
            onConfirmBulk(JobStatus.DELETED)
          },
        )
    }
  }
}

/** A tab of the feed: one status, or the Starred tab (`null`). */
internal val FeedTabs: List<JobStatus?> = listOf(JobTabs.first(), null) + JobTabs.drop(1)

internal val JobsListUiState.openTab: JobStatus?
  get() = if (starred) null else status

internal fun JobStatus?.select(): JobsListEvent =
  if (this == null) JobsListEvent.SelectStarred else JobsListEvent.SelectTab(this)

/**
 * New, Starred, Applied, Archived and Filtered out as primary tabs, each with its count in a pill.
 * Scrolls sideways when the counts make them wider than the screen, by touch or by the mouse wheel
 * over the tabs; a tab opens only when clicked.
 */
@Composable
internal fun StatusTabs(state: JobsListUiState, onSelect: (JobStatus?) -> Unit) {
  val selected = FeedTabs.indexOf(state.openTab).coerceAtLeast(0)
  val scroll = rememberScrollState()
  PrimaryScrollableTabRow(
    selectedTabIndex = selected,
    scrollState = scroll,
    modifier = Modifier.wheelScrollsSideways(scroll),
    edgePadding = 16.dp,
    containerColor = Color.Transparent,
    indicator = {
      TabRowDefaults.PrimaryIndicator(
        Modifier.tabIndicatorOffset(selected, matchContentSize = true),
        width = Dp.Unspecified,
      )
    },
  ) {
    FeedTabs.forEachIndexed { index, status ->
      val checked = index == selected
      Tab(
        selected = checked,
        onClick = { onSelect(status) },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        text = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              status?.icon ?: Icons.Rounded.Star,
              contentDescription = null,
              Modifier.size(18.dp),
            )
            Text(stringResource(status?.label ?: Res.string.jobs_tab_starred), maxLines = 1)
            CountPill(status?.let { state.counts[it] ?: 0 } ?: state.starredCount, checked)
          }
        },
      )
    }
  }
}

/** One mouse wheel notch moves a sideways row this far. */
private val WHEEL_STEP = 64.dp

/**
 * Turns the mouse wheel over a sideways row into sideways scrolling of [scroll]: on a computer the
 * wheel only scrolls up and down. Touch and a touchpad's own sideways swipe are left as they are.
 */
private fun Modifier.wheelScrollsSideways(scroll: ScrollState): Modifier =
  pointerInput(scroll) {
    coroutineScope {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent()
          if (event.type != PointerEventType.Scroll) continue
          val change = event.changes.firstOrNull() ?: continue
          if (change.isConsumed) continue
          val delta = change.scrollDelta
          val by = (if (delta.x != 0f) delta.x else delta.y) * WHEEL_STEP.toPx()
          if (by == 0f) continue
          launch { scroll.scrollBy(by) }
          event.changes.forEach { it.consume() }
        }
      }
    }
  }

/** A tab's job count; primary on the selected tab. */
@Composable
private fun CountPill(count: Int, selected: Boolean) {
  val colors = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  val container by
    animateColorAsState(
      if (selected) colors.primary else colors.surfaceContainerHighest,
      motion.fastEffectsSpec(),
    )
  val content by
    animateColorAsState(
      if (selected) colors.onPrimary else colors.onSurfaceVariant,
      motion.fastEffectsSpec(),
    )
  Text(
    "$count",
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    color = content,
    maxLines = 1,
    modifier =
      Modifier.background(container, MaterialTheme.shapes.small)
        .padding(horizontal = 8.dp, vertical = 2.dp),
  )
}

/**
 * A bar under the tabs while the computer scans: one step per saved search, then one per new job
 * whose details it reads.
 */
@Composable
internal fun ScanProgress(state: JobsListUiState) {
  if (!state.scan.scanning) return
  val scan = state.scan
  val readingJobs = scan.jobsTotal > 0 && scan.linksDone >= scan.linksTotal
  val (done, total) =
    if (readingJobs) scan.jobsDone to scan.jobsTotal else scan.linksDone to scan.linksTotal
  val text =
    when {
      readingJobs -> stringResource(Res.string.jobs_reading_progress, done, total)
      total > 0 -> stringResource(Res.string.jobs_scanning_progress, done, total)
      else -> stringResource(Res.string.jobs_scanning)
    }
  val progress by
    animateFloatAsState(if (total > 0) done.toFloat() / total else 0f, label = "scan progress")
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(4.dp))
    WavyProgress(if (total > 0) progress else null)
  }
}
