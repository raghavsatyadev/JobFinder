package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.github.raghavsatyadev.jobfinder.ui.platform.SavedComputer
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInPage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the settings screen shows. [scanner] is `null` until the first load succeeds. */
@Immutable
data class SettingsUiState(
  val loading: Boolean = true,
  val loadFailed: Boolean = false,
  val scanner: ScannerSettings? = null,
  /** `true` on desktop: the pairing and sign-in sections show. */
  val desktop: Boolean = false,
  val pairing: PairingInfo? = null,
  /** The port field as typed. */
  val portInput: String = "",
  /** The port saved in the settings. */
  val savedPort: Int = ServerSettings.DEFAULT_PORT,
  val confirmRotate: Boolean = false,
  val rotating: Boolean = false,
  /** `true` on Android: the paired computers section shows. */
  val phone: Boolean = false,
  val computers: List<SavedComputer> = emptyList(),
  val phoneConnected: Boolean = false,
  /** The computer the user is about to forget. */
  val confirmForget: SavedComputer? = null,
  /** A frequency more often than every 12 hours, waiting for the user to accept the risk. */
  val confirmFrequency: ScanFrequency? = null,
  /** Copying the browser's sign-ins through the extension. */
  val copyingSignIns: Boolean = false,
  /** How the last copy of the browser's sign-ins went. */
  val signInCopy: SignInCopy? = null,
  /** Looking at the job sites before opening the sign-in browser. */
  val checkingSignIns: Boolean = false,
  /** What that look found; the browser opened on [SignInCheck.Checked.needed] if any. */
  val signInCheck: SignInCheck? = null,
) {
  val portValid: Boolean
    get() = portInput.toIntOrNull()?.let { it in SettingsViewModel.PORT_RANGE } == true

  /** The port field differs from the saved port and is valid. */
  val canSavePort: Boolean
    get() = portValid && portInput.toInt() != savedPort
}

sealed interface SettingsUiEvent {
  data object Retry : SettingsUiEvent

  /** `null` is "Never". A risky choice asks first ([SettingsUiState.confirmFrequency]). */
  data class SetFrequency(val cronRule: String?) : SettingsUiEvent

  data object ConfirmFrequency : SettingsUiEvent

  data object DismissFrequency : SettingsUiEvent

  data class SetPreventSleep(val enabled: Boolean) : SettingsUiEvent

  data class SetSound(val enabled: Boolean) : SettingsUiEvent

  data class SetAiPaused(val paused: Boolean) : SettingsUiEvent

  /** A `JobLanguages` code; `null` turns translation off. */
  data class SetTranslateTo(val language: String?) : SettingsUiEvent

  data class SetInAppBrowser(val enabled: Boolean) : SettingsUiEvent

  /** `null` keeps filtered-out jobs forever. */
  data class SetExcludedKeepDays(val days: Int?) : SettingsUiEvent

  data class PortChanged(val text: String) : SettingsUiEvent

  data object SavePort : SettingsUiEvent

  data object RequestRotate : SettingsUiEvent

  data object DismissRotate : SettingsUiEvent

  data object ConfirmRotate : SettingsUiEvent

  /** Checks the job sites first; opens the browser only where a sign-in is needed. */
  data object OpenLoginBrowser : SettingsUiEvent

  /** Opens the sign-in browser even though every site is signed in, to switch accounts. */
  data object OpenLoginBrowserAnyway : SettingsUiEvent

  data object CopyBrowserSignIns : SettingsUiEvent

  data class SelectComputer(val key: String) : SettingsUiEvent

  data class RequestForget(val computer: SavedComputer) : SettingsUiEvent

  data object DismissForget : SettingsUiEvent

  data object ConfirmForget : SettingsUiEvent

  data object AddComputer : SettingsUiEvent

  data object CheckConnection : SettingsUiEvent
}

/** One-shot messages, shown in a snackbar. */
enum class SettingsEffect {
  SaveFailed,
  PortSaved,
  Rotated,
  RotateFailed,
}

