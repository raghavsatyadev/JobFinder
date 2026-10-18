@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_flags_none
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_formats
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_info
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_info_pages
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_none
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_remove
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_remove_text
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_remove_title
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_replace
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_upload
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_uploading
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.ui.ats.text
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsClock
import io.github.raghavsatyadev.jobfinder.ui.jobs.relativeTime
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class ResumeUiState(
  val resume: ResumeInfo? = null,
  val loading: Boolean = true,
  /** A file is being read and checked. */
  val uploading: Boolean = false,
  /** Why the last upload or removal failed, as the computer said it. */
  val error: String? = null,
  /** `false` where there is no file dialog: the upload button hides. */
  val canPick: Boolean = true,
  val confirmRemove: Boolean = false,
)

sealed interface ResumeUiEvent {
  data object Pick : ResumeUiEvent

  data object RequestRemove : ResumeUiEvent

  data object DismissRemove : ResumeUiEvent

  data object ConfirmRemove : ResumeUiEvent
}

/** The resume behind the ATS scores: pick a file, replace or remove it. */
class ResumeViewModel(private val ats: AtsRepository, private val picker: FilePicker?) :
  ViewModel() {
  private val _state = MutableStateFlow(ResumeUiState(canPick = picker != null))
  val state: StateFlow<ResumeUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        val resume = ats.getResume()
        _state.update { it.copy(resume = resume, loading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loading = false) }
      }
    }
  }

  fun onEvent(event: ResumeUiEvent) {
    when (event) {
      ResumeUiEvent.Pick -> pick()
      ResumeUiEvent.RequestRemove -> _state.update { it.copy(confirmRemove = true) }
      ResumeUiEvent.DismissRemove -> _state.update { it.copy(confirmRemove = false) }
      ResumeUiEvent.ConfirmRemove -> remove()
    }
  }

  private fun pick() {
    val picker = picker ?: return
    if (_state.value.uploading) return
    viewModelScope.launch {
      val file =
        try {
          picker.pickResume()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        } ?: return@launch
      _state.update { it.copy(uploading = true, error = null) }
      try {
        val info = ats.uploadResume(file.name, file.bytes)
        _state.update { it.copy(resume = info, uploading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update { it.copy(uploading = false, error = e.userMessage()) }
      }
    }
  }

  private fun remove() {
    _state.update { it.copy(confirmRemove = false) }
    viewModelScope.launch {
      try {
        ats.deleteResume()
        _state.update { it.copy(resume = null, error = null) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update { it.copy(error = e.userMessage()) }
      }
    }
  }

  /** The computer's message (written for the user), or the generic one. */
  private fun Exception.userMessage(): String =
    (this as? ApiFailure)?.message ?: ApiErrorCode.INTERNAL.defaultMessage
}

/** The resume section of Settings, from its ViewModel. */
@Composable
fun ResumeSettings() {
  val viewModel = koinViewModel<ResumeViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val clock = koinInject<JobsClock>()
  ResumeContent(state, clock.now(), viewModel::onEvent)
}

@Composable
fun ResumeContent(state: ResumeUiState, now: Long, onEvent: (ResumeUiEvent) -> Unit) {
  val resume = state.resume
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.settings_resume_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Nothing more until the resume is read, so "Add resume" does not flash before it.
    if (state.loading) return@Column
    if (resume == null) {
      Text(
        stringResource(Res.string.settings_resume_none),
        style = MaterialTheme.typography.bodyLarge,
      )
    } else {
      ResumeSummary(resume, now)
    }
    if (state.uploading) {
      Text(
        stringResource(Res.string.settings_resume_uploading),
        style = MaterialTheme.typography.bodyMedium,
      )
      WavyProgress()
    }
    state.error?.let {
      Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
    // The resume is kept on the computer: offline it shows read-only.
    val offline = LocalComputerOffline.current
    if (offline) OfflineNote()
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.canPick) {
        Button(
          onClick = { onEvent(ResumeUiEvent.Pick) },
          enabled = !state.uploading && !offline,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(Icons.Outlined.FileUpload, null, Modifier.size(ButtonDefaults.IconSize))
          Text(
            stringResource(
              if (resume == null) Res.string.settings_resume_upload
              else Res.string.settings_resume_replace
            ),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
      }
      if (resume != null) {
        OutlinedButton(
          onClick = { onEvent(ResumeUiEvent.RequestRemove) },
          enabled = !state.uploading && !offline,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(ButtonDefaults.IconSize))
          Text(
            stringResource(Res.string.settings_resume_remove),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
      }
    }
    if (state.canPick) {
      Text(
        stringResource(Res.string.settings_resume_formats),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  if (state.confirmRemove) {
    ConfirmDialog(
      icon = Icons.Outlined.DeleteOutline,
      title = stringResource(Res.string.settings_resume_remove_title),
      text = stringResource(Res.string.settings_resume_remove_text),
      confirmLabel = stringResource(Res.string.settings_resume_remove),
      onConfirm = { onEvent(ResumeUiEvent.ConfirmRemove) },
      onDismiss = { onEvent(ResumeUiEvent.DismissRemove) },
    )
  }
}

/** The file, its size and when it was added, then its formatting problems. */
@Composable
private fun ResumeSummary(resume: ResumeInfo, now: Long) {
  val added = relativeTime(resume.uploadedAt, now)
  val pages = resume.pages
  Text(
    if (pages != null)
      stringResource(
        Res.string.settings_resume_info_pages,
        resume.fileName,
        pages,
        resume.words,
        added,
      )
    else stringResource(Res.string.settings_resume_info, resume.fileName, resume.words, added),
    style = MaterialTheme.typography.bodyLarge,
    fontWeight = FontWeight.SemiBold,
  )
  if (resume.flags.isEmpty()) {
    Text(
      stringResource(Res.string.settings_resume_flags_none),
      style = MaterialTheme.typography.bodyMedium,
    )
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      resume.flags.forEach {
        Text("• ${stringResource(it.text)}", style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}
