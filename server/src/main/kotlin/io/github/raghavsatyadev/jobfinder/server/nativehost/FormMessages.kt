package io.github.raghavsatyadev.jobfinder.server.nativehost

import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.server.forms.FormAnswersService
import io.github.raghavsatyadev.jobfinder.server.forms.FormField
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory

/**
 * The form messages of `browser-extension/PROTOCOL.md`: `saveAnswers` → `answersSaved`,
 * `answersFor` → `answers`, `draftAnswer` → `draft`. Each returns the reply to send.
 */
class FormMessages(private val forms: FormAnswersService) {
  suspend fun handle(type: String, id: String, message: JsonObject): JsonObject {
    val url =
      message.string("url")?.takeIf { it.length <= NativeProtocol.MAX_URL_CHARS }
        ?: return bad("No string url up to 8192 chars", id)
    return try {
      when (type) {
        SAVE_ANSWERS -> {
          val fields = fields(message) ?: return bad("fields must be a list of objects", id)
          val result = forms.save(url, fields)
          reply(ANSWERS_SAVED, id) {
            put("saved", result.saved)
            result.jobId?.let { put("jobId", it.toString()) }
          }
        }
        ANSWERS_FOR -> {
          val fields = fields(message) ?: return bad("fields must be a list of objects", id)
          val ai = (message["ai"] as? JsonPrimitive)?.booleanOrNull ?: false
          val result = forms.answersFor(url, fields, ai)
          reply(ANSWERS, id) {
            result.jobId?.let { put("jobId", it.toString()) }
            result.resumeFileName?.let { put("resumeFileName", it) }
            putJsonArray("answers") {
              result.answers.forEach { answer ->
                add(
                  buildJsonObject {
                    put("ref", answer.ref)
                    put("value", answer.value)
                    put("questionKey", answer.questionKey)
                    put("match", answer.match.wire)
                    put("score", answer.score)
                  }
                )
              }
            }
          }
        }
        DRAFT_ANSWER -> {
          val field =
            (message["field"] as? JsonObject)?.let(::field)
              ?: return bad("field must be an object", id)
          val current = message.string("current")?.take(FormAnswersService.MAX_VALUE_CHARS)
          val text = forms.draft(url, field, current)
          reply(DRAFT, id) { put("text", text) }
        }
        else -> NativeProtocol.error(NativeErrorCode.UNKNOWN_TYPE, "Unknown type $type", id)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: LlmException) {
      log.info("A form answer draft failed: {}", e.message)
      NativeProtocol.error(AI_FAILED, "The AI gave no answer", id)
    } catch (e: Exception) {
      log.warn("A form request failed", e)
      NativeProtocol.error(NativeErrorCode.INTERNAL, "The request failed", id)
    }
  }

  private fun fields(message: JsonObject): List<FormField>? {
    val array = message["fields"] as? JsonArray ?: return null
    if (array.size > FormAnswersService.MAX_FIELDS) return null
    return array.map { (it as? JsonObject)?.let(::field) ?: return null }
  }

  private fun field(o: JsonObject): FormField {
    fun text(name: String) =
      o.string(name)?.takeIf { it.isNotBlank() }?.take(FormAnswersService.MAX_TEXT_CHARS)
    return FormField(
      ref = o.string("ref")?.take(NativeProtocol.MAX_ID_CHARS),
      label = text("label"),
      name = text("name"),
      elementId = text("elementId"),
      placeholder = text("placeholder"),
      ariaLabel = text("ariaLabel"),
      inputType = o.string("inputType")?.lowercase()?.take(MAX_TYPE_CHARS) ?: "text",
      autocomplete = text("autocomplete"),
      options =
        (o["options"] as? JsonArray)
          ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
          ?.map { it.take(MAX_OPTION_CHARS) }
          ?.take(MAX_OPTIONS)
          .orEmpty(),
      value = o.string("value")?.take(FormAnswersService.MAX_VALUE_CHARS),
    )
  }

  private fun bad(message: String, id: String) =
    NativeProtocol.error(NativeErrorCode.BAD_REQUEST, message, id)

  private inline fun reply(
    type: String,
    id: String,
    body: JsonObjectBuilder.() -> Unit,
  ): JsonObject = buildJsonObject {
    put("v", NativeProtocol.VERSION)
    put("type", type)
    put("id", id)
    body()
  }

  companion object {
    const val SAVE_ANSWERS = "saveAnswers"
    const val ANSWERS_SAVED = "answersSaved"
    const val ANSWERS_FOR = "answersFor"
    const val ANSWERS = "answers"
    const val DRAFT_ANSWER = "draftAnswer"
    const val DRAFT = "draft"

    /** The `error` code when the AI could not write a draft. */
    const val AI_FAILED = "ai_failed"

    /** Requests this class answers. */
    val REQUESTS = setOf(SAVE_ANSWERS, ANSWERS_FOR, DRAFT_ANSWER)

    private const val MAX_TYPE_CHARS = 32
    private const val MAX_OPTIONS = 200
    private const val MAX_OPTION_CHARS = 300
    private val log = LoggerFactory.getLogger("jobfinder.forms")
  }
}
