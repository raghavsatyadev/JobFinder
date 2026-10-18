package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** "Shouldn't be in New": the user thinks the AI filter let this job through wrongly. */
@Serializable
data class JobReport(
  /** Why it doesn't fit, in the user's words; optional. */
  val note: String? = null
) {
  companion object {
    const val MAX_NOTE = 1000
  }
}

/** The AI's second look at a reported job. */
@Serializable
data class JobReportResult(
  /** The job should have been filtered out. */
  val userRight: Boolean,
  /** Why, in a sentence. */
  val reason: String? = null,
  /** The job was moved to Filtered out. */
  val moved: Boolean = false,
)
