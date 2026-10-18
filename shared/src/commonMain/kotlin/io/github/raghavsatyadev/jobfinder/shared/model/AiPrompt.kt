package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The instructions the app gives the AI, each editable. Only the instructions are: the job, page or
 * resume the app adds, the JSON schema and the output rules stay fixed around the user's text.
 */
@Serializable
enum class PromptId(
  /** The id in the database and the API path. */
  val wire: String,
  /** Words the instructions must keep, or the answer loses a field the app reads. */
  val requiredTerms: List<String>,
  /** Reads job boards: a bad edit can stop jobs from coming in. */
  val drivesParsing: Boolean,
) {
  /** Decides which jobs AI filters move to Filtered out. */
  @SerialName("matching") MATCHING("matching", emptyList(), drivesParsing = false),
  /** Reads the jobs from a search page of a site without a built-in reader. */
  @SerialName("parse_list")
  PARSE_LIST("parse_list", listOf("externalUrl", "title", "companyName"), drivesParsing = true),
  /** Reads a job's description from its page on such a site. */
  @SerialName("parse_description")
  PARSE_DESCRIPTION("parse_description", listOf("description", "summary"), drivesParsing = true),
  /** Scores and reviews the resume against a job. */
  @SerialName("ats_review")
  ATS_REVIEW(
    "ats_review",
    listOf(
      "keywords",
      "experience_relevance",
      "achievement_quality",
      "education_fit",
      "trajectory",
      "disqualifiers",
      "assessment",
      "fixes",
    ),
    drivesParsing = false,
  ),
  /** Decides whether a careers-site job is the board's job, when the titles differ. */
  @SerialName("ghost_match") GHOST_MATCH("ghost_match", listOf("same"), drivesParsing = false),
  /** Writes negotiation tips for one job. */
  @SerialName("negotiation_tips")
  NEGOTIATION_TIPS(
    "negotiation_tips",
    listOf("leverage", "summary", "tips"),
    drivesParsing = false,
  ),
  /** Writes exact before and after edits that fit the resume to one job. */
  @SerialName("resume_improve")
  RESUME_IMPROVE("resume_improve", listOf("before", "after"), drivesParsing = false);

  companion object {
    fun fromWire(wire: String): PromptId? = entries.firstOrNull { it.wire == wire }
  }
}

/** One prompt: what the AI is told now, and the app's own text. */
@Serializable
data class AiPrompt(val id: PromptId, val text: String, val defaultText: String) {
  val edited: Boolean
    get() = text != defaultText

  companion object {
    const val MAX_LENGTH = 20_000
  }
}

/** `PUT /prompts/{id}`: new instructions, or `null` for the app's own text. */
@Serializable data class PromptEdit(val text: String?)

/** Something about an edit that may break what the prompt drives. */
sealed interface PromptWarning {
  /** No instructions at all. */
  data object Empty : PromptWarning

  /** Words the answer's fields depend on are gone. */
  data class MissingTerms(val terms: List<String>) : PromptWarning

  /** The text talks about the answer's format, which the app fixes; it may confuse the model. */
  data object MentionsFormat : PromptWarning

  /** The prompt reads job boards. */
  data object DrivesParsing : PromptWarning

  /** Matching or ATS wording changed: results will differ from before. */
  data object ResultsChange : PromptWarning
}

object PromptChecks {
  private val FORMAT_WORDS = Regex("""\b(json|schema|xml|yaml|markdown table|csv)\b""", RIGNORE)

  /**
   * The warnings for saving [text] as [id]'s instructions; strong ones ([isStrong]) need a second
   * confirmation. [defaultText] is the app's text, so a format word it already has is not flagged.
   */
  fun check(id: PromptId, text: String, defaultText: String): List<PromptWarning> = buildList {
    if (text.isBlank()) {
      add(PromptWarning.Empty)
    } else {
      val missing = id.requiredTerms.filter { !text.contains(it, ignoreCase = true) }
      if (missing.isNotEmpty()) add(PromptWarning.MissingTerms(missing))
      val mentions = FORMAT_WORDS.findAll(text).count()
      if (mentions > FORMAT_WORDS.findAll(defaultText).count()) add(PromptWarning.MentionsFormat)
    }
    if (id.drivesParsing) add(PromptWarning.DrivesParsing) else add(PromptWarning.ResultsChange)
  }

  fun isStrong(warning: PromptWarning): Boolean =
    warning !is PromptWarning.ResultsChange && warning !is PromptWarning.MentionsFormat
}

private val RIGNORE = RegexOption.IGNORE_CASE
