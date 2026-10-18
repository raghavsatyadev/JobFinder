@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.common_stale
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_archive_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_refresh
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_scan
import io.github.raghavsatyadev.jobfinder.resources.jobs_archive_all_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_archive_all_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_caught_up_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_caught_up_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_clear_filters
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_all_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_all_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_placeholder_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_placeholder_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_filtered_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_filtered_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_new_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_new_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_tab_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_empty_tab_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_end_hint_new
import io.github.raghavsatyadev.jobfinder.resources.jobs_end_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_filters
import io.github.raghavsatyadev.jobfinder.resources.jobs_filters_active
import io.github.raghavsatyadev.jobfinder.resources.jobs_loading_more
import io.github.raghavsatyadev.jobfinder.resources.jobs_more_actions
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_applied
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_archived
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_archived_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_deleted
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_deleted_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_failed
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_restored
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_scan_running
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_scan_started
import io.github.raghavsatyadev.jobfinder.resources.jobs_scanning
import io.github.raghavsatyadev.jobfinder.resources.jobs_scanning_progress
import io.github.raghavsatyadev.jobfinder.resources.jobs_search_clear
import io.github.raghavsatyadev.jobfinder.resources.jobs_search_hint
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.ui.components.ConfettiBurst
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.EmptyState
import io.github.raghavsatyadev.jobfinder.ui.components.ErrorState
import io.github.raghavsatyadev.jobfinder.ui.components.JobTabs
import io.github.raghavsatyadev.jobfinder.ui.components.ListEndCelebration
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.LocalWindowFab
import io.github.raghavsatyadev.jobfinder.ui.components.Show
import io.github.raghavsatyadev.jobfinder.ui.components.StaggeredEntrance
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.components.icon
import io.github.raghavsatyadev.jobfinder.ui.components.label
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.components.stale
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** How close to the end of the list the next page starts loading. */
private const val LOAD_MORE_THRESHOLD = 5

/** Items that cascade in on the first open; the rest are off screen anyway. */
private const val ENTRANCE_ITEMS = 8

@Composable
internal fun JobsListScreen(
  selectedJobId: Long?,
  onOpenJob: (Job) -> Unit,
  viewModel: JobsListViewModel = koinViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.messages.collect { message -> message.text()?.let { snackbar.showSnackbar(it) } }
  }
  val clock = koinInject<JobsClock>()
  JobsListContent(
    state = state,
    selectedJobId = selectedJobId,
    now = clock.now(),
    snackbar = snackbar,
    onEvent = viewModel::onEvent,
    onOpenJob = onOpenJob,
  )
}

/** The snackbar text of a message, or `null` when it needs none. */
internal suspend fun JobsMessage.text(): String? =
  when (this) {
    is JobsMessage.StatusChanged ->
      when (to) {
        JobStatus.ARCHIVED -> getString(Res.string.jobs_msg_archived)
        JobStatus.APPLIED -> getString(Res.string.jobs_msg_applied)
        JobStatus.DELETED -> getString(Res.string.jobs_msg_deleted)
        JobStatus.NEW -> getString(Res.string.jobs_msg_restored)
        else -> null
      }
    is JobsMessage.ArchivedAll -> getString(Res.string.jobs_msg_archived_all, count)
    is JobsMessage.DeletedAll -> getString(Res.string.jobs_msg_deleted_all, count)
    JobsMessage.ScanStarted -> getString(Res.string.jobs_msg_scan_started)
    JobsMessage.ScanAlreadyRunning -> getString(Res.string.jobs_msg_scan_running)
    JobsMessage.Failed -> getString(Res.string.jobs_msg_failed)
  }

