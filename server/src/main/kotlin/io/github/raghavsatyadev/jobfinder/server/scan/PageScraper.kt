package io.github.raghavsatyadev.jobfinder.server.scan

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/** One read of a loaded page, handed to [PageScraper.scrape]. */
data class PageRead(
  val html: String,
  val finalUrl: String,
  val title: String,
  val httpStatus: Int?,
  /**
   * 0 for the first read, 1 for the re-read after a failed parse, 2 for the [PageFallback] read.
   */
  val attempt: Int,
  /**
   * No more reads follow if this one fails: store what you have and keep the page for debugging.
   */
  val isLast: Boolean,
)

/** The page answered HTTP 429 or showed a Cloudflare challenge; it is not retried. */
class RateLimitedException(url: String) : RuntimeException("Rate limited on $url")

/** The page kept redirecting to a sign-in page. */
class AuthWallException(url: String) : RuntimeException("Sign-in wall at $url")

/**
 * Loads pages in the [pool] with the old Electron `HtmlDownloader`'s retry rules:
 * - an auth wall or load error is retried, up to [LOAD_ATTEMPTS] loads with a backoff of at most 5
 *   s;
 * - HTTP 429 or a "Just a moment" title waits 20-40 s and gives up, so the next page starts calm;
 * - every scrollable element is scrolled to its bottom `scrollTimes` times, 2 s apart;
 * - when the reader throws (a failed parse), the same page is read once more without reloading.
 *
 * When all of that fails, [fallback] (the browser extension) gets one go at the page.
 *
 * [isRunning] turns `false` when the app quits, which stops further retries.
 */
class PageScraper(
  private val pool: BrowserPool,
  private val pacing: Pacing = Pacing(),
  private val fallback: PageFallback? = null,
  private val isRunning: () -> Boolean = { true },
) : HtmlFetcher {
  private val log = LoggerFactory.getLogger(PageScraper::class.java)

  /**
   * Loads [url] in a free tab ([incognito] for sites that support signed-out pages), then calls
   * [read] with its HTML. The tab stays taken until [read] returns, so [read] can wait before the
   * next page without another caller taking the browser. When that fails, [fallback] reads the page
   * once more and [read] gets it as the last read.
   */
  suspend fun <T> scrape(
    url: String,
    scrollTimes: Int,
    incognito: Boolean = false,
    read: suspend (PageRead) -> T,
  ): T {
    val failure =
      try {
        return pool.withTab(incognito) { tab ->
          val status = loadWithRetries(tab, url, scrollTimes)
          readWithRetry(tab, url, status, read)
        }
      } catch (e: Exception) {
        if (e is CancellationException || fallback == null || !isRunning()) throw e
        e
      }
    val page = fallback.read(url) ?: throw failure
    log.info("Read {} through the browser extension after: {}", url, failure.message)
    try {
      return read(
        PageRead(
          html = page.html,
          finalUrl = page.finalUrl,
          title = page.title,
          httpStatus = null,
          attempt = READ_RETRIES + 1,
          isLast = true,
        )
      )
    } catch (e: Exception) {
      if (e !is CancellationException) e.addSuppressed(failure)
      throw e
    }
  }

  private suspend fun <T> readWithRetry(
    tab: BrowserTab,
    url: String,
    status: Int?,
    read: suspend (PageRead) -> T,
  ): T {
    var attempt = 0
    while (true) {
      val isLast = attempt >= READ_RETRIES || !isRunning()
      val page =
        PageRead(
          html = readHtml(tab),
          finalUrl = tab.currentUrl(),
          title = tab.title(),
          httpStatus = status,
          attempt = attempt,
          isLast = isLast,
        )
      try {
        return read(page)
      } catch (e: Exception) {
        if (e is CancellationException || isLast) throw e
        log.info("Reading {} failed ({}), reading it again", url, e.message)
        attempt++
        delay(pacing.readRetry(attempt))
      }
    }
  }

  /** The page as it is after the load and scrolls; for adding a link. */
  override suspend fun fetchHtml(url: String, scrollTimes: Int): FetchResult =
    scrape(url, scrollTimes) { FetchResult(url, it.finalUrl, it.html, it.httpStatus, it.title) }

  private suspend fun loadWithRetries(tab: BrowserTab, url: String, scrollTimes: Int): Int? {
    var attempt = 1
    while (true) {
      try {
        return loadOnce(tab, url, scrollTimes)
      } catch (e: Exception) {
        if (e is CancellationException || e is RateLimitedException) throw e
        if (attempt >= LOAD_ATTEMPTS || !isRunning()) throw e
        log.debug("Load {} of {} failed: {}", attempt, url, e.message)
        attempt++
        delay(pacing.loadRetry(attempt - 1))
      }
    }
  }

  private suspend fun loadOnce(tab: BrowserTab, url: String, scrollTimes: Int): Int? {
    log.info("Loading {}", url)
    val status = tab.load(url)
    val title = tab.title()
    if (status == HTTP_TOO_MANY_REQUESTS || title.lowercase().startsWith(CLOUDFLARE_TITLE)) {
      log.warn("Rate limited on {} (status {}, title '{}')", url, status, title)
      delay(pacing.rateLimited())
      throw RateLimitedException(url)
    }
    repeat(scrollTimes) {
      tab.scrollAll()
      delay(pacing.scrollWait)
      val current = tab.currentUrl()
      if (AUTH_WALLS.any { current.contains(it) }) {
        log.debug("Auth wall at {}", current)
        throw AuthWallException(current)
      }
    }
    return status
  }

  private suspend fun readHtml(tab: BrowserTab): String {
    if (tab.currentUrl().contains(LINKEDIN_HOST)) {
      try {
        tab.injectLinkedInContext()
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.error("Could not add the LinkedIn React context", e)
      }
    }
    return tab.html()
  }

  companion object {
    /** Loads of one page before an auth wall or load error is given up. */
    const val LOAD_ATTEMPTS = 20

    /** Re-reads of a loaded page after a failed parse. */
    const val READ_RETRIES = 1

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val CLOUDFLARE_TITLE = "just a moment"
    private const val LINKEDIN_HOST = "linkedin.com"
    private val AUTH_WALLS = listOf("authwall", "login")
  }
}
