package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** A user note on a job. [files] are attachment paths. Timestamps are epoch milliseconds (UTC). */
@Serializable
data class Note(
  val id: Long,
  val jobId: Long,
  val text: String,
  val files: List<String> = emptyList(),
  val createdAt: Long,
)
