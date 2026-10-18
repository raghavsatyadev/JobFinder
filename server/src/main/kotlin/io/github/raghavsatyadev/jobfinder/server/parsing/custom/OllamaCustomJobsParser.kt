package io.github.raghavsatyadev.jobfinder.server.parsing.custom

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.ChatCompletion
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.db.AiUsageDao
import io.github.raghavsatyadev.jobfinder.server.parsing.CustomJobsParser
import io.github.raghavsatyadev.jobfinder.server.parsing.JobDescriptionUpdates
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.LlmUsage
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * The custom-board parser: asks the local Ollama model to read jobs out of an arbitrary page (the
 * old `parseCustomJobs` and `parseCustomJobDescription`). Never throws: when Ollama is down or the
 * answer is unusable, the list result is a failure ([JobListParseResult.parseFailed]) and the
 * description result is [JobDescriptionUpdates.NONE].
 */
class OllamaCustomJobsParser(
  private val client: LlmClient,
  private val usageRecorder: AiUsageRecorder = AiUsageRecorder.NONE,
  /** The user's advanced-matching prompt, shown to the model when it summarizes a job page. */
  private val preferences: suspend () -> String? = { null },
  /** The instructions for a search page and for a job page; the user may have edited them. */
  private val listInstructions: suspend () -> String = { CustomPrompts.LIST_SYSTEM_PROMPT },
  private val descriptionInstructions: suspend () -> String = {
    CustomPrompts.DESCRIPTION_SYSTEM_PROMPT
  },
) : CustomJobsParser {
  private val log = LoggerFactory.getLogger(OllamaCustomJobsParser::class.java)

  override suspend fun parseList(input: ListParseInput): JobListParseResult {
    val completion =
      try {
        val header = CustomPageCleaner.headerInfo(input.html)
        client.chat(
          task = AiTask.PARSING,
          system = listInstructions(),
          user =
            CustomPrompts.listUserPrompt(input.url, header, CustomPageCleaner.listHtml(input.html)),
          schemaName = "ParseJobsPageResponse",
          schema = CustomPrompts.LIST_SCHEMA,
          maxTokens = LIST_MAX_TOKENS,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.warn("Custom list parse failed for site {}: {}", input.siteId, e.message)
        return failure("LLM request failed: ${e.message}")
      }

    val parsed =
      try {
        JSON.decodeFromString(ListResponse.serializer(), stripFences(completion.content))
      } catch (e: Exception) {
        log.warn("Custom list parse got malformed JSON for site {}: {}", input.siteId, e.message)
        return failure("LLM returned malformed JSON: ${e.message}")
      }

    recordUsage(completion)

    val listFound = parsed.errorMessage.isNullOrBlank()
    if (!listFound)
      log.error("Site {} - LLM reported an error: {}", input.siteId, parsed.errorMessage)

    val jobs =
      parsed.jobs
        .filter { it.externalUrl.startsWith("https://") && it.title.isNotBlank() }
        .filter { it.companyName.isNotBlank() }
        .map { job ->
          ParsedJob(
            siteId = input.siteId,
            // hash the url to create a stable externalId
            externalId = sha256Hex(job.externalUrl),
            externalUrl = job.externalUrl,
            title = job.title,
            companyName = job.companyName,
            companyLogo = job.companyLogo?.ifBlank { null },
            location = job.location?.ifBlank { null },
            salary = job.salary?.ifBlank { null },
            workMode = WorkMode.fromWireOrNull(job.jobType),
            tags = job.tags.orEmpty(),
            description = job.description?.ifBlank { null },
          )
        }
    return JobListParseResult(
      jobs = jobs,
      listFound = listFound,
      elementsCount = jobs.size,
      llmUsage = LlmUsage(completion.inputTokens, completion.outputTokens),
    )
  }

  override suspend fun parseDescription(html: String): JobDescriptionUpdates {
    val completion =
      try {
        client.chat(
          task = AiTask.PARSING,
          system = descriptionInstructions(),
          user =
            CustomPrompts.descriptionUserPrompt(
              CustomPageCleaner.descriptionMarkdown(html),
              preferences(),
            ),
          schemaName = "ParseJobDescriptionResponse",
          schema = CustomPrompts.DESCRIPTION_SCHEMA,
          maxTokens = DESCRIPTION_MAX_TOKENS,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.warn("Custom description parse failed: {}", e.message)
        return JobDescriptionUpdates.NONE
      }
    val parsed =
      try {
        JSON.decodeFromString(DescriptionResponse.serializer(), stripFences(completion.content))
      } catch (e: Exception) {
        log.warn("Custom description parse got malformed JSON: {}", e.message)
        return JobDescriptionUpdates.NONE
      }
    recordUsage(completion)

    // The old code kept the original description and added the AI text around it; the dispatcher
    // has no stored job here, so only the AI parts are returned.
    if (!parsed.errorMessage.isNullOrBlank()) {
      log.warn("LLM could not read the job description: {}", parsed.errorMessage)
      return JobDescriptionUpdates.NONE
    }
    val description =
      """
      ## AI Generated Summary
      ${parsed.summary?.trim() ?: "No summary extracted."}

      ## AI Extracted Job Description
      ${parsed.description?.trim() ?: "No description extracted."}
      """
        .trimIndent()
    return JobDescriptionUpdates(
      description = description,
      salary = parsed.salary?.trim()?.ifEmpty { null },
      tags = parsed.tags?.map { it.trim() },
    )
  }

  private suspend fun recordUsage(completion: ChatCompletion) {
    try {
      usageRecorder.record(completion.inputTokens, completion.outputTokens)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.warn("Could not record AI usage: {}", e.message)
    }
  }

  private fun failure(message: String) = JobListParseResult.NOT_FOUND.copy(error = message)

  @Serializable
  private data class ListJob(
    val externalUrl: String = "",
    val title: String = "",
    val companyName: String = "",
    val companyLogo: String? = null,
    val jobType: String? = null,
    val location: String? = null,
    val salary: String? = null,
    val tags: List<String>? = null,
    val description: String? = null,
  )

  @Serializable
  private data class ListResponse(val jobs: List<ListJob>, val errorMessage: String? = null)

  @Serializable
  private data class DescriptionResponse(
    val description: String? = null,
    val summary: String? = null,
    val salary: String? = null,
    val tags: List<String>? = null,
    val errorMessage: String? = null,
  )

  companion object {
    /** Old code asked for 50k; the local model's reply is far smaller than its context. */
    const val LIST_MAX_TOKENS = 16_000
    const val DESCRIPTION_MAX_TOKENS = 10_000

    private val JSON = Json {
      ignoreUnknownKeys = true
      coerceInputValues = true
    }

    private fun stripFences(text: String): String =
      text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

    internal fun sha256Hex(input: String): String =
      MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") {
        "%02x".format(it)
      }
  }
}

/** Writes token counts into `ai_usage_daily` (UTC day). */
class RoomAiUsageRecorder(
  private val dao: AiUsageDao,
  private val now: () -> Long = System::currentTimeMillis,
) : AiUsageRecorder {
  override suspend fun record(inputTokens: Long, outputTokens: Long) {
    val nowMs = now()
    val date = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
    dao.add(date, inputTokens, outputTokens, nowMs)
  }
}
