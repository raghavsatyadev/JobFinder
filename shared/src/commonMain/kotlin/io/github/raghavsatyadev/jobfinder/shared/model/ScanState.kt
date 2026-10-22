package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** What the scanner is doing. Timestamps are epoch milliseconds (UTC). */
@Serializable
data class ScanState(
  val scanning: Boolean = false,
  val startedAt: Long? = null,
  val linksTotal: Int = 0,
  val linksDone: Int = 0,
  val jobsTotal: Int = 0,
  val jobsDone: Int = 0,
  val lastFinishedAt: Long? = null,
  /** New jobs stored by the last finished scan. */
  val lastNewJobs: Int = 0,
  /** When the user's Refresh may start the next scan; `null` or past means it may now. */
  val refreshAllowedAt: Long? = null,
)
