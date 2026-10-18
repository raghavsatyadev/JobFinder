package io.github.raghavsatyadev.jobfinder.server.service.ghost

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/**
 * Finds the board's job among a careers site's jobs: by title words first, and only when the best
 * titles are close but not the same does it ask the AI. Without a model, or when it fails, a close
 * title counts as found, so a job is never called a ghost on a guess.
 */
class CareersMatcher(
  private val client: LlmClient?,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
  private val instructions: suspend () -> String = { INSTRUCTIONS },
) {
  private val log = LoggerFactory.getLogger(CareersMatcher::class.java)

  suspend fun match(title: String, location: String?, jobs: List<CareersJob>): CareersJob? {
    val scored = jobs.map { it to similarity(title, it.title) }.sortedByDescending { it.second }
    val best = scored.firstOrNull() ?: return null
    if (best.second >= SAME) return best.first
    val close = scored.takeWhile { it.second >= CLOSE }.take(MAX_ASKED).map { it.first }
    if (close.isEmpty()) return null
    val client = client ?: return close.first()
    return try {
      ask(client, title, location, close)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.info("Careers match for '{}' fell back to the closest title: {}", title, e.message)
      close.first()
    }
  }

  private suspend fun ask(
    client: LlmClient,
    title: String,
    location: String?,
    candidates: List<CareersJob>,
  ): CareersJob? {
    val user = buildString {
      appendLine("BOARD JOB")
      appendLine("Title: $title")
      appendLine("Location: ${location ?: "Not specified"}")
      appendLine()
      appendLine("CAREERS SITE JOBS")
      candidates.forEachIndexed { i, job ->
        appendLine("${i + 1}. ${job.title} (${job.location ?: "location not given"})")
      }
    }
    val completion =
      client.chat(
        task = AiTask.GHOST,
        system = instructions() + "\n\n" + OUTPUT_RULES,
        user = user,
        schemaName = SCHEMA_NAME,
        schema = SCHEMA,
        maxTokens = MAX_TOKENS,
      )
    val answer =
      try {
        Json.parseToJsonElement(completion.content).jsonObject
      } catch (e: Exception) {
        throw LlmException("Unreadable CareersMatch: ${completion.content.take(200)}", e)
      }
    usage.record(completion.inputTokens, completion.outputTokens)
    val same = answer["same"]?.jsonPrimitive?.booleanOrNull ?: return null
    if (!same) return null
    val index = answer["number"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull ?: 1
    return candidates.getOrNull(index - 1) ?: candidates.first()
  }

  companion object {
    /** Titles this alike are the same job. */
    const val SAME = 0.75

    /** Titles this alike may be; the AI decides. */
    const val CLOSE = 0.45
    private const val MAX_ASKED = 5
    private const val MAX_TOKENS = 300
    private const val SCHEMA_NAME = "CareersMatch"

    private val FILLER =
      setOf(
        "the",
        "and",
        "of",
        "for",
        "in",
        "at",
        "a",
        "an",
        "to",
        "with",
        "remote",
        "hybrid",
        "m",
        "f",
        "d",
      )

    /** Dice similarity of the titles' words, ignoring case, punctuation and filler words. */
    fun similarity(a: String, b: String): Double {
      val x = words(a)
      val y = words(b)
      if (x.isEmpty() || y.isEmpty()) return 0.0
      return 2.0 * x.intersect(y).size / (x.size + y.size)
    }

    private fun words(title: String): Set<String> =
      title
        .lowercase()
        .replace("sr.", "senior")
        .replace("jr.", "junior")
        .split(Regex("[^a-z0-9+#]+"))
        .filter { it.isNotEmpty() && it !in FILLER }
        .toSet()

    /** What the AI is told; the user can edit it. */
    val INSTRUCTIONS: String =
      """You check whether a job posted on a job board is also listed on the company's own careers site. You get the board job and a numbered list of careers-site jobs with similar titles.
Decide whether one of them is the same opening: the same role and seniority, allowing for different wording, abbreviations and title order, and a location that fits (a city in the same country or "remote" fits; another country does not).
Answer same as true only when you are confident; when in doubt answer false."""

    private val OUTPUT_RULES: String =
      """Reply with JSON only: "same" (true or false) and "number", the matching job's number, or null."""

    private val SCHEMA: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("same") { put("type", "boolean") }
        putJsonObject("number") {
          put(
            "anyOf",
            buildJsonArray {
              add(buildJsonObject { put("type", "integer") })
              add(buildJsonObject { put("type", "null") })
            },
          )
        }
      }
      put(
        "required",
        buildJsonArray {
          add(JsonPrimitive("same"))
          add(JsonPrimitive("number"))
        },
      )
      put("additionalProperties", false)
    }
  }
}
