package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lifecycle state of a job. [wire] is the value stored in the database and sent over the API; it
 * matches the old Postgres `"Job Status"` enum.
 */
@Serializable
enum class JobStatus(val wire: String) {
  @SerialName("new") NEW("new"),
  @SerialName("applied") APPLIED("applied"),
  @SerialName("archived") ARCHIVED("archived"),
  @SerialName("deleted") DELETED("deleted"),
  /** Found by a scan; waits for its description and the advanced-matching check. */
  @SerialName("processing") PROCESSING("processing"),
  @SerialName("excluded_by_advanced_matching")
  EXCLUDED_BY_ADVANCED_MATCHING("excluded_by_advanced_matching");

  companion object {
    /** The old Postgres status that the migrator maps to [ARCHIVED]. */
    const val LEGACY_DOWNLOADED = "downloaded"

    /** Parses a stored value. The legacy `downloaded` status becomes [ARCHIVED]. */
    fun fromWire(value: String): JobStatus =
      if (value == LEGACY_DOWNLOADED) ARCHIVED
      else
        entries.firstOrNull { it.wire == value }
          ?: throw IllegalArgumentException("Unknown job status: $value")
  }
}
