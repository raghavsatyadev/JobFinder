package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** A saved job search URL that the scanner visits. Timestamps are epoch milliseconds (UTC). */
@Serializable
data class Link(
  val id: Long,
  val url: String,
  val title: String,
  val siteId: Long,
  val createdAt: Long,
  /** Consecutive failed scans; the UI warns about a broken link at 3 or more. */
  val scrapeFailureCount: Int = 0,
  val lastScrapedAt: Long,
)
