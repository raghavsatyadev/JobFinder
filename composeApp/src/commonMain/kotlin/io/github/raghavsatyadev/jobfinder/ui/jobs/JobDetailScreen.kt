@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ats_section_title
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.common_stale
import io.github.raghavsatyadev.jobfinder.resources.ghost_section_title
import io.github.raghavsatyadev.jobfinder.resources.insights_section_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_applied
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_archive
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_restore
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_view
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_delete_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_close
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description_empty
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description_failed
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description_loading
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_excluded_reason
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_found_via
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_labels
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_not_found
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_refresh_description
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_none
import io.github.raghavsatyadev.jobfinder.resources.jobs_notes
import io.github.raghavsatyadev.jobfinder.resources.tips_section_title
import io.github.raghavsatyadev.jobfinder.shared.model.DetailSection
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.ui.ats.AtsPill
import io.github.raghavsatyadev.jobfinder.ui.ats.AtsSection
import io.github.raghavsatyadev.jobfinder.ui.ats.ResumeImproveDialog
import io.github.raghavsatyadev.jobfinder.ui.ats.resumeImproveAvailable
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.EmptyState
import io.github.raghavsatyadev.jobfinder.ui.components.ErrorState
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.jobAtsKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobCompanyKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobContainerKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobLogoKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobMetaKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobTitleKey
import io.github.raghavsatyadev.jobfinder.ui.components.label
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContainer
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContent
import io.github.raghavsatyadev.jobfinder.ui.components.stale
import io.github.raghavsatyadev.jobfinder.ui.components.tone
import io.github.raghavsatyadev.jobfinder.ui.ghost.GhostSection
import io.github.raghavsatyadev.jobfinder.ui.insights.InsightsSection
import io.github.raghavsatyadev.jobfinder.ui.tips.TipsSection
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the detail ViewModel is built from: the route's job id and the list's copy of the job. */
data class JobDetailArgs(val jobId: Long, val preview: Job?)

/** The widest the detail's text runs; wider reads poorly. */
private val ReadingWidth = 840.dp

/**
 * Job [jobId] with its description, labels and notes. [preview] is the list's copy, shown at once.
 * The nav entry calls it through `JobDetailPane`; screenshot tests call it directly.
 */
@Composable
fun JobDetailView(jobId: Long, preview: Job?, onClose: () -> Unit) {
  val viewModel =
    koinViewModel<JobDetailViewModel>(key = "job-detail-$jobId") {
      parametersOf(JobDetailArgs(jobId, preview))
    }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val close by rememberUpdatedState(onClose)
  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        JobDetailEffect.Close -> close()
        is JobDetailEffect.Message -> effect.message.text()?.let { snackbar.showSnackbar(it) }
      }
    }
  }
  val clock = koinInject<JobsClock>()
  JobDetailContent(state, clock.now(), snackbar, viewModel::onEvent, onClose)
}

/** One job drawn from [state]; [JobDetailView] feeds it from its ViewModel. */
@Composable
fun JobDetailContent(
  state: JobDetailUiState,
  now: Long,
  snackbar: SnackbarHostState = remember { SnackbarHostState() },
  onEvent: (JobDetailEvent) -> Unit = {},
  onClose: () -> Unit = {},
) {
  var confirmDelete by remember { mutableStateOf(false) }
  Scaffold(
    snackbarHost = { SnackbarHost(snackbar) },
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentWindowInsets = screenInsets(),
    modifier = Modifier.sharedContainer(jobContainerKey(state.jobId), fullScreen = true),
  ) { padding ->
    val job = state.job
    Box(Modifier.fillMaxSize().paddingAbove(padding)) {
      when {
        state.notFound ->
          EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(Res.string.jobs_detail_not_found),
            hint = "",
          )
        job == null && state.failed ->
          ErrorState(
            stringResource(Res.string.common_error_generic),
            onRetry = { onEvent(JobDetailEvent.Retry) },
          )
        job == null -> LoadingState()
        else ->
          DetailBody(
            state = state,
            job = job,
            now = now,
            onEvent = onEvent,
            onDelete = { confirmDelete = true },
          )
      }
      IconButton(
        onClick = onClose,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.ArrowBack,
          stringResource(Res.string.jobs_detail_close),
        )
      }
    }
  }
  if (confirmDelete) {
    ConfirmDialog(
      icon = Icons.Outlined.DeleteOutline,
      title = stringResource(Res.string.jobs_delete_title),
      text = stringResource(Res.string.jobs_delete_text),
      confirmLabel = stringResource(Res.string.jobs_action_delete),
      onConfirm = {
        confirmDelete = false
        onEvent(JobDetailEvent.ChangeStatus(JobStatus.DELETED))
      },
      onDismiss = { confirmDelete = false },
    )
  }
}

