package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * One task's work for a job, as it would be asked alone: its own (editable) instructions, input and
 * answer schema, and how to read the answer ([parse] throws [LlmException] on a bad one).
 */
class MergedPart<T>(
  /** The answer's field in a merged call. */
  val name: String,
  val task: AiTask,
  val system: String,
  val user: String,
  val schemaName: String,
  val schema: JsonObject,
  val maxTokens: Int,
  val parse: (String) -> T,
)

/** A merged call's answer; a part the model missed or got wrong reads as `null`. */
class MergedAnswer(
  private val parts: Map<String, String>,
  val inputTokens: Long,
  val outputTokens: Long,
) {
  operator fun <T> get(part: MergedPart<T>): T? =
    parts[part.name]?.let {
      try {
        part.parse(it)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        null
      }
    }
}

/**
 * Several tasks for one job in one request to an online model: each part's instructions stacked
 * under its own heading, and one JSON object with a field per part. Only for parts whose first
 * model is the same online model ([LlmClient.sharedTarget]); never for the model on this computer,
 * which reads one long prompt slower than two short ones. A missing part is done alone by its
 * caller.
 */
object MergedCall {
  /** One request on [target]; throws [LlmException] when it fails as a whole. */
  suspend fun run(client: LlmClient, target: LlmTarget, parts: List<MergedPart<*>>): MergedAnswer {
    require(parts.map { it.name }.distinct().size == parts.size) { "Part names must differ" }
    val completion =
      client.chatOn(
        target = target,
        system = system(parts),
        user = user(parts),
        schemaName = SCHEMA_NAME,
        schema = schema(parts),
        maxTokens = parts.maxOf { it.maxTokens } + EXTRA_TOKENS,
      )
    val answer =
      try {
        Json.parseToJsonElement(completion.content).jsonObject
      } catch (e: Exception) {
        throw LlmException("Unreadable merged answer: ${completion.content.take(200)}", e)
      }
    val found =
      parts
        .mapNotNull { part ->
          answer[part.name]?.takeIf { it !is JsonNull }?.let { part.name to it.toString() }
        }
        .toMap()
    return MergedAnswer(found, completion.inputTokens, completion.outputTokens)
  }

  fun system(parts: List<MergedPart<*>>): String = buildString {
    append("You do ${parts.size} separate tasks for the same job. Do each one on its own, ")
    append("exactly as its instructions say, as if it were the only task. Reply with one JSON ")
    append("object with one field per task: ")
    append(parts.joinToString(", ") { "\"${it.name}\"" })
    append(", each holding that task's own JSON answer.")
    for (part in parts) {
      append("\n\n## Task \"${part.name}\"\n\n")
      append(part.system)
    }
  }

  fun user(parts: List<MergedPart<*>>): String =
    parts.joinToString("\n\n") { "## Input for task \"${it.name}\"\n\n${it.user}" }

  fun schema(parts: List<MergedPart<*>>): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") { parts.forEach { put(it.name, it.schema) } }
    put("required", buildJsonArray { parts.forEach { add(it.name) } })
    put("additionalProperties", false)
  }

  private const val SCHEMA_NAME = "JobTasks"

  /** Room for the smaller parts' answers next to the largest one's budget. */
  private const val EXTRA_TOKENS = 500
}
