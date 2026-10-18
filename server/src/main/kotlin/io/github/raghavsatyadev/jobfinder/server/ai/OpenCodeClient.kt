package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.OpenCodeModels
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Sends one prompt to the OpenCode CLI's free models and gives back the JSON text, like an online
 * key does in [LlmClient].
 *
 * Each call is a fresh session in a fresh empty folder: every tool is denied except `read` (the
 * free tier refuses a session with no tools at all), and `read` only sees that empty folder. The
 * prompt says to answer from the text only. The session and the folder are deleted afterwards.
 *
 * Models go in [OpenCodeModels.order]; one out of free use or rate-limited rests a while and the
 * next one takes the call. A model that times out or answers 5xx gets one more try after a pause.
 */
class OpenCodeClient(
  private val server: OpenCodeServer,
  private val http: HttpClient,
  private val clock: () -> Long = System::currentTimeMillis,
  private val semaphore: Semaphore = Semaphore(PARALLEL_CALLS),
) {
  private val log = LoggerFactory.getLogger(OpenCodeClient::class.java)
  private val restUntil = ConcurrentHashMap<String, Long>()

  /**
   * The JSON answer to [system] and [user], fitting [schema]. [preferred] goes first. Throws
   * [LlmException] when no model answered.
   */
  suspend fun chat(
    preferred: String,
    system: String,
    user: String,
    schema: JsonObject,
  ): ChatCompletion = semaphore.withPermit {
    val prompt = prompt(system, user, schema)
    var last: Exception? = null
    for (model in OpenCodeModels.order(preferred)) {
      if ((restUntil[model] ?: 0) > clock()) continue
      var tries = 0
      while (true) {
        try {
          return@withPermit ask(model, prompt)
        } catch (e: CancellationException) {
          throw e
        } catch (e: QuotaException) {
          restUntil[model] = clock() + e.restMs
          log.info("OpenCode model {} rests {} min: {}", model, e.restMs / 60_000, e.message)
          last = e
          break
        } catch (e: PassingException) {
          last = e
          if (++tries > RETRIES) break
          delay(BACKOFF_MS * tries)
          server.restartIfDead()
        } catch (e: LlmException) {
          last = e
          break
        }
      }
    }
    throw LlmException("No OpenCode model answered: ${last?.message ?: "all are resting"}", last)
  }

  private suspend fun ask(model: String, prompt: String): ChatCompletion {
    val endpoint = server.endpoint()
    val folder = withContext(Dispatchers.IO) { Files.createTempDirectory(TEMP_PREFIX) }
    var session: String? = null
    try {
      val created =
        call(endpoint, "/api/session", SESSION_TIMEOUT_MS, sessionBody(model, folder).toString())
      session =
        data(created)?.get("id")?.jsonPrimitive?.contentOrNull
          ?: throw LlmException("OpenCode made no session")
      val generated =
        call(
          endpoint,
          "/api/session/$session/generate",
          GENERATE_TIMEOUT_MS,
          buildJsonObject { put("prompt", prompt) }.toString(),
        )
      val text =
        data(generated)?.get("text")?.jsonPrimitive?.contentOrNull
          ?: throw LlmException("OpenCode $model gave no text")
      val json = jsonOf(text) ?: throw LlmException("OpenCode $model answered without JSON")
      // No token counts come back; a rough count (4 characters a token) keeps the usage log useful.
      return ChatCompletion(json, prompt.length / 4L, json.length / 4L)
    } finally {
      withContext(NonCancellable) {
        session?.let { id -> runCatching { deleteSession(endpoint, id) } }
        withContext(Dispatchers.IO) { runCatching { folder.toFile().deleteRecursively() } }
      }
    }
  }

  private suspend fun call(
    endpoint: OpenCodeEndpoint,
    path: String,
    timeoutMs: Long,
    body: String,
  ): String {
    val response: HttpResponse =
      try {
        http.post(endpoint.baseUrl + path) {
          header(HttpHeaders.Authorization, endpoint.authorization)
          contentType(ContentType.Application.Json)
          timeout {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = timeoutMs
            socketTimeoutMillis = timeoutMs
          }
          setBody(body)
        }
      } catch (e: HttpRequestTimeoutException) {
        throw PassingException("timeout after ${timeoutMs / 1000} s", e)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw PassingException(e.message ?: e::class.simpleName.orEmpty(), e)
      }
    val text = response.bodyAsText()
    val status = response.status.value
    if (status in 200..299) return text
    val message = errorMessage(text)
    val lower = message.lowercase()
    when {
      status == 429 || QUOTA_WORDS.any { it in lower } ->
        throw QuotaException(
          "HTTP $status: $message",
          if (FREE_USE_WORDS.any { it in lower }) FREE_USE_REST_MS else RATE_REST_MS,
        )
      status == 503 && "unavailable" in lower && "model" in lower ->
        throw QuotaException("HTTP $status: $message", UNAVAILABLE_REST_MS)
      status >= 500 -> throw PassingException("HTTP $status: $message")
      else -> throw LlmException("OpenCode HTTP $status: $message")
    }
  }

  private suspend fun deleteSession(endpoint: OpenCodeEndpoint, id: String) {
    http.delete("${endpoint.baseUrl}/api/session/$id") {
      header(HttpHeaders.Authorization, endpoint.authorization)
      timeout { requestTimeoutMillis = SESSION_TIMEOUT_MS }
    }
  }

  /** A failure that may pass: timeout, connection error, 5xx. */
  private class PassingException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

  /** Out of free use or rate-limited: the model rests [restMs]. */
  private class QuotaException(message: String, val restMs: Long) : Exception(message)

  companion object {
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val PARALLEL_CALLS = 2
    private const val RETRIES = 1
    private const val BACKOFF_MS = 2_000L
    private const val CONNECT_TIMEOUT_MS = 5_000L
    private const val SESSION_TIMEOUT_MS = 10_000L
    private const val GENERATE_TIMEOUT_MS = 20_000L
    private const val RATE_REST_MS = 5 * 60_000L
    /** Free use runs out per model and can stay out for days. */
    private const val FREE_USE_REST_MS = 6 * 60 * 60_000L
    private const val UNAVAILABLE_REST_MS = 30 * 60_000L
    private const val TEMP_PREFIX = "jobfinder-opencode-"

    private val QUOTA_WORDS = listOf("free usage", "freeusage", "rate limit", "too many requests")
    private val FREE_USE_WORDS = listOf("free usage", "freeusage")

    /** The [LlmTarget] for [model] in a task's list; [LlmClient] sends it here. */
    fun target(model: String): LlmTarget =
      LlmTarget(
        LlmConfig(
          provider = AiProvider.CUSTOM,
          baseUrl = "opencode",
          model = model.ifBlank { OpenCodeModels.DEFAULT },
        ),
        AiRoute.OPEN_CODE_ID,
      )

    private fun prompt(system: String, user: String, schema: JsonObject): String = buildString {
      append(system.trim())
      append(
        "\n\nAnswer from the text in this message only. Never call tools and never read files."
      )
      append("\nReply with one JSON object only, no other text, that fits this JSON schema:\n")
      append(schema.toString())
      append("\n\n")
      append(user.trim())
    }

    private fun sessionBody(model: String, folder: Path) = buildJsonObject {
      put(
        "model",
        buildJsonObject {
          put("providerID", "opencode")
          put("id", model)
        },
      )
      put("location", buildJsonObject { put("directory", folder.toAbsolutePath().toString()) })
      put(
        "permissions",
        buildJsonArray {
          add(rule("*", "deny"))
          // The free tier refuses a session with no tools; read only sees the empty folder.
          add(rule("read", "allow"))
        },
      )
    }

    private fun rule(action: String, effect: String) = buildJsonObject {
      put("action", action)
      put("resource", "*")
      put("effect", effect)
    }

    private fun data(text: String): JsonObject? = runCatching {
      JSON.parseToJsonElement(text).jsonObject["data"]?.jsonObject
    }
      .getOrNull()

    /** `{"_tag":"…","message":"…"}`, or the start of the body. */
    private fun errorMessage(text: String): String =
      runCatching {
        val o = JSON.parseToJsonElement(text).jsonObject
        listOfNotNull(
            o["_tag"]?.jsonPrimitive?.contentOrNull,
            o["message"]?.jsonPrimitive?.contentOrNull,
          )
          .joinToString(": ")
          .ifBlank { null }
      }
        .getOrNull() ?: text.take(200)

    /** The JSON object in [text]: a ```json fence and talk around it are dropped. */
    fun jsonOf(text: String): String? {
      val start = text.indexOf('{')
      val end = text.lastIndexOf('}')
      if (start < 0 || end <= start) return null
      val candidate = text.substring(start, end + 1)
      return candidate.takeIf { runCatching { JSON.parseToJsonElement(it).jsonObject }.isSuccess }
    }
  }
}
