package io.github.raghavsatyadev.jobfinder.server.service.translate

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.JobLanguages
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** A job's title and description in another language, from [JobTranslator]. */
data class TranslatedJob(val title: String, val description: String?)

/** Turns a job's text into another language. */
fun interface JobTranslator {
  /** Throws when any part could not be translated: a part-translated job is never kept. */
  suspend fun translate(
    title: String,
    description: String?,
    from: String,
    to: String,
  ): TranslatedJob
}

/**
 * [JobTranslator] on the app's AI ([AiTask.TRANSLATE]). A translation is as long as its text, so
 * the description goes in parts of about [PART_CHARS], split between paragraphs; the first part
 * carries the title too. The Markdown is kept.
 */
class AiJobTranslator(
  private val client: LlmClient,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
) : JobTranslator {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun translate(
    title: String,
    description: String?,
    from: String,
    to: String,
  ): TranslatedJob {
    val system = instructions(JobLanguages.englishName(from), JobLanguages.englishName(to))
    val parts = parts(description.orEmpty())
    val first = parts.firstOrNull().orEmpty()
    val head = ask(system, "TITLE:\n$title\n\nTEXT:\n$first", withTitle = true, first.length)
    val translatedTitle =
      head.title?.trim()?.takeIf(String::isNotEmpty) ?: throw LlmException("No translated title")
    if (parts.isEmpty()) return TranslatedJob(translatedTitle, null)
    val texts = mutableListOf(head.text)
    for (part in parts.drop(1)) texts +=
      ask(system, "TEXT:\n$part", withTitle = false, part.length).text
    val body = texts.joinToString("\n\n") { it.trim() }
    if (body.isBlank()) throw LlmException("Empty translated description")
    return TranslatedJob(translatedTitle, body)
  }

  private suspend fun ask(system: String, user: String, withTitle: Boolean, chars: Int): Answer {
    val completion =
      client.chat(
        task = AiTask.TRANSLATE,
        system = system,
        user = user,
        schemaName = SCHEMA_NAME,
        schema = if (withTitle) TITLE_SCHEMA else TEXT_SCHEMA,
        // Tokens run well under characters; the room covers longer target languages.
        maxTokens = (chars + MIN_TOKENS).coerceAtMost(MAX_TOKENS),
      )
    usage.record(completion.inputTokens, completion.outputTokens)
    return try {
      json.decodeFromString(Answer.serializer(), completion.content)
    } catch (e: Exception) {
      throw LlmException("Unreadable translation: ${completion.content.take(200)}", e)
    }
  }

  @Serializable private data class Answer(val title: String? = null, val text: String = "")

  companion object {
    const val PART_CHARS = 2_500
    private const val MAX_PARTS = 12
    private const val MIN_TOKENS = 600
    private const val MAX_TOKENS = 8_000
    private const val SCHEMA_NAME = "JobTranslation"

    fun instructions(from: String, to: String): String =
      """You translate job postings from $from into $to. Translate the whole text faithfully: do not summarize, shorten, explain or add anything.
- Keep the Markdown exactly as it is: headings, lists, bold, links and line breaks.
- Keep names of companies, products, tools and technologies, e-mail addresses, URLs and numbers as they are.
- Translate gender marks in titles too, for example "(m/w/d)" becomes "(m/f/d)" in English.
- If a part is already in $to, return it unchanged.
Reply with JSON only: "title" (the translated title, when a TITLE is given) and "text" (the translated TEXT, empty when the TEXT is empty)."""

    /**
     * [text] in parts of at most [PART_CHARS] where it can be, split between paragraphs, then
     * lines; at most [MAX_PARTS], so a very long posting is cut short instead of half translated.
     */
    fun parts(text: String): List<String> {
      if (text.isBlank()) return emptyList()
      val blocks =
        text.split(Regex("""\n\s*\n""")).flatMap { block ->
          if (block.length <= PART_CHARS) listOf(block)
          else block.lines().flatMap { it.chunked(PART_CHARS) }
        }
      val parts = mutableListOf<String>()
      val current = StringBuilder()
      for (block in blocks) {
        if (current.isNotEmpty() && current.length + block.length + 2 > PART_CHARS) {
          parts += current.toString()
          current.clear()
        }
        if (current.isNotEmpty()) current.append("\n\n")
        current.append(block)
      }
      if (current.isNotBlank()) parts += current.toString()
      return parts.take(MAX_PARTS)
    }

    private val TEXT_SCHEMA: JsonObject = schema(withTitle = false)
    private val TITLE_SCHEMA: JsonObject = schema(withTitle = true)

    private fun schema(withTitle: Boolean): JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        if (withTitle) putJsonObject("title") { put("type", "string") }
        putJsonObject("text") { put("type", "string") }
      }
      put(
        "required",
        buildJsonArray {
          if (withTitle) add(JsonPrimitive("title"))
          add(JsonPrimitive("text"))
        },
      )
      put("additionalProperties", false)
    }
  }
}
