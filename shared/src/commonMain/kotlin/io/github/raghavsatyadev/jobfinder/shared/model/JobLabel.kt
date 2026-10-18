package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** User label on a job. [wire] is the stored value (the old `JOB_LABELS` strings). */
@Serializable
enum class JobLabel(val wire: String) {
  @SerialName("Considering") CONSIDERING("Considering"),
  @SerialName("Submitted") SUBMITTED("Submitted"),
  @SerialName("Interviewing") INTERVIEWING("Interviewing"),
  @SerialName("Offer") OFFER("Offer"),
  @SerialName("Rejected") REJECTED("Rejected"),
  @SerialName("Ghosted") GHOSTED("Ghosted"),
  /** The star: shown as a star button and the Starred tab, not with the other labels. */
  @SerialName("Starred") STARRED("Starred");

  companion object {
    /** The labels picked from the label list; [STARRED] has its own button. */
    val CHOICES: List<JobLabel> = entries - STARRED

    fun fromWireOrNull(value: String): JobLabel? = entries.firstOrNull { it.wire == value }
  }
}
