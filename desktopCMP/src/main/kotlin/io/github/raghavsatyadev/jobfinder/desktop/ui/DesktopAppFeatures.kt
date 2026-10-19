package io.github.raghavsatyadev.jobfinder.desktop.ui

import androidx.compose.runtime.Composable
import io.github.raghavsatyadev.jobfinder.desktop.browser.CefRuntime
import io.github.raghavsatyadev.jobfinder.desktop.browser.ExtensionSignIns
import io.github.raghavsatyadev.jobfinder.desktop.browser.SystemBrowserSignIn
import io.github.raghavsatyadev.jobfinder.desktop.setup.SetupMemory
import io.github.raghavsatyadev.jobfinder.server.http.ApiServerHost
import io.github.raghavsatyadev.jobfinder.server.http.PairedPhones
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * The desktop's [DesktopFeatures]: the pairing QR from the embedded API server, and the sign-in and
 * add-search browsers on the scanner's JCEF profile. Every window it opens is a normal window:
 * never always-on-top.
 */
class DesktopAppFeatures(
  private val host: ApiServerHost,
  private val settings: LocalSettingsRepository,
  private val runtime: CefRuntime,
  private val login: LoginBrowserState = LoginBrowserState(),
  private val picked: PickedPages = PickedPages(),
  private val signIn: SystemBrowserSignIn? = null,
  private val phones: PairedPhones? = null,
  private val extensionSignIns: ExtensionSignIns? = null,
  /** Remembers a sign-in opened from anywhere, so the setup step shows done. */
  private val setupMemory: SetupMemory? = null,
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : DesktopFeatures, AutoCloseable {
  private val log = LoggerFactory.getLogger(DesktopAppFeatures::class.java)
  private val token = MutableStateFlow<String?>(null)

  init {
    scope.launch {
      try {
        token.compareAndSet(null, settings.pairingToken())
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.error("Could not read the pairing token", e)
      }
    }
  }

  override val pairing: StateFlow<PairingInfo?> =
    combine(host.endpoint, token) { endpoint, token ->
        if (endpoint == null || token == null) null
        else PairingInfo(endpoint.baseUrl, endpoint.pairingCode(token))
      }
      .stateIn(scope, SharingStarted.Eagerly, null)

  /**
   * Replaces the token, then restarts the API server: requests with the old token fail at once (the
   * token is read on every request) and the restart also ends open event streams.
   */
  override suspend fun rotatePairingToken() {
    token.value = settings.rotatePairingToken()
    phones?.forget()
    host.stop()
    host.refresh()
  }

  /**
   * Opens [url] for signing in: in the installed Chrome or Edge when there is one (it can do Google
   * sign-in, passkeys and saved passwords), else in the embedded sign-in window (Main shows it).
   */
  override fun openLoginBrowser(url: String?) {
    val target = url ?: BLANK_PAGE
    if (url != null) setupMemory?.signedIn = true
    if (signIn?.isAvailable != true) return login.open(target)
    scope.launch {
      try {
        signIn.open(target)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.warn("Could not open {}; using the embedded sign-in window", signIn.browserName, e)
        login.open(target)
      }
    }
  }

  override val appBrowserName: String?
    get() = signIn?.takeIf { it.isAvailable }?.browserName

  override suspend fun openAppBrowserForExtension(): String? {
    val browser = signIn?.takeIf { it.isAvailable } ?: return null
    val page = browser.extensionsPage ?: return null
    try {
      browser.open(BLANK_PAGE)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.warn("Could not open {} for the extension", browser.browserName, e)
      return null
    }
    try {
      Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(page), null)
    } catch (e: IllegalStateException) {
      log.warn("Could not copy {} to the clipboard", page, e)
    }
    return page
  }

  override suspend fun copyBrowserSignIns(): SignInCopy =
    extensionSignIns?.copy() ?: SignInCopy.NoExtension

  @Composable
  override fun AddLinkBrowser(
    onPicked: (url: String, title: String) -> Unit,
    onCancel: () -> Unit,
  ) {
    AddLinkBrowserPanel(runtime, picked, onPicked, onCancel)
  }

  override fun close() {
    scope.cancel()
  }

  companion object {
    private const val BLANK_PAGE = "about:blank"
  }
}
