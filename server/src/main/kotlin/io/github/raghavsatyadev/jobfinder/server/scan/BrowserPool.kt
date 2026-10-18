package io.github.raghavsatyadev.jobfinder.server.scan

/**
 * One tab of a real browser engine, used by one caller at a time. The desktop app backs it with a
 * hidden JCEF browser; tests back it with a script.
 */
interface BrowserTab {
  /**
   * Loads [url] and waits until it finishes loading (at most the 60 s navigation timeout; the page
   * is then used as it is). Returns the HTTP status of the main document, or `null` when the engine
   * did not report one.
   */
  suspend fun load(url: String): Int?

  /** The URL shown now, after redirects and client-side routing. */
  suspend fun currentUrl(): String

  /** `document.title`. */
  suspend fun title(): String

  /** Scrolls every scrollable element to its bottom once, so infinite lists load more. */
  suspend fun scrollAll()

  /**
   * On a LinkedIn search page, copies each job card's React props into an `f2a-react-context`
   * attribute, where the LinkedIn parser finds the job ids.
   */
  suspend fun injectLinkedInContext()

  /** The rendered DOM, `document.documentElement.outerHTML`. */
  suspend fun html(): String
}

/**
 * Hidden browsers for scans: persistent ones on the shared profile, so a sign-in carries over to
 * every scan, and one in-memory (incognito) one for sites that show more to signed-out visitors
 * (LinkedIn job pages). Callers wait when every browser of the kind they asked for is busy.
 */
interface BrowserPool {
  suspend fun <T> withTab(incognito: Boolean = false, block: suspend (BrowserTab) -> T): T
}
