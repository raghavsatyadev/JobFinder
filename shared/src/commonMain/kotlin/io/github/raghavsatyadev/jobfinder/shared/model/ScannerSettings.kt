package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** Scan schedule and behaviour (the old Electron `settings.json`). */
@Serializable
data class ScannerSettings(
  /** Cron rule for scheduled scans; `null` means "Never". */
  val cronRule: String? = DEFAULT_CRON_RULE,
  val preventSleep: Boolean = true,
  val useSound: Boolean = true,
  val inAppBrowserEnabled: Boolean = true,
  /** Persistent hidden browsers used for scans (1–2). */
  val scraperConcurrency: Int = DEFAULT_SCRAPER_CONCURRENCY,
  /** Days a job the AI filtered out is kept before it is deleted; `null` keeps them forever. */
  val excludedKeepDays: Int? = DEFAULT_EXCLUDED_KEEP_DAYS,
  /**
   * Stops the AI work the app does on its own: ATS scores, company ratings and careers checks of
   * jobs nobody opened. A job the user opens still gets them.
   */
  val aiPaused: Boolean = false,
) {
  companion object {
    /**
     * "Every 12 hours", also the shortest gap we advise: job sites block an address that reads them
     * every few hours.
     */
    const val DEFAULT_CRON_RULE = "0 */12 * * *"

    /** Rules from the old app that scan more often than every 2 hours, and what they become. */
    private val TOO_OFTEN = setOf("*/30 * * * *", "0 * * * *")

    /** Choices the app still allows but warns against: more often than every 12 hours. */
    private val RISKY = setOf("0 */2 * * *", "0 */4 * * *", "0 */8 * * *")

    /** [rule], or every 12 hours when it is one of the old, too frequent choices. */
    fun limitFrequency(rule: String?): String? = if (rule in TOO_OFTEN) DEFAULT_CRON_RULE else rule

    /** `true` when [rule] scans more often than every 12 hours and may get the address blocked. */
    fun isRisky(rule: String?): Boolean = rule in RISKY || rule in TOO_OFTEN

    const val DEFAULT_SCRAPER_CONCURRENCY = 2
    const val DEFAULT_EXCLUDED_KEEP_DAYS = 30
    val EXCLUDED_KEEP_DAYS_RANGE = 1..365
    val SCRAPER_CONCURRENCY_RANGE = 1..2
  }
}

/** How the embedded API server listens on the LAN. */
@Serializable
data class ServerSettings(
  val port: Int = DEFAULT_PORT,
  /** Network interface name to bind to; `null` picks the default-route interface. */
  val networkInterface: String? = null,
) {
  companion object {
    const val DEFAULT_PORT = 47821
  }
}
