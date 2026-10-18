package io.github.raghavsatyadev.jobfinder.desktop.browser

import java.awt.Component
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.slf4j.LoggerFactory

/** What a page showed when the user finished with it. */
data class PageSnapshot(val url: String, val title: String, val html: String)

/**
 * A visible JCEF browser on the persistent profile, for embedding in a Compose window through
 * `SwingPanel`. With [popupsInPlace] a popup opens in this browser, so a search never leaves the
 * panel; without it popups get their own native windows and keep `window.opener`, which "Sign in
 * with Google" and similar flows need. Create and close it on the Swing thread.
 */
class EmbeddedBrowser(runtime: CefRuntime, startUrl: String, private val popupsInPlace: Boolean) :
  AutoCloseable {
  private val log = LoggerFactory.getLogger(EmbeddedBrowser::class.java)
  private val client: CefClient = runtime.createClient()
  private val browser: CefBrowser = client.createBrowser(startUrl, false, false)
  private val popups = mutableListOf<CefBrowser>()
  private val _url = MutableStateFlow(startUrl)
  private val _title = MutableStateFlow("")
  private val _canGoBack = MutableStateFlow(false)
  private val _canGoForward = MutableStateFlow(false)
  private var closed = false

  val url: StateFlow<String> = _url.asStateFlow()
  val title: StateFlow<String> = _title.asStateFlow()
  val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()
  val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

  val component: Component
    get() = browser.uiComponent

  init {
    SwingThreadMessagePump.acquire()
    runtime.disableAutofillPrompts()
    client.addDisplayHandler(
      object : CefDisplayHandlerAdapter() {
        override fun onAddressChange(source: CefBrowser?, frame: CefFrame?, newUrl: String?) {
          if (source === browser && frame?.isMain == true && newUrl != null) _url.value = newUrl
        }

        override fun onTitleChange(source: CefBrowser?, newTitle: String?) {
          if (source === browser) _title.value = newTitle.orEmpty()
        }
      }
    )
    client.addLoadHandler(
      object : CefLoadHandlerAdapter() {
        override fun onLoadingStateChange(
          source: CefBrowser?,
          isLoading: Boolean,
          canGoBack: Boolean,
          canGoForward: Boolean,
        ) {
          if (source !== browser) return
          _canGoBack.value = canGoBack
          _canGoForward.value = canGoForward
        }
      }
    )
    client.addRequestHandler(
      object : CefRequestHandlerAdapter() {
        // LinkedIn's passkey prompt is a native popup that takes no clicks here.
        override fun getResourceRequestHandler(
          browser: CefBrowser?,
          frame: CefFrame?,
          request: CefRequest?,
          isNavigation: Boolean,
          isDownload: Boolean,
          requestInitiator: String?,
          disableDefaultHandling: BoolRef?,
        ): CefResourceRequestHandler? =
          if (request?.url?.contains(HiddenBrowser.PASSKEY_URL) == true) HiddenBrowser.BlockRequest
          else null
      }
    )
    client.addLifeSpanHandler(
      object : CefLifeSpanHandlerAdapter() {
        override fun onBeforePopup(
          source: CefBrowser?,
          frame: CefFrame?,
          targetUrl: String?,
          targetFrameName: String?,
        ): Boolean {
          log.info("Popup requested: {} (frame '{}')", targetUrl, targetFrameName)
          if (!popupsInPlace) return false
          if (targetUrl != null) browser.loadURL(targetUrl)
          return true
        }

        override fun onAfterCreated(created: CefBrowser?) {
          if (created != null && created !== browser) synchronized(popups) { popups += created }
        }

        override fun onBeforeClose(closing: CefBrowser?) {
          synchronized(popups) { popups.remove(closing) }
        }
      }
    )
  }

  fun load(address: String) = browser.loadURL(normalizeAddress(address))

  fun goBack() = browser.goBack()

  fun goForward() = browser.goForward()

  fun reload() = browser.reload()

  /**
   * Reads the page's URL, title and rendered HTML (the old overlay browser's `finish`). LinkedIn
   * search pages first get the React context the parser needs. Throws when the page does not answer
   * within [timeout].
   */
  suspend fun snapshot(timeout: Duration = SNAPSHOT_TIMEOUT): PageSnapshot {
    val current = _url.value
    if (current.contains("linkedin.com")) {
      browser.executeJavaScript(BrowserScripts.LINKEDIN_REACT_CONTEXT, current, 0)
    }
    val html = CompletableDeferred<String>()
    browser.getSource { html.complete(it) }
    return PageSnapshot(current, _title.value, withTimeout(timeout) { html.await() })
  }

  /** Closes popups and the browser. Safe to call more than once. */
  override fun close() {
    if (closed) return
    closed = true
    synchronized(popups) {
      popups.forEach {
        it.setCloseAllowed()
        it.close(true)
      }
      popups.clear()
    }
    browser.setCloseAllowed()
    browser.close(true)
    client.dispose()
    SwingThreadMessagePump.release()
  }

  companion object {
    /** The old overlay browser's limit for reading the page. */
    val SNAPSHOT_TIMEOUT = 15.seconds

    /** Adds `https://` to an address typed without a scheme. */
    fun normalizeAddress(input: String): String {
      val text = input.trim()
      return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(text)) text else "https://$text"
    }
  }
}
