@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_archive_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_archive_all_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_archive_all_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_all_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_all_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_applied
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_archived
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_archived_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_deleted
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_deleted_all
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_failed
import io.github.raghavsatyadev.jobfinder.resources.jobs_msg_restored
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.ErrorState
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalScrollBars
import io.github.raghavsatyadev.jobfinder.ui.components.ScrollBars
import io.github.raghavsatyadev.jobfinder.ui.components.icon
import io.github.raghavsatyadev.jobfinder.ui.components.label
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
        val tab = FeedTabs.indexOf(state.openTab)
        val step = if (event.key == Key.DirectionLeft) -1 else 1
        onEvent(FeedTabs[(tab + step + FeedTabs.size) % FeedTabs.size].select())
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

  // Scrolling down hides the search, the tabs and the floating tab bar; scrolling up shows them.
  val bars = LocalScrollBars.current ?: remember { ScrollBars() }
  DisposableEffect(bars) { onDispose { bars.visible = true } }
  val atTop by remember {
    derivedStateOf {
      listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
    }
  }
  // Kept while typing a search, and at the top of the list, where there is nothing to make room
  // for.
  val showHeader = bars.visible || searchFocused || atTop
  val animate = animationsEnabled()
  Scaffold(
    contentWindowInsets = screenInsets(),
    snackbarHost = { SnackbarHost(snackbar) },
    modifier = Modifier.onPreviewKeyEvent(::onKey).focusRequester(focus).focusable(),
  ) { padding ->
    Column(Modifier.fillMaxSize().paddingAbove(padding).nestedScroll(bars.connection)) {
      AnimatedVisibility(
        visible = showHeader,
        enter = if (animate) expandVertically() + fadeIn() else EnterTransition.None,
        exit = if (animate) shrinkVertically() + fadeOut() else ExitTransition.None,
      ) {
        Column {
          ListHeader(
            state = state,
            onEvent = onEvent,
            onOpenFilters = { showFilters = true },
            onConfirmBulk = { confirmBulk = it },
            onSearchFocus = { searchFocused = it },
          )
          StatusTabs(state, onSelect = { onEvent(it.select()) })
        }
      }
      ScanProgress(state)
      val pull = rememberPullToRefreshState()
      // A mouse wheel past the top starts a pull it never lets go of: the computer has no pull to
      // refresh (it lists new jobs itself).
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
              onStar = { onEvent(JobsListEvent.ToggleStar(it.id)) },
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
