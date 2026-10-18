package io.github.raghavsatyadev.jobfinder.ui.pairing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.ui.platform.FoundComputer
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingError
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.github.raghavsatyadev.jobfinder.ui.platform.SavedComputer
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A pairing request waiting for the user on the computer. */
@Immutable data class PendingApproval(val computer: String, val code: String?)

/** What the pairing screen shows. */
@Immutable
data class PairingUiState(
  val code: String = "",
  val busy: Boolean = false,
  val error: PairingError? = null,
  val scanFailed: Boolean = false,
  /** Computers announcing JobFinder on this Wi-Fi. */
  val found: List<FoundComputer> = emptyList(),
  /** Saved computers other than the active one (when adding another). */
  val saved: List<SavedComputer> = emptyList(),
  /**
   * Set while a request is under way; [PendingApproval.code] once the computer took it, which is
   * when the approval card shows. Until then the computer may not answer at all.
   */
  val approval: PendingApproval? = null,
  /** The computer found on this Wi-Fi that then did not answer: likely its firewall. */
  val silentComputer: String? = null,
  /** `true` when a computer is already paired: the screen has a way back. */
  val canClose: Boolean = false,
) {
  val canSubmit: Boolean
    get() = code.isNotBlank() && !busy
}

sealed interface PairingUiEvent {
  data object Scan : PairingUiEvent

  data class CodeChanged(val text: String) : PairingUiEvent

  /** Pairs with the pasted code, or asks the typed address for approval. */
  data object Submit : PairingUiEvent

  data class PickFound(val computer: FoundComputer) : PairingUiEvent

  data class PickSaved(val key: String) : PairingUiEvent

  data object CancelApproval : PairingUiEvent

  data object Close : PairingUiEvent
}

/**
 * Pairs the phone: with a computer found on the Wi-Fi or typed in (the user allows it on the
 * computer), or from a scanned or pasted code. On success [PhonePairing.address] changes.
 */
class PairingViewModel(private val pairing: PhonePairing) : ViewModel() {
  private val _state = MutableStateFlow(PairingUiState())
  val state: StateFlow<PairingUiState> = _state.asStateFlow()
  private var approvalJob: Job? = null

  init {
    viewModelScope.launch {
      pairing.computers.collect { list ->
        _state.update { s ->
          s.copy(saved = list.filterNot { it.active }, canClose = list.any { it.active })
        }
      }
    }
  }

  fun onEvent(event: PairingUiEvent) {
    when (event) {
      PairingUiEvent.Scan -> scan()
      is PairingUiEvent.CodeChanged ->
        _state.update {
          it.copy(code = event.text, error = null, scanFailed = false, silentComputer = null)
        }
      PairingUiEvent.Submit -> submit(_state.value.code)
      is PairingUiEvent.PickFound -> pickFound(event.computer)
      is PairingUiEvent.PickSaved -> pairing.select(event.key)
      PairingUiEvent.CancelApproval -> {
        approvalJob?.cancel()
        _state.update { it.copy(busy = false, approval = null) }
      }
      PairingUiEvent.Close -> {
        approvalJob?.cancel()
        _state.update { it.copy(busy = false, approval = null, error = null) }
        pairing.showPairingScreen(false)
      }
    }
  }

  /** Lists the computers on the Wi-Fi until cancelled; the screen runs it while it shows. */
  suspend fun watchNetwork() {
    try {
      pairing.discover().collect { list -> _state.update { it.copy(found = list) } }
    } finally {
      _state.update { it.copy(found = emptyList()) }
    }
  }

  /** Pairs with [code] from outside the screen (the debug deep link). */
  fun pairWith(code: String) = pairWithCode(code)

  private fun submit(text: String) {
    // A pairing code is JSON; anything else is taken as an address.
    if (text.trimStart().startsWith("{")) pairWithCode(text) else askForApproval(text, text.trim())
  }

  private fun pickFound(computer: FoundComputer) {
    val key = computer.savedKey
    if (key != null) pairing.select(key)
    else askForApproval(computer.address, computer.name, found = true)
  }

  private fun scan() {
    if (_state.value.busy) return
    _state.update { it.copy(error = null, scanFailed = false) }
    viewModelScope.launch {
      val code =
        try {
          pairing.scanCode()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          _state.update { it.copy(scanFailed = true) }
          return@launch
        }
      if (code != null) pairWithCode(code)
    }
  }

  private fun pairWithCode(code: String) {
    if (_state.value.busy || code.isBlank()) return
    _state.update { it.copy(busy = true, error = null, scanFailed = false) }
    viewModelScope.launch {
      val error =
        try {
          pairing.pair(code.trim())
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          PairingError.DESKTOP_DID_NOT_ANSWER
        }
      finish(error)
    }
  }

  private fun askForApproval(address: String, computer: String, found: Boolean = false) {
    if (_state.value.busy || address.isBlank()) return
    _state.update {
      it.copy(
        busy = true,
        error = null,
        scanFailed = false,
        silentComputer = null,
        approval = PendingApproval(computer, null),
      )
    }
    approvalJob = viewModelScope.launch {
      val error =
        try {
          pairing.requestApproval(address.trim()) { code ->
            _state.update { it.copy(approval = PendingApproval(computer, code)) }
          }
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          PairingError.DESKTOP_DID_NOT_ANSWER
        }
      finish(error, silentComputer = computer.takeIf { found })
    }
  }

  private fun finish(error: PairingError?, silentComputer: String? = null) {
    _state.update {
      it.copy(
        busy = false,
        error = error,
        silentComputer = silentComputer.takeIf { error == PairingError.DESKTOP_DID_NOT_ANSWER },
        approval = null,
        code = if (error == null) "" else it.code,
      )
    }
  }
}
