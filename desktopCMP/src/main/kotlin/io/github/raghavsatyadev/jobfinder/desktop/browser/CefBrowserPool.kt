package io.github.raghavsatyadev.jobfinder.desktop.browser

import io.github.raghavsatyadev.jobfinder.server.scan.BrowserPool
import io.github.raghavsatyadev.jobfinder.server.scan.BrowserTab
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * The scan browsers: up to [size] persistent [HiddenBrowser]s on the shared profile (one login
 * serves them all) and one incognito browser, each created on first use and reused during a scan.
 * Callers beyond the limit wait for a free browser. [resize] changes the persistent limit while
 * scans run. A browser idle for [idleCloseMs] closes, so the app holds no web pages (and their
 * renderer processes) between scans.
 */
class CefBrowserPool(
  runtime: CefRuntime,
  size: Int = DEFAULT_SIZE,
  idleCloseMs: Long = IDLE_CLOSE_MS,
) : BrowserPool, AutoCloseable {
  private val reaper = Executors.newSingleThreadScheduledExecutor {
    Thread(it, "browser-reaper").apply { isDaemon = true }
  }
  private val persistent = BrowserLane(size, idleCloseMs, reaper) { HiddenBrowser.create(runtime) }
  private val incognito =
    BrowserLane(1, idleCloseMs, reaper) { HiddenBrowser.create(runtime, incognito = true) }

  override suspend fun <T> withTab(incognito: Boolean, block: suspend (BrowserTab) -> T): T =
    (if (incognito) this.incognito else persistent).use { block(HiddenBrowserTab(it)) }

  /** Sets how many persistent browsers may exist; idle ones above it close now, busy ones later. */
  fun resize(size: Int) {
    require(size in ScannerSettings.SCRAPER_CONCURRENCY_RANGE) { "Pool size out of range: $size" }
    persistent.resize(size)
  }

  /** Closes every browser. CEF itself stays up; [CefRuntime.shutdown] stops it. */
  override fun close() {
    reaper.shutdownNow()
    persistent.close()
    incognito.close()
  }

  /** A [HiddenBrowser] as the scraper sees it. */
  private class HiddenBrowserTab(private val browser: HiddenBrowser) : BrowserTab {
    override suspend fun load(url: String): Int? {
      var result = browser.load(url)
      // The PC switched networks (Wi-Fi reconnect, VPN): the page loads once the link settles.
      if (result.error?.contains(NETWORK_CHANGED) == true) {
        delay(NETWORK_SETTLE)
        result = browser.load(url)
      }
      result.error?.let { log.warn("Load error on {}: {}", url, it) }
      return result.httpStatus
    }

    override suspend fun currentUrl(): String = browser.url.orEmpty()

    override suspend fun title(): String = browser.evaluate("document.title")

    override suspend fun scrollAll() {
      browser.evaluate(BrowserScripts.SCROLL_ALL)
    }

    override suspend fun injectLinkedInContext() {
      browser.evaluate(BrowserScripts.LINKEDIN_REACT_CONTEXT)
    }

    override suspend fun html(): String = browser.evaluate(BrowserScripts.OUTER_HTML)
  }

  companion object {
    private val log = LoggerFactory.getLogger(CefBrowserPool::class.java)

    /** Same as the old Electron app's default number of scraper windows. */
    const val DEFAULT_SIZE = ScannerSettings.DEFAULT_SCRAPER_CONCURRENCY

    /** Long enough to keep the browsers through one scan, short enough to free them right after. */
    const val IDLE_CLOSE_MS = 60_000L

    private const val NETWORK_CHANGED = "ERR_NETWORK_CHANGED"
    private val NETWORK_SETTLE = 3.seconds
  }
}
