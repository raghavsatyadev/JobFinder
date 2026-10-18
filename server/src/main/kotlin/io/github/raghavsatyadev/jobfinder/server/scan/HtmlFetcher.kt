package io.github.raghavsatyadev.jobfinder.server.scan

/**
 * Loads a page in a real browser engine and returns its rendered DOM.
 *
 * The desktop app implements it with a pool of hidden JCEF browsers that share one persistent
 * profile, so cookies from a login carry over to every scan. Implementations queue calls when all
 * browsers are busy.
 */
interface HtmlFetcher {
  /**
   * Loads [url], waits for the load to finish, scrolls every scrollable element [scrollTimes] times
   * (to trigger infinite lists), then reads `document.documentElement.outerHTML`.
   */
  suspend fun fetchHtml(url: String, scrollTimes: Int = DEFAULT_SCROLL_TIMES): FetchResult

  companion object {
    /** Same default as the old Electron `HtmlDownloader.loadUrl`. */
    const val DEFAULT_SCROLL_TIMES = 3
  }
}

/** Result of [HtmlFetcher.fetchHtml]. */
data class FetchResult(
  /** The URL that was requested. */
  val url: String,
  /** The URL after redirects; an auth wall shows up here (for example `/authwall` or `/login`). */
  val finalUrl: String,
  /** `document.documentElement.outerHTML` after the scrolls. */
  val html: String,
  /** HTTP status of the main document, or `null` when the engine did not report one. */
  val httpStatus: Int?,
  /** `document.title`; "Just a moment..." means a Cloudflare challenge. */
  val title: String,
)
