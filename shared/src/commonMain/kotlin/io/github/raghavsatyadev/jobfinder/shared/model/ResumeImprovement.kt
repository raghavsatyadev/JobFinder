package io.github.raghavsatyadev.jobfinder.shared.model

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where in the resume an edit goes. */
@Serializable
enum class ResumeSection {
  @SerialName("summary") SUMMARY,
  @SerialName("experience") EXPERIENCE,
  @SerialName("skills") SKILLS,
  @SerialName("projects") PROJECTS,
  @SerialName("other") OTHER,
}

/**
 * One change to the resume for one job: [before] is copied from the resume, [after] replaces it. An
 * edit that brings in a skill or a number the resume does not show yet, or leaves a blank to fill,
 * [needsCheck]: use it only if it is true.
 */
@Serializable
data class ResumeEdit(
  val id: Int,
  val section: ResumeSection,
  val before: String,
  val after: String,
  val reason: String,
  /** The job's keywords this edit brings in as written in the job. */
  val gains: List<String> = emptyList(),
  /** Gained keywords the resume did not mention in any form. */
  val newSkills: List<String> = emptyList(),
  /** Numbers in [after] that [before] does not have. */
  val newNumbers: List<String> = emptyList(),
  /** Blanks such as `[X%]` for the user to fill with the real figure. */
  val blanks: List<String> = emptyList(),
) {
  val needsCheck: Boolean
    get() = newSkills.isNotEmpty() || newNumbers.isNotEmpty() || blanks.isNotEmpty()
}

/**
 * A ranked fix plan for the resume against one job, after the `ats-smart` method: exact before and
 * after text, never an invented skill or number without asking.
 */
@Serializable
data class ResumeImprovement(
  val jobId: Long,
  /** The review's keywords, to work out the match with some edits taken. */
  val keywords: List<AtsKeyword>,
  val edits: List<ResumeEdit>,
  val createdAt: Long,
) {
  val baseMatch: Int
    get() = matchPercent(keywords.map { it.match })

  /** The keyword match with [taken] edits applied: each gained keyword counts as exact. */
  fun projectedMatch(taken: Set<Int>): Int {
    val gained = edits.filter { it.id in taken }.flatMap { it.gains }.map { it.lowercase() }.toSet()
    return matchPercent(
      keywords.map { if (it.term.lowercase() in gained) KeywordMatch.EXACT else it.match }
    )
  }

  companion object {
    /** `(exact + 0.5 × partial) / total`, as a whole percentage, like the review's. */
    fun matchPercent(matches: List<KeywordMatch>): Int {
      if (matches.isEmpty()) return 0
      val credit = matches.sumOf {
        when (it) {
          KeywordMatch.EXACT -> 1.0
          KeywordMatch.PARTIAL -> 0.5
          KeywordMatch.MISSING -> 0.0
        }
      }
      return (credit * 100 / matches.size).roundToInt()
    }
  }
}

@Serializable
enum class ImprovementState {
  /** None written yet; they are written only when asked. */
  @SerialName("none") NONE,
  /** The job has no ATS review yet: the plan is built on it. */
  @SerialName("no_review") NO_REVIEW,
  /** Waiting for the AI model; an `ImprovementReady` event follows. */
  @SerialName("queued") QUEUED,
  @SerialName("ready") READY,
  /** The model gave no usable answer; asking again retries. */
  @SerialName("failed") FAILED,
}

@Serializable
data class ImprovementStatus(
  val state: ImprovementState,
  val improvement: ResumeImprovement? = null,
)

/** `POST /jobs/{id}/improve/text`: the edits to apply. */
@Serializable data class ApplyEdits(val edits: List<Int>)

/** The resume's text with the chosen edits applied, to copy or save. */
@Serializable data class ImprovedResume(val text: String)
