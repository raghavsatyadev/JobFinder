package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.desktop.setup.DesktopOllamaSetup
import io.github.raghavsatyadev.jobfinder.desktop.setup.Progress
import io.github.raghavsatyadev.jobfinder.desktop.setup.PullProgress
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.readUTF8Line
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A model Ollama has, and its size on disk. */
data class OllamaModel(val name: String, val sizeBytes: Long)

/** Ollama could not do what was asked; [message] is Ollama's own error when it gave one. */
class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Ollama's model API: list, pull, push a GGUF file, create and delete. */
interface OllamaModels {
  /** `null` when Ollama does not answer. */
  suspend fun list(): List<OllamaModel>?

  /** Pulls [model] (a library name or `hf.co/<repo>:<quant>`). Throws [OllamaException]. */
  suspend fun pull(model: String, onProgress: Progress)

  /** Gives Ollama [file] as the blob [digest] (`sha256:<hex>`) unless it has it already. */
  suspend fun pushBlob(file: File, digest: String)

  /**
   * Makes [name] from the model [from] or from the GGUF [files] (file name to digest), with
   * [parameters] such as `num_ctx`. Throws [OllamaException].
   */
  suspend fun create(
    name: String,
    from: String? = null,
    files: Map<String, String> = emptyMap(),
    parameters: Map<String, Number> = emptyMap(),
  )

  suspend fun delete(model: String)
}

/** [OllamaModels] on Ollama's HTTP API at [baseUrl]. */
class HttpOllamaModels(
  private val http: HttpClient,
  private val baseUrl: String = DesktopOllamaSetup.BASE_URL,
) : OllamaModels {
  override suspend fun list(): List<OllamaModel>? =
    try {
      val response = http.get("$baseUrl/api/tags") { timeout { requestTimeoutMillis = QUICK_MS } }
      if (response.status.isSuccess()) parseList(response.bodyAsText()) else null
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      null
    }

  override suspend fun pull(model: String, onProgress: Progress) {
    val progress = PullProgress()
    try {
      http
        .preparePost("$baseUrl/api/pull") {
          longRunning()
          contentType(ContentType.Application.Json)
          setBody(buildJsonObject { put("model", model) }.toString())
        }
        .execute { response ->
          if (!response.status.isSuccess()) throw OllamaException(error(response.bodyAsText()))
          val channel = response.bodyAsChannel()
          while (true) {
            val line = channel.readUTF8Line() ?: break
            if (progress.add(line)) onProgress(progress.done, progress.total)
          }
        }
    } catch (e: Exception) {
      if (e is CancellationException || e is OllamaException) throw e
      throw OllamaException(e.cause?.message ?: e.message ?: "pull failed", e)
    }
    if (!progress.success) throw OllamaException("The download did not finish")
  }

  override suspend fun pushBlob(file: File, digest: String) {
    val url = "$baseUrl/api/blobs/$digest"
    if (http.head(url) { timeout { requestTimeoutMillis = QUICK_MS } }.status.isSuccess()) return
    val response =
      http.post(url) {
        longRunning()
        setBody(file.inputStream().toByteReadChannel())
      }
    if (!response.status.isSuccess()) throw OllamaException(error(response.bodyAsText()))
  }

  override suspend fun create(
    name: String,
    from: String?,
    files: Map<String, String>,
    parameters: Map<String, Number>,
  ) {
    val body = buildJsonObject {
      put("model", name)
      from?.let { put("from", it) }
      if (files.isNotEmpty()) put("files", JsonObject(files.mapValues { JsonPrimitive(it.value) }))
      if (parameters.isNotEmpty()) {
        put("parameters", JsonObject(parameters.mapValues { JsonPrimitive(it.value) }))
      }
      put("stream", false)
    }
    val response =
      http.post("$baseUrl/api/create") {
        longRunning()
        contentType(ContentType.Application.Json)
        setBody(body.toString())
      }
    if (!response.status.isSuccess()) throw OllamaException(error(response.bodyAsText()))
  }

  override suspend fun delete(model: String) {
    val response =
      http.delete("$baseUrl/api/delete") {
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject { put("model", model) }.toString())
      }
    if (!response.status.isSuccess()) throw OllamaException(error(response.bodyAsText()))
  }

  private fun HttpRequestBuilder.longRunning() = timeout {
    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    socketTimeoutMillis = QUIET_LIMIT_MS
  }

  companion object {
    private const val QUICK_MS = 3_000L
    /** Ollama checks a big download's digest before it says anything more. */
    private const val QUIET_LIMIT_MS = 10 * 60_000L

    /** The models in an `/api/tags` answer. */
    fun parseList(body: String): List<OllamaModel>? = runCatching {
      (Json.parseToJsonElement(body) as JsonObject)["models"]
        ?.let { it as JsonArray }
        .orEmpty()
        .mapNotNull { element ->
          val model = element as? JsonObject ?: return@mapNotNull null
          val name = model["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
          OllamaModel(name, model["size"]?.jsonPrimitive?.longOrNull ?: 0L)
        }
    }
      .getOrNull()

    /** Ollama's `{"error": "..."}`, or the body itself. */
    fun error(body: String): String =
      runCatching {
        (Json.parseToJsonElement(body) as JsonObject)["error"]?.jsonPrimitive?.contentOrNull
      }
        .getOrNull() ?: body.take(200).ifBlank { "Ollama refused" }
  }
}
