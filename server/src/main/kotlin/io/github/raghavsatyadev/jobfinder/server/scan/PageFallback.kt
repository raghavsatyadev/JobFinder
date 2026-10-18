package io.github.raghavsatyadev.jobfinder.server.scan

/**
 * Reads a page another way when the hidden browser cannot (a sign-in wall, a Cloudflare challenge
 * or a page that would not parse): in the user's own browser, through the browser extension.
 */
fun interface PageFallback {
  /** The page as the other reader saw it, or `null` when that reader is missing or failed. */
  suspend fun read(url: String): FallbackPage?
}

/** A page read by a [PageFallback]. */
data class FallbackPage(val html: String, val finalUrl: String, val title: String)
