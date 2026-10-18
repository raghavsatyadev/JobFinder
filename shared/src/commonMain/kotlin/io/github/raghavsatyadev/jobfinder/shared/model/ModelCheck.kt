package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a local model did against the user's saved answers. */
@Serializable
enum class ModelCheckState {
  /** Every answer agreed. */
  @SerialName("match") MATCH,
  /** Some answers agreed; see [ModelCheck.misses]. */
  @SerialName("partial") PARTIAL,
  /** No usable answer: the model would not load, timed out or gave unreadable answers. */
  @SerialName("failed") FAILED,
  /** Nothing to compare with yet: no saved matching decisions or ATS reviews. */
  @SerialName("no_samples") NO_SAMPLES,
}

/** One check the model did not pass. */
@Serializable
data class ModelCheckMiss(
  val kind: Kind,
  val jobTitle: String,
  val company: String,
  /** The saved answer, for example "kept", "excluded" or "7.4, shortlisted". */
  val expected: String,
  /** The model's answer, or `null` when it gave none. */
  val got: String?,
) {
  @Serializable
  enum class Kind {
    @SerialName("matching") MATCHING,
    @SerialName("ats") ATS,
  }
}

/**
 * The accuracy check of [model]: the AI filter on recent kept and excluded jobs, and ATS reviews,
 * compared with the answers saved in the database (made by the model in use before).
 */
@Serializable
data class ModelCheck(
  val model: String,
  val state: ModelCheckState,
  /** Agreed checks, 0..100. */
  val percent: Int,
  val agreed: Int,
  val checks: Int,
  /** Checks with no usable answer. */
  val failedAnswers: Int,
  val misses: List<ModelCheckMiss> = emptyList(),
  /** Average seconds per AI filter answer. */
  val secondsPerJob: Double? = null,
  /** Average seconds per ATS review. */
  val secondsPerReview: Double? = null,
  /** Memory the loaded model takes, and how much of it is on the graphics card. */
  val memoryGb: Double? = null,
  val gpuMemoryGb: Double? = null,
  val checkedAt: Long,
)