/**
 * Scanner settings for every platform; on desktop ([desktop] bound) also the phone pairing and the
 * sign-in browser, on Android ([phone] bound) the paired computers and the connection check. A
 * change is shown at once and saved in the background; a failed save puts the old value back.
 */
class SettingsViewModel(
  private val settings: SettingsRepository,
  private val desktop: DesktopFeatures?,
  private val phone: PhonePairing? = null,
  private val doctor: ConnectionDoctor? = null,
) : ViewModel() {
  private val _state =
    MutableStateFlow(SettingsUiState(desktop = desktop != null, phone = phone != null))
  val state: StateFlow<SettingsUiState> = _state.asStateFlow()

  private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
  val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

  init {
    load()
    desktop?.let { features ->
      viewModelScope.launch {
        features.pairing.collect { p -> _state.update { it.copy(pairing = p) } }
      }
    }
    phone?.let { pairing ->
      viewModelScope.launch {
        pairing.computers.collect { list -> _state.update { it.copy(computers = list) } }
      }
      viewModelScope.launch {
        pairing.connected.collect { c -> _state.update { it.copy(phoneConnected = c) } }
      }
    }
  }

  fun onEvent(event: SettingsUiEvent) {
    when (event) {
      SettingsUiEvent.Retry -> load()
      is SettingsUiEvent.SetFrequency ->
        if (ScannerSettings.isRisky(event.cronRule)) {
          val choice = SCAN_FREQUENCIES.firstOrNull { it.cronRule == event.cronRule }
          _state.update { it.copy(confirmFrequency = choice) }
        } else {
          changeScanner { it.copy(cronRule = event.cronRule) }
        }
      SettingsUiEvent.ConfirmFrequency -> {
        val choice = _state.value.confirmFrequency
        _state.update { it.copy(confirmFrequency = null) }
        if (choice != null) changeScanner { it.copy(cronRule = choice.cronRule) }
      }
      SettingsUiEvent.DismissFrequency -> _state.update { it.copy(confirmFrequency = null) }
      is SettingsUiEvent.SetPreventSleep -> changeScanner { it.copy(preventSleep = event.enabled) }
      is SettingsUiEvent.SetSound -> changeScanner { it.copy(useSound = event.enabled) }
      is SettingsUiEvent.SetAiPaused -> changeScanner { it.copy(aiPaused = event.paused) }
      is SettingsUiEvent.SetTranslateTo -> changeScanner { it.copy(translateTo = event.language) }
      is SettingsUiEvent.SetExcludedKeepDays ->
        changeScanner { it.copy(excludedKeepDays = event.days) }
      is SettingsUiEvent.SetInAppBrowser ->
        changeScanner { it.copy(inAppBrowserEnabled = event.enabled) }
      is SettingsUiEvent.PortChanged ->
        _state.update { it.copy(portInput = event.text.filter(Char::isDigit).take(PORT_DIGITS)) }
      SettingsUiEvent.SavePort -> savePort()
      SettingsUiEvent.RequestRotate ->
        if (desktop != null) _state.update { it.copy(confirmRotate = true) }
      SettingsUiEvent.DismissRotate -> _state.update { it.copy(confirmRotate = false) }
      SettingsUiEvent.ConfirmRotate -> rotate()
      SettingsUiEvent.OpenLoginBrowser -> checkSignIns()
      SettingsUiEvent.OpenLoginBrowserAnyway -> openAnyway()
      SettingsUiEvent.CopyBrowserSignIns -> copySignIns()
      is SettingsUiEvent.SelectComputer -> phone?.select(event.key)
      is SettingsUiEvent.RequestForget ->
        if (phone != null) _state.update { it.copy(confirmForget = event.computer) }
      SettingsUiEvent.DismissForget -> _state.update { it.copy(confirmForget = null) }
      SettingsUiEvent.ConfirmForget -> {
        val computer = _state.value.confirmForget
        _state.update { it.copy(confirmForget = null) }
        if (computer != null) phone?.forget(computer.key)
      }
      SettingsUiEvent.AddComputer -> phone?.showPairingScreen(true)
      SettingsUiEvent.CheckConnection -> doctor?.show(true)
    }
  }

  private fun load() {
    _state.update { it.copy(loading = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val scanner = settings.getScannerSettings()
        val server = if (desktop != null) settings.getServerSettings() else null
        _state.update {
          it.copy(
            loading = false,
            scanner = scanner,
            savedPort = server?.port ?: it.savedPort,
            portInput = server?.port?.toString() ?: it.portInput,
          )
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _state.update { it.copy(loading = false, loadFailed = true) }
      }
    }
  }

  private fun changeScanner(change: (ScannerSettings) -> ScannerSettings) {
    val old = _state.value.scanner ?: return
    val new = change(old)
    if (new == old) return
    _state.update { it.copy(scanner = new) }
    viewModelScope.launch {
      try {
        val saved = settings.updateScannerSettings(new)
        _state.update { if (it.scanner == new) it.copy(scanner = saved) else it }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _state.update { if (it.scanner == new) it.copy(scanner = old) else it }
        _effects.trySend(SettingsEffect.SaveFailed)
      }
    }
  }

  private fun savePort() {
    val current = _state.value
    if (!current.canSavePort) return
    val port = current.portInput.toInt()
    viewModelScope.launch {
      try {
        val server = settings.getServerSettings()
        val saved = settings.updateServerSettings(server.copy(port = port))
        _state.update { it.copy(savedPort = saved.port) }
        _effects.trySend(SettingsEffect.PortSaved)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _effects.trySend(SettingsEffect.SaveFailed)
      }
    }
  }

  private fun checkSignIns() {
    val desktop = desktop ?: return
    if (_state.value.checkingSignIns) return
    _state.update { it.copy(checkingSignIns = true, signInCheck = null) }
    viewModelScope.launch {
      val result =
        try {
          desktop.checkSignIns()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          null
        }
      when (result) {
        null -> desktop.openLoginBrowser()
        is SignInCheck.Checked -> result.needed.forEach { desktop.openLoginBrowser(it.url) }
        SignInCheck.NoSearches -> Unit
      }
      _state.update { it.copy(checkingSignIns = false, signInCheck = result) }
    }
  }

  /** Opens every saved search's site, or a blank page without any. */
  private fun openAnyway() {
    val desktop = desktop ?: return
    val checked = _state.value.signInCheck as? SignInCheck.Checked
    val pages = checked?.let { (it.ready + it.needed).map(SignInPage::url) }.orEmpty()
    if (pages.isEmpty()) desktop.openLoginBrowser() else pages.forEach(desktop::openLoginBrowser)
  }

  private fun copySignIns() {
    val desktop = desktop ?: return
    if (_state.value.copyingSignIns) return
    _state.update { it.copy(copyingSignIns = true, signInCopy = null) }
    viewModelScope.launch {
      val result =
        try {
          desktop.copyBrowserSignIns()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          SignInCopy.Failed(e.message.orEmpty())
        }
      _state.update { it.copy(copyingSignIns = false, signInCopy = result) }
    }
  }

  private fun rotate() {
    val features = desktop ?: return
    if (_state.value.rotating) return
    _state.update { it.copy(confirmRotate = false, rotating = true) }
    viewModelScope.launch {
      try {
        features.rotatePairingToken()
        _effects.trySend(SettingsEffect.Rotated)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _effects.trySend(SettingsEffect.RotateFailed)
      } finally {
        _state.update { it.copy(rotating = false) }
      }
    }
  }

  companion object {
    /** Unprivileged ports only; the same range the desktop server accepts. */
    val PORT_RANGE = 1024..65535
    private const val PORT_DIGITS = 5
  }
}
