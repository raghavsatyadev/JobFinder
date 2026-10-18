package io.github.raghavsatyadev.jobfinder.desktop.browser

import io.github.raghavsatyadev.jobfinder.server.scan.FetchResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.browser.CefRequestContext
import org.cef.browser.HeadlessOsrBrowser
import org.cef.callback.CefJSDialogCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefJSDialogHandler
import org.cef.handler.CefJSDialogHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.slf4j.LoggerFactory

/**
 * One hidden, off-screen (windowless) browser on the shared persistent profile.
 *
 * The page sees a [CefConfig.VIEW_WIDTH] x [CefConfig.VIEW_HEIGHT] visible window. JavaScript
 * values come back through a [CefMessageRouter] query, which carries strings of many megabytes. Not
 * thread-safe: use one browser for one job at a time (see [CefBrowserPool]).
 */
class HiddenBrowser
private constructor(
  private val client: CefClient,
  private val browser: CefBrowser,
  private val context: CefRequestContext?,
) : AutoCloseable {
  private val pending = ConcurrentHashMap<String, CompletableDeferred<BrowserScripts.Reply>>()
  @Volatile private var load: LoadWaiter? = null
  private val closed = AtomicBoolean(false)

  /** Result of [load]. */
  data class LoadResult(val finalUrl: String, val httpStatus: Int?, val error: String?)

  private class LoadWaiter {
    val started = AtomicBoolean(false)
    val done = CompletableDeferred<Unit>()
    @Volatile var httpStatus: Int? = null
    @Volatile var error: String? = null
  }

  /**
   * Loads [url] and waits until the main document and its sub-resources finish loading, or until
   * [timeout] (the page is then used as it is).
   */
  suspend fun load(url: String, timeout: Duration = LOAD_TIMEOUT): LoadResult {
    val waiter = LoadWaiter()
    load = waiter
    browser.loadURL(url)
    if (withTimeoutOrNull(timeout) { waiter.done.await() } == null) {
      log.warn("Load did not finish within {}: {}", timeout, url)
    }
    load = null
    return LoadResult(browser.url ?: url, waiter.httpStatus, waiter.error)
  }

  /**
   * Evaluates the JS [expression] in the main frame and returns its value as a string (awaited if
   * it is a promise). Throws [JsException] when the script throws, and
   * [TimeoutCancellationException] when no reply comes within [timeout] (for example when the page
   * navigates away meanwhile).
   */
  suspend fun evaluate(expression: String, timeout: Duration = EVAL_TIMEOUT): String {
    val id = UUID.randomUUID().toString()
    val reply = CompletableDeferred<BrowserScripts.Reply>()
    pending[id] = reply
    try {
      browser.executeJavaScript(BrowserScripts.evaluate(id, expression), "f2a://bridge", 1)
      val result = withTimeout(timeout) { reply.await() }
      if (!result.ok) throw JsException(result.value)
      return result.value
    } finally {
      pending.remove(id)
    }
  }

  /** Scrolls every scrollable element to the bottom [times] times, waiting [wait] after each. */
  suspend fun scroll(times: Int, wait: Duration = SCROLL_WAIT) {
    repeat(times) {
      evaluate(BrowserScripts.SCROLL_ALL)
      delay(wait)
    }
  }

  /** Loads [url], scrolls [scrollTimes] times and reads the rendered DOM. */
  suspend fun fetch(url: String, scrollTimes: Int): FetchResult {
    val loaded = load(url)
    scroll(scrollTimes)
    return FetchResult(
      url = url,
      finalUrl = browser.url ?: loaded.finalUrl,
      html = evaluate(BrowserScripts.OUTER_HTML),
      httpStatus = loaded.httpStatus,
      title = evaluate("document.title"),
    )
  }

  /** Runs the DevTools Protocol [method] on this browser and returns its JSON result. */
  suspend fun devTools(
    method: String,
    params: String = "{}",
    timeout: Duration = EVAL_TIMEOUT,
  ): String {
    val tools = withContext(Dispatchers.Swing) { browser.devToolsClient }
    return withTimeout(timeout) { tools.executeDevToolsMethod(method, params).await() }
  }

  /** The current URL. */
  val url: String?
    get() = browser.url

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    pending.values.forEach { it.cancel() }
    browser.close(true)
    client.dispose()
    context?.dispose()
  }

  /** Thrown when an evaluated script throws; [message] holds the JS error and stack. */
  class JsException(message: String) : RuntimeException(message)

  companion object {
    private val log = LoggerFactory.getLogger(HiddenBrowser::class.java)
    private val LOAD_TIMEOUT = 60.seconds
    private val EVAL_TIMEOUT = 30.seconds
    private val SCROLL_WAIT = 2.seconds
    private val CREATE_TIMEOUT = 30.seconds

    /** LinkedIn's passkey prompt request; it opens a native popup and stalls the page. */
    internal const val PASSKEY_URL = "checkpoint/pk/initiateLogin"

    /**
     * Creates a browser on about:blank and waits until CEF has created it. An [incognito] browser
     * gets its own in-memory request context: no cookies from the profile, nothing written to disk.
     */
    suspend fun create(
      runtime: CefRuntime,
      incognito: Boolean = false,
      width: Int = CefConfig.VIEW_WIDTH,
      height: Int = CefConfig.VIEW_HEIGHT,
    ): HiddenBrowser {
      val created = CompletableDeferred<Unit>()
      lateinit var hidden: HiddenBrowser
      withContext(Dispatchers.Swing) {
        val client = runtime.createClient()
        // A context created without a cache path lives in memory only.
        val context = if (incognito) CefRequestContext.createContext(null) else null
        val browser = HeadlessOsrBrowser(client, "about:blank", context, width, height)
        hidden = HiddenBrowser(client, browser, context)
        hidden.installHandlers(created)
        browser.createImmediately()
      }
      try {
        withTimeout(CREATE_TIMEOUT) { created.await() }
      } catch (e: TimeoutCancellationException) {
        hidden.close()
        throw IllegalStateException("CEF did not create the hidden browser in $CREATE_TIMEOUT", e)
      }
      return hidden
    }
  }

  private fun installHandlers(created: CompletableDeferred<Unit>) {
    val router =
      CefMessageRouter.create(
        CefMessageRouter.CefMessageRouterConfig(
          BrowserScripts.QUERY_FUNCTION,
          BrowserScripts.CANCEL_FUNCTION,
        )
      )
    router.addHandler(
      object : CefMessageRouterHandlerAdapter() {
        override fun onQuery(
          browser: CefBrowser?,
          frame: CefFrame?,
          queryId: Long,
          request: String?,
          persistent: Boolean,
          callback: CefQueryCallback?,
        ): Boolean {
          val reply = request?.let(BrowserScripts::parseReply) ?: return false
          pending[reply.id]?.complete(reply)
          callback?.success("")
          return true
        }
      },
      true,
    )
    client.addMessageRouter(router)

    client.addLifeSpanHandler(
      object : CefLifeSpanHandlerAdapter() {
        override fun onAfterCreated(browser: CefBrowser?) {
          if (browser === this@HiddenBrowser.browser) created.complete(Unit)
        }

        // Hidden scrapers never open popups.
        override fun onBeforePopup(
          browser: CefBrowser?,
          frame: CefFrame?,
          targetUrl: String?,
          targetFrameName: String?,
        ): Boolean = true
      }
    )

    client.addLoadHandler(
      object : CefLoadHandlerAdapter() {
        override fun onLoadingStateChange(
          browser: CefBrowser?,
          isLoading: Boolean,
          canGoBack: Boolean,
          canGoForward: Boolean,
        ) {
          val waiter = load ?: return
          if (isLoading) waiter.started.set(true)
          else if (waiter.started.get()) waiter.done.complete(Unit)
        }

        override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
          if (frame?.isMain == true) load?.httpStatus = httpStatusCode
        }

        override fun onLoadError(
          browser: CefBrowser?,
          frame: CefFrame?,
          errorCode: CefLoadHandler.ErrorCode?,
          errorText: String?,
          failedUrl: String?,
        ) {
          // ERR_ABORTED is a navigation replaced by another one (redirects, SPA routing).
          if (frame?.isMain != true || errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) return
          load?.error = "$errorCode $errorText ($failedUrl)"
        }
      }
    )

    client.addRequestHandler(
      object : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
          browser: CefBrowser?,
          frame: CefFrame?,
          request: CefRequest?,
          isNavigation: Boolean,
          isDownload: Boolean,
          requestInitiator: String?,
          disableDefaultHandling: BoolRef?,
        ): CefResourceRequestHandler? =
          if (request?.url?.contains(PASSKEY_URL) == true) BlockRequest else null
      }
    )

    client.addJSDialogHandler(
      object : CefJSDialogHandlerAdapter() {
        // Nobody can answer a dialog in a hidden browser: drop alerts, cancel confirms.
        override fun onJSDialog(
          browser: CefBrowser?,
          originUrl: String?,
          dialogType: CefJSDialogHandler.JSDialogType?,
          messageText: String?,
          defaultPromptText: String?,
          callback: CefJSDialogCallback?,
          suppressMessage: BoolRef?,
        ): Boolean {
          suppressMessage?.set(true)
          return false
        }

        override fun onBeforeUnloadDialog(
          browser: CefBrowser?,
          messageText: String?,
          isReload: Boolean,
          callback: CefJSDialogCallback?,
        ): Boolean {
          callback?.Continue(true, "")
          return true
        }
      }
    )
  }

  /** Cancels a request before it is sent. */
  internal object BlockRequest : CefResourceRequestHandlerAdapter() {
    override fun onBeforeResourceLoad(
      browser: CefBrowser?,
      frame: CefFrame?,
      request: CefRequest?,
    ): Boolean = true
  }
}
