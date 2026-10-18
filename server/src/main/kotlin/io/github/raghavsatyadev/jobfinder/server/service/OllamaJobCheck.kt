package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.server.ai.MergedPart
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The old `promptOpenAI` from `_shared/advancedMatching.ts`, on the local Ollama model: the same
 * system prompt, user prompt and `JobExclusion` schema. Throws [LlmException] on any failure (bad
 * HTTP status, unfinished answer, unreadable JSON); [AdvancedMatchingFilter] then keeps the job.
 * Token counts are recorded only after a readable answer.
 */
class OllamaJobCheck(
  private val client: LlmClient,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
  /** The instructions, which the user may have edited; the output rules are added to them. */
  private val instructions: suspend () -> String = { INSTRUCTIONS },
) : LlmJobCheck {
  override suspend fun check(prompt: String, job: Job): JobExclusion {
    val part = part(prompt, job)
    val completion =
      client.chat(
        task = part.task,
        system = part.system,
        user = part.user,
        schemaName = part.schemaName,
        schema = part.schema,
        maxTokens = part.maxTokens,
      )
    val answer = part.parse(completion.content)
    usage.record(completion.inputTokens, completion.outputTokens)
    return answer
  }

  /** This check as one part of a [MergedCall]. */
  suspend fun part(prompt: String, job: Job): MergedPart<JobExclusion> =
    MergedPart(
      name = PART_NAME,
      task = AiTask.MATCHING,
      system = systemPrompt(instructions()),
      user = userPrompt(prompt, job),
      schemaName = SCHEMA_NAME,
      schema = SCHEMA,
      maxTokens = MAX_TOKENS,
      parse = ::parse,
    )

  companion object {
    const val PART_NAME = "filter"
    const val SCHEMA_NAME = "JobExclusion"
    const val MAX_TOKENS = 3000

    /** `zodResponseFormat(z.object({ excluded: z.boolean(), reason: z.string().nullable() }))`. */
    val SCHEMA: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("excluded") { put("type", "boolean") }
        putJsonObject("reason") {
          put(
            "anyOf",
            buildJsonArray {
              add(buildJsonObject { put("type", "string") })
              add(buildJsonObject { put("type", "null") })
            },
          )
        }
      }
      put(
        "required",
        buildJsonArray {
          add(JsonPrimitive("excluded"))
          add(JsonPrimitive("reason"))
        },
      )
      put("additionalProperties", false)
    }

    /** Reads the model's JSON; `excluded` is required, `reason` may be missing or null. */
    fun parse(content: String): JobExclusion {
      val json =
        try {
          Json.parseToJsonElement(content).jsonObject
        } catch (e: Exception) {
          throw LlmException("Unreadable JobExclusion: ${content.take(200)}", e)
        }
      val excluded =
        json["excluded"]?.jsonPrimitive?.booleanOrNull
          ?: throw LlmException("JobExclusion without a boolean 'excluded': ${content.take(200)}")
      val reason = json["reason"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
      return JobExclusion(excluded, reason)
    }

    /** The old `generateUserPrompt`, word for word. */
    fun userPrompt(prompt: String, job: Job): String =
      """Here are my requirements for job filtering:
$prompt

Job Title: ${job.title}
Location: ${job.location ?: "Not specified"}
Work mode: ${job.workMode?.wire ?: "Not specified"}
Tags: ${job.tags.joinToString(", ")}
Job Description:
${job.description}

Based on my requirements, should this job be excluded from my feed?"""

    /**
     * The old `SYSTEM_PROMPT`'s rules, word for word, which the user can edit; `${' '}` keeps the
     * trailing space after "filtering jobs:".
     */
    val INSTRUCTIONS: String =
      """You are an assistant which helps users with their job search. You will have to analyze a job and decide if it should be discarded based on the user's requirements.
Following are the rules for filtering jobs:${' '}
- if the user wants to avoid certain technologies or skills, the job should only be excluded if it explicitly mentions any of them.
- if the user is requesting a minimum salary, it should be fine if the job just says: "Up to x amount" or "Depending on experience". Also the currency can be ignored.
- if the job does not mention a salary range, ignore salary requirements by the user (this rule can be overridden by the user if they want to exclude jobs without a salary range).
- only consider a job description unsuitable based on remoteness if the user explicitly restricts their interest to certain locations (e.g., "fully remote jobs in the UK") and the description specifies otherwise (e.g., "remote only in Belgium")
- treat the absence of specific details (such as PTO days or remote work specifics) neutrally unless the user specifies that such details are a deciding factor.
- job level/title: only disqualify based on job level if the description clearly conflicts with the user's specified job level.
- contract type: do not disqualify if contract type is unspecified, unless explicitly required by the user.
- location/relocation: treat location neutrally unless the user specifies no willingness to relocate or a specific geographic preference.
- benefits/company culture: absence of benefits or cultural descriptors should not disqualify a job unless specifically stated by the user as a requirement.
- technological tools: only jobs mandating undesired technologies should be disqualified, absence of mention should be neutral.
- working hours: absence of detailed working hours should not disqualify a job unless specific hours are a user requirement.
- for experience, interpret any specified maximum or minimum years of experience in relation to what is stated in the job description. If the job specifies an experience range, the job should be considered a match if the user's requirement fits within this range or if the user's requirement aligns with the maximum experience mentioned. Absence of experience details in the job description should not disqualify the job unless the user explicitly requires experience details to be mentioned.

Really important, if there are any ambiguities between the user's requirements and the job, never exclude it."""

    /** The answer's format, which stays fixed whatever the instructions say. */
    val OUTPUT_RULES: String =
      """Reply with a JSON object containing the following fields:
- excluded: boolean (true if the job should be excluded, false otherwise)
- reason: string (the reason why the job should be excluded; leave this field empty if the job should not be excluded)
- keep the reason as short as possible, maximum 20 words.
"""

    /** [instructions] with the fixed output rules; the defaults give the old prompt exactly. */
    fun systemPrompt(instructions: String): String = instructions + "\n\n" + OUTPUT_RULES

    val SYSTEM_PROMPT: String = systemPrompt(INSTRUCTIONS)
  }
}
