package io.github.raghavsatyadev.jobfinder.ui.ats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_close
import io.github.raghavsatyadev.jobfinder.resources.improve_blanks
import io.github.raghavsatyadev.jobfinder.resources.improve_copied
import io.github.raghavsatyadev.jobfinder.resources.improve_copy
import io.github.raghavsatyadev.jobfinder.resources.improve_failed
import io.github.raghavsatyadev.jobfinder.resources.improve_gains
import io.github.raghavsatyadev.jobfinder.resources.improve_hint
import io.github.raghavsatyadev.jobfinder.resources.improve_load_failed
import io.github.raghavsatyadev.jobfinder.resources.improve_match
import io.github.raghavsatyadev.jobfinder.resources.improve_new
import io.github.raghavsatyadev.jobfinder.resources.improve_new_numbers
import io.github.raghavsatyadev.jobfinder.resources.improve_new_skills
import io.github.raghavsatyadev.jobfinder.resources.improve_no_review
import io.github.raghavsatyadev.jobfinder.resources.improve_none
import io.github.raghavsatyadev.jobfinder.resources.improve_now
import io.github.raghavsatyadev.jobfinder.resources.improve_queued
import io.github.raghavsatyadev.jobfinder.resources.improve_save
import io.github.raghavsatyadev.jobfinder.resources.improve_saved
import io.github.raghavsatyadev.jobfinder.resources.improve_section_experience
import io.github.raghavsatyadev.jobfinder.resources.improve_section_other
import io.github.raghavsatyadev.jobfinder.resources.improve_section_projects
import io.github.raghavsatyadev.jobfinder.resources.improve_section_skills
import io.github.raghavsatyadev.jobfinder.resources.improve_section_summary
import io.github.raghavsatyadev.jobfinder.resources.improve_title
import io.github.raghavsatyadev.jobfinder.resources.improve_write_again
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementState
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeEdit
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeSection
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The fix plan for one job and the edits the user keeps ticked. */
@Immutable
data class ResumeImproveUiState(
  val status: ImprovementStatus? = null,
  val loadFailed: Boolean = false,
  /** Ticked edits; those that need checking start unticked. */
  val taken: Set<Int> = emptySet(),
  /** The changed text is being fetched. */
  val working: Boolean = false,
  val done: ImproveDone? = null,
)

enum class ImproveDone {
  COPIED,
  SAVED,
  FAILED,
}

sealed interface ResumeImproveEvent {
  data object Write : ResumeImproveEvent

  data class Toggle(val id: Int) : ResumeImproveEvent
}

/**
 * The resume fix plan for [jobId]: asks the computer for one when there is none yet, waits for its
 * `ImprovementReady` event, and fetches the resume's text with the ticked edits applied.
 */
