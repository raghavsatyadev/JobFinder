package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.db.AdvancedMatchingDao
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.AdvancedMatchingFilter
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.OllamaJobCheck
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobReportResult
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.JobReportRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * "Shouldn't be in New": asks the AI, through the matching routes, whether a job the user reported
 * should have been filtered out by their wishes and their note. When it should, the job moves to
 * Filtered out with the AI's reason.
 */
class JobReports(
  private val jobs: JobsDao,
  private val matching: AdvancedMatchingDao,
  private val filter: AdvancedMatchingFilter,
  private val client: LlmClient,
  private val events: ServerEvents,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
  private val instructions: suspend () -> String = { OllamaJobCheck.INSTRUCTIONS },
  private val clock: () -> Long = System::currentTimeMillis,
) : JobReportRepository {
  private val log = LoggerFactory.getLogger(JobReports::class.java)

  override suspend fun reportJob(jobId: Long, note: String?): JobReportResult {
    val entity =
      jobs.get(jobId) ?: throw ApiException(ApiErrorCode.NOT_FOUND, "The job was not found.")
    if (entity.status != JobStatus.NEW.wire) {
      throw ApiException(ApiErrorCode.BAD_REQUEST, "Only jobs in New can be reported.")
    }
    val job = entity.toModel()
    val result =
      if (job.description.isNullOrBlank()) {
        JobReportResult(userRight = true, reason = AdvancedMatchingFilter.NO_DESCRIPTION)
      } else {
        ask(job, note?.trim()?.take(MAX_NOTE)?.ifBlank { null })
      }
    if (!result.userRight) return result
    val moved =
      jobs.updateMatch(
        jobId,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING.wire,
        result.reason ?: REPORTED,
        filter.key(),
        clock(),
      ) > 0
    if (moved) {
      log.info("Reported job {} moved to filtered out", jobId)
      events.emit(ServerEvent.NewJobs(emptyList()))
    }
    return result.copy(moved = moved)
  }

  private suspend fun ask(job: Job, note: String?): JobReportResult {
    val wishes = matching.get()?.prompt.orEmpty()
    val user =
      OllamaJobCheck.userPrompt(wishes.ifBlank { "(none written)" }, job) +
        "\n\nI think this job should have been excluded." +
        (note?.let { "\nMy reason: $it" } ?: "")
    val completion =
      try {
        client.chat(
          task = AiTask.MATCHING,
          system = instructions() + "\n\n" + RULES,
          user = user,
          schemaName = SCHEMA_NAME,
          schema = SCHEMA,
          maxTokens = OllamaJobCheck.MAX_TOKENS,
        )
      } catch (e: LlmException) {
        throw ApiException(ApiErrorCode.UNAVAILABLE, e.message ?: "The AI gave no usable answer.")
      }
    val answer = parse(completion.content)
    usage.record(completion.inputTokens, completion.outputTokens)
    return answer
  }

  private companion object {
    const val MAX_NOTE = 1000
    const val REPORTED = "You reported it."
    const val SCHEMA_NAME = "JobReport"

    /** The answer's format; the user's reason counts as one of their requirements. */
    val RULES =
      """The user says this job got through their filter wrongly. Decide whether they are right: judge the job by their requirements and by their reason, if they gave one; their reason counts as a requirement.
Reply with a JSON object containing the following fields:
- userRight: boolean (true if the job should be excluded, false if it fits the requirements)
- reason: string (why it should be excluded, or why it fits; maximum 25 words)
"""

    val SCHEMA = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("userRight") { put("type", "boolean") }
        putJsonObject("reason") { put("type", "string") }
      }
      put(
        "required",
        buildJsonArray {
          add(JsonPrimitive("userRight"))
          add(JsonPrimitive("reason"))
        },
      )
      put("additionalProperties", false)
    }

    fun parse(content: String): JobReportResult {
      val json =
        try {
          Json.parseToJsonElement(content).jsonObject
        } catch (e: Exception) {
          throw ApiException(ApiErrorCode.UNAVAILABLE, "The AI gave no usable answer.")
        }
      val right =
        json["userRight"]?.jsonPrimitive?.booleanOrNull
          ?: throw ApiException(ApiErrorCode.UNAVAILABLE, "The AI gave no usable answer.")
      val reason = json["reason"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
      return JobReportResult(right, reason)
    }
  }
}
