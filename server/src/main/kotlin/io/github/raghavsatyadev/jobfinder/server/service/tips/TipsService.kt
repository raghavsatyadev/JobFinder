package io.github.raghavsatyadev.jobfinder.server.service.tips

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.NegotiationTipsEntity
import io.github.raghavsatyadev.jobfinder.server.db.TipsDao
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.CareersResult
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.GhostCheck
import io.github.raghavsatyadev.jobfinder.shared.model.GhostSignal
import io.github.raghavsatyadev.jobfinder.shared.model.Leverage
import io.github.raghavsatyadev.jobfinder.shared.model.NegotiationTips
import io.github.raghavsatyadev.jobfinder.shared.model.TipsState
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
import org.slf4j.LoggerFactory

/** What the tips are written from, besides the job itself; each part may be missing. */
data class TipsFacts(
  val review: AtsReview?,
  val insights: CompanyInsights?,
  val ghost: GhostCheck?,
)

/**
 * Negotiation tips for one job, written by the AI only when the user asks, from the posted salary,
 * the company's ratings and salary range, how long and how often the job was advertised, and the
 * ATS review. Kept until asked again.
 */
class TipsService(
  private val dao: TipsDao,
  private val jobs: JobsDao,
  private val client: LlmClient,
  private val facts: suspend (Long) -> TipsFacts,
  private val events: ServerEvents,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
  private val instructions: suspend () -> String = { INSTRUCTIONS },
  private val clock: () -> Long = System::currentTimeMillis,
) : TipsRepository {
  private val queue = Channel<Long>(Channel.UNLIMITED)
  private val queued = ConcurrentHashMap.newKeySet<Long>()
  private val json = Json { ignoreUnknownKeys = true }

  fun start(scope: CoroutineScope) {
    scope.launch {
      for (jobId in queue) {
        try {
          write(jobId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Negotiation tips for job {} failed: {}", jobId, e.message)
          dao.upsert(NegotiationTipsEntity(jobId, failed = true, createdAt = clock()))
        } finally {
          queued.remove(jobId)
        }
        events.emit(ServerEvent.TipsReady(jobId))
      }
    }
  }

  override suspend fun requestTips(jobId: Long, write: Boolean): TipsStatus {
    jobs.get(jobId) ?: notFound("Job")
    val stored = dao.get(jobId)
    val tips = stored?.tips?.let { ApiJson.decodeFromString(NegotiationTips.serializer(), it) }
    return when {
      jobId in queued -> TipsStatus(TipsState.QUEUED, tips)
      write -> {
        if (queued.add(jobId)) queue.trySend(jobId)
        TipsStatus(TipsState.QUEUED, tips)
      }
      stored == null -> TipsStatus(TipsState.NONE)
      stored.failed -> TipsStatus(TipsState.FAILED)
      else -> TipsStatus(TipsState.READY, tips)
    }
  }

  private suspend fun write(jobId: Long) {
    val job = jobs.get(jobId) ?: return
    val completion =
      client.chat(
        task = AiTask.TIPS,
        system = instructions() + "\n\n" + OUTPUT_RULES,
        user = userPrompt(job, facts(jobId), clock()),
        schemaName = SCHEMA_NAME,
        schema = SCHEMA,
        maxTokens = MAX_TOKENS,
      )
    val answer =
      try {
        json.decodeFromString(Answer.serializer(), completion.content)
      } catch (e: Exception) {
        throw LlmException("Unreadable NegotiationTips: ${completion.content.take(200)}", e)
      }
    usage.record(completion.inputTokens, completion.outputTokens)
    val tips =
      NegotiationTips(
        leverage =
          Leverage.entries.firstOrNull { it.name.equals(answer.leverage, ignoreCase = true) }
            ?: Leverage.MEDIUM,
        summary = answer.summary.trim(),
        tips = answer.tips.map(String::trim).filter(String::isNotEmpty).take(MAX_TIPS),
        createdAt = clock(),
      )
    if (tips.tips.isEmpty()) throw LlmException("NegotiationTips without tips")
    dao.upsert(
      NegotiationTipsEntity(
        jobId,
        ApiJson.encodeToString(NegotiationTips.serializer(), tips),
        createdAt = tips.createdAt,
      )
    )
  }

  @Serializable
  private data class Answer(val leverage: String, val summary: String, val tips: List<String>)

  companion object {
    private const val SCHEMA_NAME = "NegotiationTips"
    private const val MAX_TOKENS = 1_500
    private const val MAX_TIPS = 6
    private const val MAX_DESCRIPTION = 6_000
    private const val DAY_MS = 86_400_000L
    private val log = LoggerFactory.getLogger("jobfinder.tips")

    /** What the AI is told; the user can edit it. */
    val INSTRUCTIONS: String =
      """You advise a job seeker on negotiating the offer for one job. Use only the facts given: the job and its posted salary, the company's employee ratings and salary range for the role, how long and how often the job has been advertised, and how well the resume matches the job (ATS review).
- leverage: the candidate's room to negotiate. High when the job has been open long or reposted, the resume matches strongly, or market pay is above the posted salary; low when the match is weak or the role is easy to fill.
- summary: one or two sentences on the candidate's position.
- tips: 3 to 6 concrete tips, one sentence each: a target from the salary data, what to ask for besides base pay, which strengths from the review to stress, what to ask about when ratings point to a problem. Never invent numbers that are not in the facts; without salary data, say how to find it."""

    private val OUTPUT_RULES: String =
      """Reply with JSON only: "leverage" (low, medium or high), "summary" and "tips" (a list of strings)."""

    private val SCHEMA: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("leverage") {
          put("type", "string")
          putJsonArray("enum") {
            add("low")
            add("medium")
            add("high")
          }
        }
        putJsonObject("summary") { put("type", "string") }
        putJsonObject("tips") {
          put("type", "array")
          putJsonObject("items") { put("type", "string") }
        }
      }
      put(
        "required",
        buildJsonArray {
          add(JsonPrimitive("leverage"))
          add(JsonPrimitive("summary"))
          add(JsonPrimitive("tips"))
        },
      )
      put("additionalProperties", false)
    }

    fun userPrompt(job: JobEntity, facts: TipsFacts, now: Long): String = buildString {
      appendLine("JOB")
      appendLine("Title: ${job.title}")
      appendLine("Company: ${job.companyName}")
      appendLine("Location: ${job.location ?: "Not specified"}")
      appendLine("Posted salary: ${job.salary?.takeIf { it.isNotBlank() } ?: "Not given"}")
      appendLine("First seen: ${(now - job.createdAt) / DAY_MS} days ago")
      appendLine()
      facts.insights?.let { insights ->
        appendLine("COMPANY")
        insights.ratings.forEach {
          append("${it.source.name.lowercase()} rating ${it.rating}/5")
          it.reviews?.let { n -> append(" from $n reviews") }
          appendLine()
          if (it.dislikes.isNotEmpty())
            appendLine("Employees dislike: ${it.dislikes.take(3).joinToString("; ")}")
        }
        insights.salary?.let {
          appendLine(
            "Salary for ${it.role}${if (it.exactRole) "" else " (nearest role)"}: " +
              "${it.low} to ${it.high} ${it.currency} a year, median ${it.median}"
          )
        } ?: appendLine("Company salary data: none found")
        appendLine()
      }
      facts.ghost?.let { ghost ->
        appendLine("POSTING HISTORY")
        ghost.signals.forEach {
          when (it) {
            is GhostSignal.Reposted -> appendLine("Posted ${it.times} times")
            is GhostSignal.OldPosting -> appendLine("Advertised for ${it.days} days")
            else -> Unit
          }
        }
        ghost.careers?.let {
          appendLine(
            when (it.result) {
              CareersResult.FOUND -> "Listed on the company's own careers site"
              CareersResult.NOT_FOUND -> "Not on the company's own careers site"
              CareersResult.UNKNOWN -> "Company careers site unknown"
            }
          )
        }
        appendLine()
      }
      facts.review?.let { review ->
        appendLine("ATS REVIEW")
        appendLine(
          "Overall ${review.summary.overall}/10, keyword match ${review.summary.atsMatch}%"
        )
        if (review.disqualifiers.isNotEmpty()) {
          appendLine("Gaps: ${review.disqualifiers.joinToString("; ")}")
        }
        appendLine("Assessment: ${review.assessment}")
        appendLine()
      }
      appendLine("DESCRIPTION")
      append(job.description.orEmpty().take(MAX_DESCRIPTION))
    }
  }
}
