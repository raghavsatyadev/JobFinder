package io.github.raghavsatyadev.jobfinder.server.service.ats

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.server.ai.MergedCall
import io.github.raghavsatyadev.jobfinder.server.ai.MergedPart
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordCategory
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The model's judgement of a resume against a job; [AtsScoring] turns it into a review. */
data class AtsJudgement(
  val keywords: List<JobKeyword>,
  /** Each out of 10. */
  val experience: Double,
  val achievements: Double,
  val education: Double,
  val trajectory: Double,
  val disqualifiers: List<Disqualifier>,
  val assessment: String,
  val fixes: List<String>,
)

enum class DisqualifierKind {
  /** Fewer years than the job requires. */
  YEARS,
  /** A requirement the job calls mandatory is not in the resume. */
  MANDATORY,
  /** Dates or roles in the resume contradict each other. */
  DATES,
}

/**
 * A hard reason the resume would be screened out. [evidence] quotes the job (for [YEARS] and
 * [MANDATORY]) or the resume (for [DATES]); [Disqualifiers] drops one whose quote is not there.
 */
data class Disqualifier(val kind: DisqualifierKind, val reason: String, val evidence: String)

/** Reads a resume against a job description. Throws [LlmException] when it can't. */
fun interface AtsModel {
  suspend fun judge(resume: String, job: Job): AtsJudgement

  /** This review as one part of a [MergedCall]; `null` when it can't go in one. */
  suspend fun part(resume: String, job: Job): MergedPart<AtsJudgement>? = null
}

/**
 * [AtsModel] on the local Ollama model, after the `ats-smart` method: keywords in the job's own
 * spelling with synonyms, then a strict recruiter screen. The keyword match itself is counted in
 * code ([KeywordMatcher]), not by the model.
 */