/** The job feed drawn from [state]; [JobsListScreen] feeds it from its ViewModel. */
@Composable
fun JobsListContent(
  state: JobsListUiState,
  selectedJobId: Long?,
  now: Long,
  snackbar: SnackbarHostState = remember { SnackbarHostState() },
  onEvent: (JobsListEvent) -> Unit = {},
  onOpenJob: (Job) -> Unit = {},
) {
  var showFilters by rememberSaveable { mutableStateOf(false) }
  var confirmBulk by remember { mutableStateOf<JobStatus?>(null) }
  var pendingDelete by remember { mutableStateOf<Job?>(null) }
  var searchFocused by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val focus = remember { FocusRequester() }
  val scope = rememberCoroutineScope()
  val jobs = state.jobs.orEmpty()

  fun open(job: Job) {
    onOpenJob(job)
    focus.requestFocus()
  }

  // Keyboard, as in the old app: arrows move the selection, Ctrl/Cmd+A archives the selected job
  // and Ctrl/Cmd+D asks to delete it, Left/Right switch tabs. Ignored while typing a search.
  fun onKey(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown || searchFocused) return false
    val index = jobs.indexOfFirst { it.id == selectedJobId }
    val selected = jobs.getOrNull(index)
    val command = event.isCtrlPressed || event.isMetaPressed
    return when {
      event.key == Key.DirectionDown && !command -> {
        val next = jobs.getOrNull(if (index < 0) 0 else index + 1) ?: return true
        open(next)
        scope.launch { listState.ensureVisible(jobs.indexOf(next)) }
        true
      }
      event.key == Key.DirectionUp && !command -> {
        val previous = jobs.getOrNull(index - 1) ?: return true
        open(previous)
        scope.launch { listState.ensureVisible(index - 1) }
        true
      }
      event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> {
        val tab = JobTabs.indexOf(state.status)
        val step = if (event.key == Key.DirectionLeft) -1 else 1
        onEvent(JobsListEvent.SelectTab(JobTabs[(tab + step + JobTabs.size) % JobTabs.size]))
        true
      }
      command && event.key == Key.A -> {
        if (selected != null && selected.status != JobStatus.ARCHIVED) {
          (jobs.getOrNull(index + 1) ?: jobs.getOrNull(index - 1))?.let(::open)
          onEvent(JobsListEvent.ChangeStatus(selected.id, JobStatus.ARCHIVED))
        }
        selected != null
      }
      command && event.key == Key.D -> {
        if (selected != null) pendingDelete = selected
        selected != null
      }
      else -> false
    }
  }

  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  val offline = LocalComputerOffline.current
  val scanFab: @Composable () -> Unit = {
    // Scans run on the computer.
    if (!state.scan.scanning && !offline) {
      SmallExtendedFloatingActionButton(
        text = { Text(stringResource(Res.string.jobs_action_scan)) },
        icon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
        onClick = { onEvent(JobsListEvent.ScanAll) },
      )
    }
  }
  // Side by side, Scan now sits in the window's corner, not the narrow list's.
  val windowFab = LocalWindowFab.current
  windowFab?.Show(scanFab)
  Scaffold(
    contentWindowInsets = screenInsets(),
    snackbarHost = { SnackbarHost(snackbar) },
    floatingActionButton = { if (windowFab == null) scanFab() },
    modifier = Modifier.onPreviewKeyEvent(::onKey).focusRequester(focus).focusable(),
  ) { padding ->
    Column(Modifier.fillMaxSize().paddingAbove(padding)) {
      ListHeader(
        state = state,
        onEvent = onEvent,
        onOpenFilters = { showFilters = true },
        onConfirmBulk = { confirmBulk = it },
        onSearchFocus = { searchFocused = it },
      )
      StatusTabs(state, onSelect = { onEvent(JobsListEvent.SelectTab(it)) })
      ScanProgress(state)
      val pull = rememberPullToRefreshState()
      // A mouse wheel past the top starts a pull it never lets go of: the computer has no pull to
      // refresh (it lists new jobs itself, and has Scan now).
      val koin = getKoin()
      val canPull = remember { koin.getOrNull<DesktopFeatures>() == null }
      PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { onEvent(JobsListEvent.Refresh) },
        state = pull,
        enabled = canPull,
        indicator = {
          if (canPull) {
            PullToRefreshDefaults.LoadingIndicator(
              state = pull,
              isRefreshing = state.refreshing,
              modifier = Modifier.align(Alignment.TopCenter),
            )
          }
        },
        modifier = Modifier.fillMaxSize(),
      ) {
        val list = state.jobs
        var newHadJobs by remember { mutableStateOf(false) }
        var caughtUp by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(state.status, state.isFiltered, list?.size) {
          when {
            state.status != JobStatus.NEW || state.isFiltered -> {
              newHadJobs = false
              caughtUp = null
            }
            !list.isNullOrEmpty() -> {
              newHadJobs = true
              caughtUp = null
            }
            list != null && newHadJobs -> {
              newHadJobs = false
              caughtUp = (caughtUp ?: 0) + 1
            }
          }
        }
        when {
          list == null && state.failed ->
            ErrorState(
              stringResource(Res.string.common_error_generic),
              onRetry = { onEvent(JobsListEvent.Refresh) },
            )
          list == null -> LoadingState()
          list.isEmpty() && !state.loading -> EmptyJobs(state, onEvent, caughtUp)
          list.isEmpty() -> LoadingState()
          else ->
            JobList(
              state = state,
              jobs = list,
              selectedJobId = selectedJobId,
              now = now,
              listState = listState,
              onOpen = ::open,
              onArchive = { onEvent(JobsListEvent.ChangeStatus(it.id, JobStatus.ARCHIVED)) },
              onDelete = { pendingDelete = it },
              onLoadMore = { onEvent(JobsListEvent.LoadMore) },
            )
        }
      }
    }
  }

  if (showFilters) {
    JobFilterSheet(
      filter = state.filter,
      sites = state.sites,
      links = state.links,
      onApply = { onEvent(JobsListEvent.ApplyFilter(it)) },
      onDismiss = { showFilters = false },
    )
  }

  confirmBulk?.let { to ->
    val tab = stringResource(state.status.label)
    val delete = to == JobStatus.DELETED
    ConfirmDialog(
      icon = if (delete) Icons.Outlined.DeleteOutline else Icons.Outlined.Archive,
      title =
        stringResource(
          if (delete) Res.string.jobs_delete_all_title else Res.string.jobs_archive_all_title,
          tab,
        ),
      text =
        stringResource(
          if (delete) Res.string.jobs_delete_all_text else Res.string.jobs_archive_all_text
        ),
      confirmLabel =
        stringResource(
          if (delete) Res.string.jobs_action_delete_all else Res.string.jobs_action_archive_all
        ),
      onConfirm = {
        confirmBulk = null
        onEvent(if (delete) JobsListEvent.DeleteAll else JobsListEvent.ArchiveAll)
      },
      onDismiss = { confirmBulk = null },
    )
  }

  pendingDelete?.let { job ->
    ConfirmDialog(
      icon = Icons.Outlined.DeleteOutline,
      title = stringResource(Res.string.jobs_delete_title),
      text = stringResource(Res.string.jobs_delete_text),
      confirmLabel = stringResource(Res.string.jobs_action_delete),
      onConfirm = {
        pendingDelete = null
        if (job.id == selectedJobId) {
          val index = jobs.indexOfFirst { it.id == job.id }
          (jobs.getOrNull(index + 1) ?: jobs.getOrNull(index - 1))?.let(::open)
        }
        onEvent(JobsListEvent.ChangeStatus(job.id, JobStatus.DELETED))
      },
      onDismiss = { pendingDelete = null },
    )
  }
}

