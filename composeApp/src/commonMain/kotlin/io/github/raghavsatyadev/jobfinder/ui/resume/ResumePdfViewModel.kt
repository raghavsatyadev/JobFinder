package io.github.raghavsatyadev.jobfinder.ui.resume

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.model.MakeResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.PdfPageSize
import io.github.raghavsatyadev.jobfinder.shared.model.PdfSection
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a "Save as PDF" came to. */
sealed interface PdfOutcome {
  data class Saved(val pdf: ResumePdf) : PdfOutcome

  data class Failed(val message: String) : PdfOutcome
}

/**
 * Makes the PDF on the computer and asks where to save it; `null` when the user cancelled the save
 * dialog.
 */
suspend fun makeAndSavePdf(
  repository: ResumePdfRepository,
  picker: FilePicker,
  request: MakeResumePdf,
): PdfOutcome? =
  try {
    val pdf = repository.makePdf(request)
    if (picker.save(pdf.fileName, "application/pdf", Base64.decode(pdf.data))) {
      PdfOutcome.Saved(pdf)
    } else null
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    PdfOutcome.Failed(e.userMessage())
  }

/** The computer's message (written for the user), or the generic one. */
internal fun Exception.userMessage(): String =
  (this as? ApiFailure)?.message ?: ApiErrorCode.INTERNAL.defaultMessage

enum class PdfBusy {
  LOADING,
  IMPORTING,
  MAKING,
}

@Immutable
data class ResumePdfUiState(
  val status: ResumePdfStatus = ResumePdfStatus(),
  val busy: PdfBusy? = PdfBusy.LOADING,
  val outcome: PdfOutcome? = null,
  /** Why the last import or removal failed. */
  val error: String? = null,
  val showSteps: Boolean = false,
)

sealed interface ResumePdfEvent {
  data object ImportLinkedIn : ResumePdfEvent

  data object RemoveLinkedIn : ResumePdfEvent

  data object ToggleSteps : ResumePdfEvent

  data class SetPageSize(val size: PdfPageSize) : ResumePdfEvent

  data class SetPages(val pages: Int) : ResumePdfEvent

  data class SetUseLinkedIn(val use: Boolean) : ResumePdfEvent

  data class Move(val section: PdfSection, val up: Boolean) : ResumePdfEvent

  data class SetShown(val section: PdfSection, val shown: Boolean) : ResumePdfEvent

  data object Save : ResumePdfEvent
}

/** The resume PDF part of Settings: the LinkedIn data, the PDF preferences and the general PDF. */
class ResumePdfViewModel(
  private val repository: ResumePdfRepository,
  private val picker: FilePicker,
) : ViewModel() {
  private val _state = MutableStateFlow(ResumePdfUiState())
  val state: StateFlow<ResumePdfUiState> = _state.asStateFlow()

  /** The last save of the preferences; a newer one replaces it, so an older answer can't win. */
  private var saving: Job? = null

  init {
    viewModelScope.launch {
      val status = runCatching { repository.status() }.getOrNull()
      _state.update { it.copy(status = status ?: it.status, busy = null) }
    }
  }

  fun onEvent(event: ResumePdfEvent) {
    val prefs = _state.value.status.prefs
    when (event) {
      ResumePdfEvent.ImportLinkedIn -> import()
      ResumePdfEvent.RemoveLinkedIn -> run(PdfBusy.IMPORTING) { repository.deleteLinkedIn() }
      ResumePdfEvent.ToggleSteps -> _state.update { it.copy(showSteps = !it.showSteps) }
      is ResumePdfEvent.SetPageSize -> savePrefs(prefs.copy(pageSize = event.size))
      is ResumePdfEvent.SetPages -> savePrefs(prefs.copy(maxPages = event.pages))
      is ResumePdfEvent.SetUseLinkedIn -> savePrefs(prefs.copy(useLinkedIn = event.use))
      is ResumePdfEvent.Move -> savePrefs(prefs.copy(order = moved(prefs.fullOrder, event)))
      is ResumePdfEvent.SetShown ->
        savePrefs(
          prefs.copy(
            hidden = if (event.shown) prefs.hidden - event.section else prefs.hidden + event.section
          )
        )
      ResumePdfEvent.Save -> save()
    }
  }

  private fun moved(order: List<PdfSection>, move: ResumePdfEvent.Move): List<PdfSection> {
    val at = order.indexOf(move.section)
    val to = if (move.up) at - 1 else at + 1
    if (at < 0 || to !in order.indices) return order
    return order.toMutableList().apply { add(to, removeAt(at)) }
  }

  private fun savePrefs(prefs: ResumePdfPrefs) {
    // Shown at once; the computer's answer follows.
    _state.update { it.copy(status = it.status.copy(prefs = prefs), outcome = null) }
    saving?.cancel()
    saving = viewModelScope.launch {
      runCatching { repository.savePrefs(prefs) }
        .onSuccess { status -> _state.update { it.copy(status = status) } }
    }
  }

  private fun import() {
    if (_state.value.busy != null) return
    viewModelScope.launch {
      val file =
        try {
          picker.pickLinkedInExport()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        } ?: return@launch
      run(PdfBusy.IMPORTING) { repository.importLinkedIn(file.name, file.bytes) }
    }
  }

  private fun run(busy: PdfBusy, block: suspend () -> ResumePdfStatus) {
    _state.update { it.copy(busy = busy, error = null, outcome = null) }
    viewModelScope.launch {
      try {
        val status = block()
        _state.update { it.copy(status = status, busy = null) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update { it.copy(busy = null, error = e.userMessage()) }
      }
    }
  }

  private fun save() {
    if (_state.value.busy != null) return
    _state.update { it.copy(busy = PdfBusy.MAKING, outcome = null, error = null) }
    viewModelScope.launch {
      val outcome = makeAndSavePdf(repository, picker, MakeResumePdf())
      _state.update { it.copy(busy = null, outcome = outcome) }
    }
  }
}
