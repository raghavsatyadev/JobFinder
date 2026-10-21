package io.github.raghavsatyadev.jobfinder.ui.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/** The address phones pair with, and the text of the pairing QR code. */
data class PairingInfo(val baseUrl: String, val pairingCode: String)

/** The app's browser opened: [page] is its extensions address, [copied] if on the clipboard. */
data class AppBrowserOpened(val page: String, val copied: Boolean)

/** Which job sites the scanner is signed in to, from one look at each saved search's site. */
sealed interface SignInCheck {
  /** No search saved yet, so there is no site to sign in to. */
  data object NoSearches : SignInCheck

  /**
   * [ready] sites show the scanner full results; [needed] ones want a sign-in, with a page each.
   */
  data class Checked(val ready: List<SignInPage>, val needed: List<SignInPage>) : SignInCheck
}

/** A job site, by host, and the saved search page to sign in on. */
data class SignInPage(val site: String, val url: String)

/** How copying the browser's sign-ins went. */
sealed interface SignInCopy {
  /** [cookies] copied; [missing] boards the extension may not read. */
  data class Copied(val cookies: Int, val missing: List<String>) : SignInCopy

  /** JobFinder Helper is not installed, or not connected. */
  data object NoExtension : SignInCopy

  /** The extension's "Share my job board sign-ins" switch is off. */
  data object SharingOff : SignInCopy

  data class Failed(val message: String) : SignInCopy
}

/**
 * What only the desktop app can do. The desktop binds it in Koin; on Android it is absent and the
 * screens hide these actions (resolve it with `getOrNull()`).
 */
interface DesktopFeatures {
  /** The pairing address and code, or `null` while the API server is off (no LAN address). */
  val pairing: StateFlow<PairingInfo?>

  /** Replaces the pairing token; every paired phone has to pair again. */
  suspend fun rotatePairingToken()

  /** Opens the sign-in browser on the scanner's profile, at [url] or a blank page. */
  fun openLoginBrowser(url: String? = null)

  /** Looks at each saved search's site in the scanner's browser; `null` when it cannot. */
  suspend fun checkSignIns(): SignInCheck? = null

  /** The Chrome or Edge the app signs in with, by name; `null` when neither is installed. */
  val appBrowserName: String?
    get() = null

  /**
   * Opens that browser on the app's own profile, so the extension can be added there. Browsers do
   * not open their extensions page from the command line, so its address goes to the clipboard.
   * Returns `null` when the browser did not open.
   */
  suspend fun openAppBrowserForExtension(): AppBrowserOpened? = null

  /** Copies the job board sign-ins from the user's own browser through the extension. */
  suspend fun copyBrowserSignIns(): SignInCopy

  /**
   * The add-search browser: the user browses to a search results page and confirms. Calls
   * [onPicked] with the page's URL and title, or [onCancel].
   */
  @Composable
  fun AddLinkBrowser(onPicked: (url: String, title: String) -> Unit, onCancel: () -> Unit)
}
