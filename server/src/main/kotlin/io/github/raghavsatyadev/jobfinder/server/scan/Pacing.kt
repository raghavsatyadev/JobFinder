package io.github.raghavsatyadev.jobfinder.server.scan

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The scan's waits, ported from the old Electron scanner: random pauses keep Cloudflare and
 * LinkedIn from rate limiting, and the retry backoffs use full jitter like the
 * `exponential-backoff` package did.
 */
class Pacing(private val random: Random = Random.Default) {
  /** After each scroll, for the next page of an infinite list to load. */
  val scrollWait: Duration = 2.seconds

  /** After a job list page was stored, before the browser takes the next link. */
  fun betweenLinks(): Duration = between(5_000, 20_000)

  /** After a job page was stored, before the browser takes the next job. */
  fun betweenJobs(): Duration = between(2_000, 8_000)

  /** [items] in a random order, so each scan visits the sites in a different order. */
  fun <T> order(items: List<T>): List<T> = items.shuffled(random)

  /** After an HTTP 429 or a Cloudflare challenge; the page is then given up. */
  fun rateLimited(): Duration = between(20_000, 40_000)

  /** Before load attempt [attempt] (1-based) after an auth wall or a load error. */
  fun loadRetry(attempt: Int): Duration = jitter(LOAD_RETRY_START_MS, attempt)

  /** Before re-reading a page whose parse failed. */
  fun readRetry(attempt: Int): Duration = jitter(READ_RETRY_START_MS, attempt)

  private fun between(fromMs: Long, toMs: Long): Duration =
    random.nextLong(fromMs, toMs + 1).milliseconds

  private fun jitter(startMs: Long, attempt: Int): Duration {
    val cap = (startMs shl (attempt - 1).coerceIn(0, MAX_SHIFT)).coerceAtMost(MAX_RETRY_DELAY_MS)
    return random.nextLong(0, cap + 1).milliseconds
  }

  private companion object {
    const val LOAD_RETRY_START_MS = 100L
    const val READ_RETRY_START_MS = 1_000L
    const val MAX_RETRY_DELAY_MS = 5_000L
    const val MAX_SHIFT = 16
  }
}