class ResumeImproveViewModel(
  private val jobId: Long,
  private val repository: ResumeImproveRepository,
  scan: ScanRepository,
) : ViewModel() {
  private val _state = MutableStateFlow(ResumeImproveUiState())
  val state: StateFlow<ResumeImproveUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch { load(write = false, firstTime = true) }
    viewModelScope.launch {
      scan.events.collect { event ->
        if (event is ServerEvent.ImprovementReady && event.jobId == jobId) load(write = false)
      }
    }
  }

  fun onEvent(event: ResumeImproveEvent) {
    when (event) {
      ResumeImproveEvent.Write -> viewModelScope.launch { load(write = true) }
      is ResumeImproveEvent.Toggle ->
        _state.update { s ->
          s.copy(taken = if (event.id in s.taken) s.taken - event.id else s.taken + event.id)
        }
    }
  }

  /** The changed resume text, or `null` when it could not be fetched. */
  suspend fun improvedText(): String? {
    _state.update { it.copy(working = true, done = null) }
    return try {
      repository.improvedResume(jobId, _state.value.taken.sorted()).text
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    } finally {
      _state.update { it.copy(working = false) }
    }
  }

  fun finished(done: ImproveDone?) = _state.update { it.copy(done = done) }

  private suspend fun load(write: Boolean, firstTime: Boolean = false) {
    _state.update { it.copy(loadFailed = false) }
    try {
      var status = repository.requestImprovement(jobId, write)
      // Opening the dialog is the ask: write one when there is none yet.
      if (firstTime && status.state == ImprovementState.NONE) {
        status = repository.requestImprovement(jobId, write = true)
      }
      val fresh = status.improvement?.createdAt != _state.value.status?.improvement?.createdAt
      _state.update { s ->
        s.copy(
          status = status,
          taken =
            if (fresh) {
              status.improvement?.edits?.filterNot { it.needsCheck }?.map { it.id }?.toSet()
                ?: emptySet()
            } else s.taken,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      _state.update { it.copy(loadFailed = true) }
    }
  }
}

/** Opens the fix plan of [jobId]; nothing where no computer writes plans. */
@Composable
fun ResumeImproveDialog(jobId: Long, onDismiss: () -> Unit) {
  val viewModel =
    koinViewModel<ResumeImproveViewModel>(key = "improve-$jobId") { parametersOf(jobId) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val koin = getKoin()
  val picker = remember(koin) { koin.getOrNull<FilePicker>() }
  @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  ResumeImproveContent(
    state = state,
    onEvent = viewModel::onEvent,
    onCopy = {
      scope.launch {
        val text = viewModel.improvedText()
        if (text != null) clipboard.setText(AnnotatedString(text))
        viewModel.finished(if (text != null) ImproveDone.COPIED else ImproveDone.FAILED)
      }
    },
    onSave = {
      scope.launch {
        val text = viewModel.improvedText()
        val saved =
          text != null &&
            picker != null &&
            runCatching {
                picker.save("resume-for-job-$jobId.txt", "text/plain", text.encodeToByteArray())
              }
              .getOrDefault(false)
        viewModel.finished(
          when {
            saved -> ImproveDone.SAVED
            text == null -> ImproveDone.FAILED
            else -> null
          }
        )
      }
    },
    onDismiss = onDismiss,
  )
}

@Composable
fun ResumeImproveContent(
  state: ResumeImproveUiState,
  onEvent: (ResumeImproveEvent) -> Unit,
  onCopy: () -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
) {
  val plan = state.status?.improvement?.takeIf { state.status.state == ImprovementState.READY }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(Res.string.improve_title)) },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when {
          state.loadFailed -> Muted(stringResource(Res.string.improve_load_failed))
          state.status == null -> WavyProgress()
          state.status.state == ImprovementState.NO_REVIEW ->
            Muted(stringResource(Res.string.improve_no_review))
          state.status.state == ImprovementState.QUEUED -> {
            Muted(stringResource(Res.string.improve_queued))
            WavyProgress()
          }
          state.status.state == ImprovementState.FAILED ->
            Muted(stringResource(Res.string.improve_failed))
          plan != null && plan.edits.isEmpty() -> Muted(stringResource(Res.string.improve_none))
          plan != null -> {
            Text(
              stringResource(
                Res.string.improve_match,
                plan.baseMatch,
                plan.projectedMatch(state.taken),
              ),
              style = MaterialTheme.typography.titleMedium,
            )
            Muted(stringResource(Res.string.improve_hint))
            plan.edits.forEach { edit ->
              EditCard(edit, edit.id in state.taken) {
                onEvent(ResumeImproveEvent.Toggle(edit.id))
              }
            }
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              OutlinedButton(
                onClick = onSave,
                enabled = !state.working,
                shapes = ButtonDefaults.shapes(),
              ) {
                Text(stringResource(Res.string.improve_save))
              }
              OutlinedButton(
                onClick = { onEvent(ResumeImproveEvent.Write) },
                enabled = !state.working,
                shapes = ButtonDefaults.shapes(),
              ) {
                Text(stringResource(Res.string.improve_write_again))
              }
            }
            if (state.working) WavyProgress()
            when (state.done) {
              ImproveDone.COPIED -> Muted(stringResource(Res.string.improve_copied))
              ImproveDone.SAVED -> Muted(stringResource(Res.string.improve_saved))
              ImproveDone.FAILED -> Muted(stringResource(Res.string.improve_load_failed))
              null -> Unit
            }
          }
        }
        val retry =
          state.loadFailed ||
            state.status?.state == ImprovementState.FAILED ||
            state.status?.state == ImprovementState.NONE ||
            plan?.edits?.isEmpty() == true
        if (retry) {
          FilledTonalButton(
            onClick = { onEvent(ResumeImproveEvent.Write) },
            shapes = ButtonDefaults.shapes(),
          ) {
            Text(stringResource(Res.string.improve_write_again))
          }
        }
      }
    },
    confirmButton = {
      if (plan != null) {
        TextButton(onClick = onCopy, enabled = !state.working && state.taken.isNotEmpty()) {
          Text(stringResource(Res.string.improve_copy))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_close)) }
    },
  )
}

/** One edit: tick to keep it, what it replaces and what it says now, and what to check. */
@Composable
private fun EditCard(edit: ResumeEdit, taken: Boolean, onToggle: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.medium,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
      Checkbox(checked = taken, onCheckedChange = { onToggle() })
      Column(
        Modifier.padding(top = 12.dp, end = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          stringResource(edit.section.label) + " · " + edit.reason,
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          stringResource(Res.string.improve_now, edit.before),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          stringResource(Res.string.improve_new, edit.after),
          style = MaterialTheme.typography.bodyMedium,
        )
        if (edit.gains.isNotEmpty()) {
          Text(
            stringResource(Res.string.improve_gains, edit.gains.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Check(edit.newSkills, Res.string.improve_new_skills)
        Check(edit.newNumbers, Res.string.improve_new_numbers)
        Check(edit.blanks, Res.string.improve_blanks)
      }
    }
  }
}

@Composable
private fun Check(items: List<String>, text: StringResource) {
  if (items.isEmpty()) return
  Text(
    stringResource(text, items.joinToString(", ")),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
  )
}

@Composable
private fun Muted(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private val ResumeSection.label: StringResource
  get() =
    when (this) {
      ResumeSection.SUMMARY -> Res.string.improve_section_summary
      ResumeSection.EXPERIENCE -> Res.string.improve_section_experience
      ResumeSection.SKILLS -> Res.string.improve_section_skills
      ResumeSection.PROJECTS -> Res.string.improve_section_projects
      ResumeSection.OTHER -> Res.string.improve_section_other
    }

/** Whether this app can ask for fix plans. */
@Composable
fun resumeImproveAvailable(): Boolean {
  val koin = getKoin()
  return remember(koin) { koin.getOrNull<ResumeImproveRepository>() != null }
}