private suspend fun LazyListState.ensureVisible(index: Int) {
  if (index < 0) return
  val visible = layoutInfo.visibleItemsInfo
  val first = visible.firstOrNull()?.index ?: 0
  val last = visible.lastOrNull()?.index ?: 0
  if (index <= first || index >= last) animateScrollToItem(index)
}

@Composable
private fun ListHeader(
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
  // Scans and changes to every job at once need the computer.
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
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.jobs_action_scan)) },
        leadingIcon = { Icon(Icons.Rounded.Radar, null) },
        enabled = !state.scan.scanning && online,
        onClick = {
          open = false
          onEvent(JobsListEvent.ScanAll)
        },
      )
      HorizontalDivider()
      if (state.status != JobStatus.ARCHIVED) {
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

/**
 * New, Applied, Archived and Filtered out as primary tabs, each with its count in a pill. Scrolls
 * sideways when the counts make them wider than the screen.
 */
@Composable
private fun StatusTabs(state: JobsListUiState, onSelect: (JobStatus) -> Unit) {
  val selected = JobTabs.indexOf(state.status).coerceAtLeast(0)
  PrimaryScrollableTabRow(
    selectedTabIndex = selected,
    edgePadding = 16.dp,
    containerColor = Color.Transparent,
    indicator = {
      TabRowDefaults.PrimaryIndicator(
        Modifier.tabIndicatorOffset(selected, matchContentSize = true),
        width = Dp.Unspecified,
      )
    },
  ) {
    JobTabs.forEachIndexed { index, status ->
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
            Icon(status.icon, contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(status.label), maxLines = 1)
            CountPill(state.counts[status] ?: 0, checked)
          }
        },
      )
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

@Composable
private fun ScanProgress(state: JobsListUiState) {
  if (!state.scan.scanning) return
  val scan = state.scan
  val text =
    if (scan.linksTotal > 0)
      stringResource(Res.string.jobs_scanning_progress, scan.linksDone, scan.linksTotal)
    else stringResource(Res.string.jobs_scanning)
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
    Text(
      text,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.size(4.dp))
    if (scan.linksTotal > 0) {
      val progress = scan.linksDone.toFloat() / scan.linksTotal
      WavyProgress(progress)
    } else {
      WavyProgress()
    }
  }
}

@Composable
private fun EmptyJobs(
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
private fun JobList(
  state: JobsListUiState,
  jobs: List<Job>,
  selectedJobId: Long?,
  now: Long,
  listState: LazyListState,
  onOpen: (Job) -> Unit,
  onArchive: (Job) -> Unit,
  onDelete: (Job) -> Unit,
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
  LaunchedEffect(listState, jobs.size) {
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
        )
      }
    }
    if (state.nextPageToken == null && !state.loadingMore && jobs.size >= END_MIN_JOBS) {
      item(key = "list-end-${state.status.wire}", contentType = "end") {
        ListEndCelebration(
          title = stringResource(Res.string.jobs_end_title),
          hint =
            if (state.status == JobStatus.NEW) stringResource(Res.string.jobs_end_hint_new)
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
