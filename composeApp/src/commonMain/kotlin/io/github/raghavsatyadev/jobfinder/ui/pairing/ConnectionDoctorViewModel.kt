package io.github.raghavsatyadev.jobfinder.ui.pairing

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckState
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DoctorUiState(
  val checks: List<DoctorCheck> = emptyList(),
  val running: Boolean = false,
  /** The active computer's name or address. */
  val computer: String = "",
) {
  /** Every check ran and none failed. */
  val allGood: Boolean
    get() =
      !running &&
        checks.isNotEmpty() &&
        checks.none { it.state == DoctorCheckState.FAILED || it.state == DoctorCheckState.SKIPPED }
}

sealed interface DoctorUiEvent {
  data object Run : DoctorUiEvent

  data object OpenWifiSettings : DoctorUiEvent

  /** Opens the pairing screen; the saved computer is replaced when paired again. */
  data object PairAgain : DoctorUiEvent

  data object Close : DoctorUiEvent
}

/** Runs the connection checks and their fixes. */
class ConnectionDoctorViewModel(
  private val doctor: ConnectionDoctor,
  private val pairing: PhonePairing,
) : ViewModel() {
  private val _state = MutableStateFlow(DoctorUiState())
  val state: StateFlow<DoctorUiState> = _state.asStateFlow()
  private var job: Job? = null

  init {
    viewModelScope.launch {
      pairing.computers.collect { list ->
        _state.update { s -> s.copy(computer = list.firstOrNull { it.active }?.label.orEmpty()) }
      }
    }
  }

  fun onEvent(event: DoctorUiEvent) {
    when (event) {
      DoctorUiEvent.Run -> run()
      DoctorUiEvent.OpenWifiSettings -> doctor.openWifiSettings()
      DoctorUiEvent.PairAgain -> {
        doctor.show(false)
        pairing.showPairingScreen(true)
      }
      DoctorUiEvent.Close -> {
        job?.cancel()
        _state.update { it.copy(running = false) }
        doctor.show(false)
      }
    }
  }

  private fun run() {
    job?.cancel()
    _state.update { it.copy(checks = emptyList(), running = true) }
    job = viewModelScope.launch {
      try {
        doctor.run().collect { checks -> _state.update { it.copy(checks = checks) } }
      } catch (e: Exception) {
        // A check that throws ends the run; what ran so far stays on screen.
        if (e is CancellationException) throw e
      }
      _state.update { it.copy(running = false) }
    }
  }
}
