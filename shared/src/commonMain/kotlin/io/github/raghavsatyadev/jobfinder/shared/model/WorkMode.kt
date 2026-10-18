package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Work mode of a job (the old `jobType` column). [wire] is the stored, lower-case value. */
@Serializable
enum class WorkMode(val wire: String) {
  @SerialName("remote") REMOTE("remote"),
  @SerialName("hybrid") HYBRID("hybrid"),
  @SerialName("onsite") ONSITE("onsite");

  companion object {
    /** Case-insensitive, like the old `lower("jobType")` filter. */
    fun fromWireOrNull(value: String?): WorkMode? = value?.let { v ->
      entries.firstOrNull { it.wire.equals(v, ignoreCase = true) }
    }
  }
}
