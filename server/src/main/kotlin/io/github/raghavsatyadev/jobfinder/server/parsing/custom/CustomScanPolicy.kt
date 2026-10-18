package io.github.raghavsatyadev.jobfinder.server.parsing.custom

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * The old `scan-urls` rule `wasScrapedWithinLast24h`: custom boards cost an LLM call, so a link is
 * scanned at most once per 24h, measured from the last successful scrape.
 */
object CustomScanPolicy {
  val MIN_INTERVAL_MS: Long = 24.hours.inWholeMilliseconds

  /**
   * `last_scraped_at` defaults to the insert time, so a new link looks just scraped. It only counts
   * as a real scrape when it is more than this far past `created_at`.
   */
  val SCRAPE_LEEWAY_MS: Long = 1.minutes.inWholeMilliseconds

  /** True when a custom-board link must be skipped now. All values are epoch milliseconds. */
  fun shouldSkip(lastScrapedAtMs: Long, createdAtMs: Long, nowMs: Long): Boolean {
    val hasRealScrape = lastScrapedAtMs - createdAtMs > SCRAPE_LEEWAY_MS
    if (!hasRealScrape) return false
    return nowMs - lastScrapedAtMs < MIN_INTERVAL_MS
  }
}