@Composable
private fun DetailBody(
  state: JobDetailUiState,
  job: Job,
  now: Long,
  onEvent: (JobDetailEvent) -> Unit,
  onDelete: () -> Unit,
) {
  val staleReason = stringResource(Res.string.common_stale)
  Column(
    Modifier.fillMaxSize()
      .verticalScroll(rememberScrollState())
      .stale(state.failed, staleReason)
      .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 32.dp + scrollBottom()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Column(
      Modifier.widthIn(max = ReadingWidth).fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Header(state, job, now)
      job.excludeReason
        ?.takeIf { job.status == JobStatus.EXCLUDED_BY_ADVANCED_MATCHING }
        ?.let { ExcludedReason(it) }
      Actions(job, busy = state.busy, onEvent = onEvent, onDelete = onDelete)
      if (state.canReport && job.status == JobStatus.NEW) {
        ReportButton(onClick = { onEvent(JobDetailEvent.OpenReport) })
      }
      state.report?.let { ReportDialog(it, onEvent) }
      for (section in state.layout.shown) {
        key(section) { MovableSection(section, state, job, now, onEvent) }
      }
    }
  }
}

/** One of the sections the user can move or hide; a section with nothing to show is left out. */
@Composable
private fun MovableSection(
  section: DetailSection,
  state: JobDetailUiState,
  job: Job,
  now: Long,
  onEvent: (JobDetailEvent) -> Unit,
) {
  when (section) {
    DetailSection.LABELS ->
      Section(stringResource(Res.string.jobs_detail_labels)) {
        LabelPicker((job.labels - JobLabel.STARRED).firstOrNull()) {
          onEvent(JobDetailEvent.SetLabel(it))
        }
      }
    DetailSection.ATS ->
      Section(stringResource(Res.string.ats_section_title)) {
        var improving by rememberSaveable { mutableStateOf(false) }
        AtsSection(
          state.ats,
          state.atsFailed,
          onRetry = { onEvent(JobDetailEvent.RetryAts) },
          onImprove = if (resumeImproveAvailable()) ({ improving = true }) else null,
        )
        if (improving) ResumeImproveDialog(job.id) { improving = false }
      }
    DetailSection.COMPANY ->
      if (state.insights != null || state.insightsFailed) {
        Section(stringResource(Res.string.insights_section_title)) {
          InsightsSection(
            state.insights,
            state.insightsFailed,
            onRetry = { onEvent(JobDetailEvent.RetryInsights) },
          )
        }
      }
    DetailSection.GHOST ->
      if (state.ghost != null || state.ghostFailed) {
        Section(stringResource(Res.string.ghost_section_title)) {
          GhostSection(
            state.ghost,
            state.ghostFailed,
            now,
            onRetry = { onEvent(JobDetailEvent.RetryGhost) },
          )
        }
      }
    DetailSection.TIPS ->
      if (state.tips != null || state.tipsFailed) {
        Section(stringResource(Res.string.tips_section_title)) {
          TipsSection(
            state.tips,
            state.tipsFailed,
            onWrite = { onEvent(JobDetailEvent.WriteTips) },
          )
        }
      }
    DetailSection.DESCRIPTION ->
      Section(
        stringResource(Res.string.jobs_detail_description),
        action = {
          IconButton(
            onClick = { onEvent(JobDetailEvent.RefreshDescription) },
            enabled = !state.descriptionLoading,
            shapes = IconButtonDefaults.shapes(),
          ) {
            Icon(Icons.Outlined.Refresh, stringResource(Res.string.jobs_detail_refresh_description))
          }
        },
      ) {
        Description(state, job)
      }
    DetailSection.NOTES ->
      Section(stringResource(Res.string.jobs_notes)) {
        JobNotesSection(
          notes = state.notes,
          now = now,
          onAdd = { onEvent(JobDetailEvent.AddNote(it)) },
          onEdit = { id, text -> onEvent(JobDetailEvent.EditNote(id, text)) },
          onDelete = { onEvent(JobDetailEvent.DeleteNote(it)) },
        )
      }
  }
}