class OllamaAtsModel(
  private val client: LlmClient,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
  /** Without it the model takes recent dates for the future and calls them errors. */
  private val today: () -> LocalDate = LocalDate::now,
  /** The instructions, which the user may have edited; "Reply with JSON only." is added. */
  private val instructions: suspend () -> String = { INSTRUCTIONS },
) : AtsModel {
  override suspend fun judge(resume: String, job: Job): AtsJudgement {
    val part = part(resume, job)
    val completion =
      client.chat(
        task = part.task,
        system = part.system,
        user = part.user,
        schemaName = part.schemaName,
        schema = part.schema,
        maxTokens = part.maxTokens,
      )
    val judgement = part.parse(completion.content)
    usage.record(completion.inputTokens, completion.outputTokens)
    return judgement
  }

  override suspend fun part(resume: String, job: Job): MergedPart<AtsJudgement> =
    MergedPart(
      name = PART_NAME,
      task = AiTask.ATS,
      system = systemPrompt(instructions()),
      user = userPrompt(resume, job, today()),
      schemaName = SCHEMA_NAME,
      schema = SCHEMA,
      maxTokens = MAX_TOKENS,
      parse = ::parse,
    )

  @Serializable
  private data class Answer(
    val keywords: List<Keyword>,
    @SerialName("experience_relevance") val experience: Double,
    @SerialName("achievement_quality") val achievements: Double,
    @SerialName("education_fit") val education: Double,
    val trajectory: Double,
    val disqualifiers: List<DisqualifierAnswer> = emptyList(),
    val assessment: String,
    val fixes: List<String> = emptyList(),
  )

  @Serializable
  private data class DisqualifierAnswer(val kind: String, val reason: String, val evidence: String)

  @Serializable
  private data class Keyword(
    val term: String,
    val category: String,
    val synonyms: List<String> = emptyList(),
  )

  companion object {
    const val PART_NAME = "ats"
    const val SCHEMA_NAME = "AtsReview"
    const val MAX_TOKENS = 4_000
    /** Enough of each text for the 16k context, with room for the answer. */
    const val MAX_RESUME_CHARS = 14_000
    const val MAX_JOB_CHARS = 14_000

    private val JSON = Json { ignoreUnknownKeys = true }

    private val CATEGORIES =
      mapOf(
        "hard_skill" to KeywordCategory.HARD_SKILL,
        "soft_skill" to KeywordCategory.SOFT_SKILL,
        "experience" to KeywordCategory.EXPERIENCE,
        "role" to KeywordCategory.ROLE,
      )

    private val KINDS =
      mapOf(
        "years" to DisqualifierKind.YEARS,
        "mandatory" to DisqualifierKind.MANDATORY,
        "dates" to DisqualifierKind.DATES,
      )

    fun parse(content: String): AtsJudgement {
      val answer =
        try {
          JSON.decodeFromString(Answer.serializer(), content)
        } catch (e: Exception) {
          throw LlmException("Unreadable AtsReview: ${content.take(200)}", e)
        }
      val keywords =
        answer.keywords.mapNotNull { keyword ->
          CATEGORIES[keyword.category.lowercase()]?.let {
            JobKeyword(keyword.term.trim(), it, keyword.synonyms.map(String::trim))
          }
        }
      if (keywords.isEmpty()) throw LlmException("AtsReview without keywords")
      return AtsJudgement(
        keywords = keywords,
        experience = answer.experience.coerceIn(0.0, 10.0),
        achievements = answer.achievements.coerceIn(0.0, 10.0),
        education = answer.education.coerceIn(0.0, 10.0),
        trajectory = answer.trajectory.coerceIn(0.0, 10.0),
        disqualifiers =
          answer.disqualifiers.mapNotNull { d ->
            KINDS[d.kind.lowercase()]?.takeIf { d.reason.isNotBlank() }
              ?.let { Disqualifier(it, d.reason.trim(), d.evidence.trim()) }
          },
        assessment = answer.assessment.trim(),
        fixes = answer.fixes.map(String::trim).filter(String::isNotEmpty).take(5),
      )
    }

    fun userPrompt(resume: String, job: Job, today: LocalDate): String =
      """TODAY: $today

JOB
Title: ${job.title}
Company: ${job.companyName}
Location: ${job.location ?: "Not specified"}
Description:
${job.description.orEmpty().take(MAX_JOB_CHARS)}

RESUME
${resume.take(MAX_RESUME_CHARS)}"""

    /** What the reviewer is told; the user can edit it. */
    val INSTRUCTIONS: String =
      """You review a resume against a job description the way an applicant tracking system and then a strict technical recruiter would. Be conservative: a real ATS gives no benefit of the doubt, and a 7 out of 10 means "likely gets a call".

1. keywords: list up to 40 keywords from the JOB DESCRIPTION only, in its exact spelling, most important first. Categories:
   - hard_skill: languages, frameworks, tools, platforms, methodologies, certifications
   - soft_skill: only soft skills stated word for word in the job description, never inferred
   - experience: years, seniority, domain, degree (as written in the job, for example "5+ years")
   - role: job titles, functions, domain language
   For each keyword give up to 3 synonyms or close equivalents (for example "k8s" for "Kubernetes", "Google Play" for "Play Store"). Do not judge whether the resume has them; that is counted separately.
2. Scores from 0 to 10, calibrated honestly, not inflated:
   - experience_relevance: how relevant each role is to this job, and the seniority and years against what the job asks
   - achievement_quality: quantified outcomes, impact verbs and scope (team size, users, scale) rather than duties
   - education_fit: education and credentials against the job's requirements (10 when the job asks for none)
   - trajectory: career progression, gaps over 6 months, short stints
3. disqualifiers: only hard ones, usually none. Each has a kind, a reason in a few words, and evidence copied word for word:
   - years: the resume shows fewer years than the job requires; evidence is the job's requirement, for example "5+ years of Android development"
   - mandatory: something the job requires is missing from the resume; evidence is the job's sentence that requires it
   - dates: dates or roles in the resume contradict each other; evidence is the resume's text
   Never a disqualifier: anything under "nice to have", "preferred" or "bonus", a change of focus, a current role of any length, or dates up to TODAY.
4. assessment: 2 to 4 direct sentences on how this resume reads for this job. No softening.
5. fixes: up to 5 changes with the most impact, each one sentence, for example weaving a missing keyword into an existing bullet or adding a real metric. Only suggest skills the resume shows evidence of; never invent experience or numbers."""

    fun systemPrompt(instructions: String): String = instructions + "\n\nReply with JSON only."

    val SYSTEM_PROMPT: String = systemPrompt(INSTRUCTIONS)

    val SCHEMA: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("keywords") {
          put("type", "array")
          putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
              putJsonObject("term") { put("type", "string") }
              putJsonObject("category") {
                put("type", "string")
                putJsonArray("enum") { CATEGORIES.keys.forEach { add(it) } }
              }
              putJsonObject("synonyms") {
                put("type", "array")
                putJsonObject("items") { put("type", "string") }
              }
            }
            put("required", required("term", "category", "synonyms"))
            put("additionalProperties", false)
          }
        }
        for (name in SCORES) putJsonObject(name) { put("type", "number") }
        putJsonObject("disqualifiers") {
          put("type", "array")
          putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
              putJsonObject("kind") {
                put("type", "string")
                putJsonArray("enum") { KINDS.keys.forEach { add(it) } }
              }
              putJsonObject("reason") { put("type", "string") }
              putJsonObject("evidence") { put("type", "string") }
            }
            put("required", required("kind", "reason", "evidence"))
            put("additionalProperties", false)
          }
        }
        putJsonObject("assessment") { put("type", "string") }
        putJsonObject("fixes") {
          put("type", "array")
          putJsonObject("items") { put("type", "string") }
        }
      }
      put(
        "required",
        required("keywords", *SCORES.toTypedArray(), "disqualifiers", "assessment", "fixes"),
      )
      put("additionalProperties", false)
    }

    private val SCORES
      get() = listOf("experience_relevance", "achievement_quality", "education_fit", "trajectory")

    private fun required(vararg names: String) = buildJsonArray {
      names.forEach { add(JsonPrimitive(it)) }
    }
  }
}
