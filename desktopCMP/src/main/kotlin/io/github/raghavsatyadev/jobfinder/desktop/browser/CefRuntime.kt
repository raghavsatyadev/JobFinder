package io.github.raghavsatyadev.jobfinder.desktop.browser

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.CefBuildInfo
import me.friwi.jcefmaven.EnumProgress
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefRequestContext
import org.cef.callback.CefCommandLine
import org.cef.handler.CefAppHandlerAdapter
import org.cef.network.CefCookieManager
import org.slf4j.LoggerFactory

/**
 * Owns the process-wide [CefApp]: starts it on first use with the persistent profile and the
 * bundled natives, and shuts it down on quit. CEF can start only once per process.
 */
class CefRuntime(val paths: AppPaths) {
  private val log = LoggerFactory.getLogger(CefRuntime::class.java)
  private val terminated = CountDownLatch(1)
  private val initialized = CountDownLatch(1)
  private var keepAlive: CefClient? = null
  @Volatile private var app: CefApp? = null
  private var promptsDisabled = false

  /** The Chrome user agent every browser sends; it matches the bundled Chromium major version. */
  val userAgent: String by lazy {
    CefConfig.chromeUserAgent(CefConfig.chromiumMajor(CefBuildInfo.fromClasspath().releaseTag))
  }

  /** `true` once [start] has run (CEF may have been shut down since). */
  val isStarted: Boolean
    get() = app != null

  /** Starts CEF if needed. Blocks for a few seconds on first call (longer on first install). */
  @Synchronized
  fun start(): CefApp {
    app?.let {
      return it
    }
    paths.cefProfile.mkdirs()
    val builder = CefAppBuilder()
    builder.setInstallDir(paths.cefNatives)
    // Natives come from the jcef-natives jar on the classpath. No mirrors: never download.
    builder.mirrors = emptyList()
    builder.setProgressHandler { state, percent ->
      if (state != EnumProgress.DOWNLOADING || percent < 0) log.info("JCEF {}", state)
    }
    CefConfig.applySettings(builder.cefSettings, paths, userAgent)
    // For debugging page problems from Chrome's DevTools (chrome://inspect); off unless set.
    System.getenv(DEBUG_PORT_ENV)?.toIntOrNull()?.let {
      builder.cefSettings.remote_debugging_port = it
    }
    // Not builder.setAppHandler: it takes a MavenCefAppHandlerAdapter, whose final
    // onBeforeCommandLineProcessing can only append switches, and an appended second
    // --disable-features would replace the list CEF sets. That adapter only matters on macOS.
    CefApp.addAppHandler(
      object : CefAppHandlerAdapter(null) {
        override fun onBeforeCommandLineProcessing(
          processType: String,
          commandLine: CefCommandLine,
        ) {
          if (processType.isEmpty()) CefConfig.applySwitches(commandLine)
        }

        override fun stateHasChanged(state: CefApp.CefAppState) {
          log.info("CEF state {}", state)
          if (state == CefApp.CefAppState.INITIALIZED) initialized.countDown()
          if (state == CefApp.CefAppState.TERMINATED) terminated.countDown()
        }
      }
    )
    val started = builder.build()
    log.info("CEF {} started, profile {}", started.version, paths.cefProfile)
    app = started
    return started
  }

  /** Creates a client (handlers and browsers hang off it). Starts CEF if needed. */
  fun createClient(): CefClient = start().createClient()

  /**
   * Turns off Chrome's saved-password, autofill and passkey suggestions on the persistent profile.
   * Their dropdowns are native popups that take no clicks in a browser embedded in another window,
   * so they only get in the way of typing. Call on the Swing thread once CEF has started.
   */
  fun disableAutofillPrompts() {
    if (promptsDisabled) return
    promptsDisabled = true
    val context = CefRequestContext.getGlobalContext()
    for ((name, value) in AUTOFILL_PREFERENCES) {
      if (!context.canSetPreference(name)) continue
      context.setPreference(name, value)?.let { log.warn("Could not set {}: {}", name, it) }
    }
  }

  /**
   * Starts CEF and waits until it is initialized, which CEF only does once a client exists; the
   * cookie manager is `null` before. Call off the Swing thread: CEF initializes on it.
   */
  suspend fun awaitReady(timeout: Duration = 30.seconds) {
    check(!SwingUtilities.isEventDispatchThread()) { "CEF initializes on the Swing thread" }
    withContext(Dispatchers.Swing) {
      start()
      if (keepAlive == null) keepAlive = createClient()
    }
    runInterruptible {
      if (!initialized.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
        error("CEF did not initialize within $timeout")
      }
    }
  }

  /** Writes cookies to disk now instead of on CEF's own schedule. */
  fun flushCookies(timeout: Duration = 5.seconds) {
    if (app == null) return
    val done = CountDownLatch(1)
    val manager = CefCookieManager.getGlobalManager() ?: return
    if (manager.flushStore { done.countDown() }) {
      done.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    }
  }

  /**
   * Closes every browser and shuts CEF down, waiting up to [timeout] when called off the EDT (CEF
   * finishes its shutdown on the EDT, so the EDT cannot wait for it). Safe to call more than once.
   */
  fun shutdown(timeout: Duration = 15.seconds) {
    val current = app ?: return
    if (CefApp.getState() == CefApp.CefAppState.TERMINATED) return
    flushCookies()
    current.dispose()
    if (!SwingUtilities.isEventDispatchThread()) {
      if (!terminated.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)) {
        log.warn("CEF did not shut down within {}", timeout)
      }
    }
  }

  private companion object {
    const val DEBUG_PORT_ENV = "F2A_CEF_DEBUG_PORT"
    val AUTOFILL_PREFERENCES: Map<String, Any> =
      mapOf(
        "credentials_enable_service" to false,
        "profile.password_manager_enabled" to false,
        "autofill.profile_enabled" to false,
        "autofill.credit_card_enabled" to false,
      )
  }
}
