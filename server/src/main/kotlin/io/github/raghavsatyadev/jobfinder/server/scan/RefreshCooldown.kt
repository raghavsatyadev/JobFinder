package io.github.raghavsatyadev.jobfinder.server.scan

import java.io.File
import org.slf4j.LoggerFactory

/**
 * When the user may ask for the next scan of every link (the Refresh button). Scanning job sites
 * too often gets the user's IP address blocked, so a Refresh blocks the next one for
 * [REFRESH_COOLDOWN_MILLIS]. Kept in [file] so restarting the app does not lift it; `null` keeps it
 * in memory only.
 */
class RefreshCooldown(private val file: File? = null) {
  private val log = LoggerFactory.getLogger(RefreshCooldown::class.java)

  /** Epoch milliseconds from which a Refresh may start a scan; `null` when it may now. */
  @Volatile
  var allowedAt: Long? = read()
    private set

  fun allows(now: Long): Boolean = allowedAt.let { it == null || now >= it }

  /** Blocks the next Refresh until [REFRESH_COOLDOWN_MILLIS] after [now]. */
  fun start(now: Long) {
    val next = now + REFRESH_COOLDOWN_MILLIS
    allowedAt = next
    try {
      file?.apply { parentFile?.mkdirs() }?.writeText(next.toString())
    } catch (e: Exception) {
      log.warn("Could not save the refresh cooldown", e)
    }
  }

  private fun read(): Long? =
    try {
      file?.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
    } catch (e: Exception) {
      log.warn("Could not read the refresh cooldown", e)
      null
    }

  companion object {
    const val REFRESH_COOLDOWN_MILLIS = 4 * 60 * 60 * 1000L
  }
}
