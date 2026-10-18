package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a job is applied to. Not stored: derived from the job tags. LinkedIn marks Easy Apply jobs
 * with an "Easy Apply" tag; every other job is [EXTERNAL].
 */
@Serializable
enum class ApplyMethod(val wire: String) {
  @SerialName("easy_apply") EASY_APPLY("easy_apply"),
  @SerialName("external") EXTERNAL("external");

  companion object {
    /** The tag (compared case-insensitively) that marks an Easy Apply job. */
    const val EASY_APPLY_TAG = "easy apply"

    fun fromTags(tags: List<String>): ApplyMethod =
      if (tags.any { it.lowercase() == EASY_APPLY_TAG }) EASY_APPLY else EXTERNAL
  }
}
