package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A formatting problem in the resume that applicant tracking systems or recruiters trip on. */
@Serializable
enum class ResumeFlag {
  @SerialName("too_short") TOO_SHORT,
  @SerialName("too_long") TOO_LONG,
  @SerialName("no_email") NO_EMAIL,
  @SerialName("no_phone") NO_PHONE,
  @SerialName("no_experience_section") NO_EXPERIENCE_SECTION,
  @SerialName("no_education_section") NO_EDUCATION_SECTION,
  @SerialName("no_skills_section") NO_SKILLS_SECTION,
  @SerialName("no_dates") NO_DATES,
  /** Icon fonts or glyphs that come out of text extraction as junk. */
  @SerialName("odd_characters") ODD_CHARACTERS,
  @SerialName("tables") TABLES,
  @SerialName("images") IMAGES,
  @SerialName("multi_column") MULTI_COLUMN,
}

/** The resume the ATS scores use. Only its text is kept, on the computer. */
@Serializable
data class ResumeInfo(
  val fileName: String,
  val uploadedAt: Long,
  /** `null` for formats without pages (DOCX, text). */
  val pages: Int? = null,
  val words: Int,
  val flags: List<ResumeFlag> = emptyList(),
)

@Serializable
enum class KeywordCategory {
  @SerialName("hard_skill") HARD_SKILL,
  @SerialName("soft_skill") SOFT_SKILL,
  @SerialName("experience") EXPERIENCE,
  @SerialName("role") ROLE,
}

/** How a job keyword shows up in the resume: exact 1.0, partial (a synonym) 0.5, missing 0. */
@Serializable
enum class KeywordMatch {
  @SerialName("exact") EXACT,
  @SerialName("partial") PARTIAL,
  @SerialName("missing") MISSING,
}

/** A keyword from the job description and how the resume covers it. */
@Serializable
data class AtsKeyword(
  val term: String,
  val category: KeywordCategory,
  val match: KeywordMatch,
  /** The synonym found, for a partial match. */
  val matchedAs: String? = null,
  /** How often the resume uses it; critical terms should appear 2 to 4 times. */
  val count: Int = 0,
)

/** What a job card shows: the overall score out of 10, the keyword match and the verdict. */
@Serializable
data class AtsSummary(val overall: Double, val atsMatch: Int, val shortlisted: Boolean)

/** The parts of the overall score, each out of 10. */
@Serializable
data class AtsScores(
  val keywords: Double,
  val experience: Double,
  val achievements: Double,
  val formatting: Double,
  val education: Double,
  val trajectory: Double,
)

/** The full ATS review of the resume against one job. */
@Serializable
data class AtsReview(
  val jobId: Long,
  val summary: AtsSummary,
  val scores: AtsScores,
  val keywords: List<AtsKeyword>,
  val disqualifiers: List<String> = emptyList(),
  val assessment: String,
  val fixes: List<String> = emptyList(),
  val formatting: List<ResumeFlag> = emptyList(),
  val createdAt: Long,
)

@Serializable
enum class AtsState {
  /** No resume uploaded yet. */
  @SerialName("no_resume") NO_RESUME,
  /** The job has no description to compare with. */
  @SerialName("no_description") NO_DESCRIPTION,
  /** Waiting for the AI model, or being scored now. */
  @SerialName("queued") QUEUED,
  @SerialName("ready") READY,
  /** The model gave no usable answer; asking again retries. */
  @SerialName("failed") FAILED,
}

/** Where one job's ATS review stands; [review] is set when [state] is `READY`. */
@Serializable data class AtsStatus(val state: AtsState, val review: AtsReview? = null)