/** The card's pieces again (logo, title, company, meta) so each flies to its twin. */
@Composable
private fun Header(state: JobDetailUiState, job: Job, now: Long) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      CompanyLogo(
        job,
        size = DetailLogo,
        modifier = Modifier.sharedContent(jobLogoKey(job.id), CardLogo / DetailLogo),
      )
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(
          job.title,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
          modifier =
            Modifier.sharedContent(jobTitleKey(job.id), titleRatio(detail = false)).semantics {
              heading()
            },
        )
        Text(
          job.companyName,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.sharedContent(jobCompanyKey(job.id), companyRatio(detail = false)),
        )
      }
    }
    JobMeta(job, Modifier.sharedContent(jobMetaKey(job.id)))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      itemVerticalAlignment = Alignment.CenterVertically,
    ) {
      StatusPill(stringResource(job.status.label), job.status.tone)
      // The card's ATS pill flies here.
      job.ats?.let { AtsPill(it, Modifier.sharedContent(jobAtsKey(job.id))) }
      Row(verticalAlignment = Alignment.CenterVertically) {
        SiteLogo(state.siteLogoKey)
        Spacer(Modifier.width(6.dp))
        val source =
          listOfNotNull(
              state.siteName,
              state.linkTitle?.let { stringResource(Res.string.jobs_detail_found_via, it) },
              relativeTime(job.updatedAt, now),
            )
            .joinToString(" · ")
        Text(
          source,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ExcludedReason(reason: String) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp)) {
      Text(
        stringResource(Res.string.jobs_detail_excluded_reason),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(reason, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

/**
 * View is the main action; status moves sit beside it and delete sits last, in the error colour.
 */
@Composable
private fun Actions(
  job: Job,
  busy: Boolean,
  onEvent: (JobDetailEvent) -> Unit,
  onDelete: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Button(
      onClick = { runCatching { uriHandler.openUri(job.externalUrl) } },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(stringResource(Res.string.jobs_action_view))
    }
    StarButton(job.starred, onClick = { onEvent(JobDetailEvent.ToggleStar) })
    if (job.status != JobStatus.APPLIED) {
      FilledTonalButton(
        onClick = { onEvent(JobDetailEvent.ChangeStatus(JobStatus.APPLIED)) },
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
      ) {
        Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.jobs_action_applied))
      }
    }
    if (job.status != JobStatus.ARCHIVED) {
      FilledTonalButton(
        onClick = { onEvent(JobDetailEvent.ChangeStatus(JobStatus.ARCHIVED)) },
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
      ) {
        Icon(Icons.Outlined.Archive, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.jobs_action_archive))
      }
    }
    if (job.status != JobStatus.NEW) {
      FilledTonalButton(
        onClick = { onEvent(JobDetailEvent.ChangeStatus(JobStatus.NEW)) },
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
      ) {
        Icon(Icons.Outlined.Unarchive, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.jobs_action_restore))
      }
    }
    OutlinedButton(
      onClick = onDelete,
      enabled = !busy,
      shapes = ButtonDefaults.shapes(),
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(stringResource(Res.string.jobs_action_delete))
    }
  }
}

@Composable
private fun Section(
  title: String,
  action: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      action?.invoke()
    }
    content()
  }
}

/** One label per job, as in the old app; "None" clears it. */
@Composable
private fun LabelPicker(current: JobLabel?, onPick: (JobLabel?) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    LabelChip(stringResource(Res.string.jobs_label_none), current == null) { onPick(null) }
    JobLabel.CHOICES.forEach { label ->
      LabelChip(stringResource(label.label), current == label) { onPick(label) }
    }
  }
}

@Composable
private fun LabelChip(text: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text) },
    leadingIcon =
      if (selected) {
        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
      } else null,
  )
}

@Composable
private fun Description(state: JobDetailUiState, job: Job) {
  val description = job.description
  when {
    !description.isNullOrBlank() ->
      DescriptionText(
        description,
        Modifier.stale(
          state.descriptionLoading,
          stringResource(Res.string.jobs_detail_description_loading),
        ),
      )
    state.descriptionLoading -> {
      val text = stringResource(Res.string.jobs_detail_description_loading)
      Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
      }
    }
    state.descriptionFailed ->
      Text(
        stringResource(Res.string.jobs_detail_description_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    else ->
      Text(
        stringResource(Res.string.jobs_detail_description_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
  }
}

/** A job description's Markdown, tidied by [DescriptionFormat]. */
@Composable
fun DescriptionText(markdown: String, modifier: Modifier = Modifier) {
  Markdown(
    content = remember(markdown) { DescriptionFormat.tidy(markdown) },
    typography = descriptionTypography(),
    padding = descriptionPadding(),
    modifier = modifier.fillMaxWidth(),
  )
}

/** Room between paragraphs and around lists, tight between a list's items. */
@Composable
private fun descriptionPadding() =
  markdownPadding(
    block = 6.dp,
    list = 4.dp,
    listItemTop = 2.dp,
    listItemBottom = 2.dp,
    listIndent = 12.dp,
  )

/** Headings sized for a pane, not a page: a job description's `##` must not out-shout the title. */
@Composable
private fun descriptionTypography() =
  MaterialTheme.typography.let { type ->
    // Bold and a step above the text, so sections stand out without out-shouting the job title.
    val section = type.titleLarge.copy(fontWeight = FontWeight.Bold)
    val subsection = type.titleMedium.copy(fontWeight = FontWeight.Bold)
    markdownTypography(
      h1 = section,
      h2 = section,
      h3 = section,
      h4 = subsection,
      h5 = subsection,
      h6 = subsection,
      text = type.bodyLarge,
      paragraph = type.bodyLarge,
    )
  }
