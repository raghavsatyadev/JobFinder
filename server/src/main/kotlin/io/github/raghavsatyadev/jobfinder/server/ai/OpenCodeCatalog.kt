package io.github.raghavsatyadev.jobfinder.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * The free OpenCode models, as the user's OpenCode server lists them now (no names built in), and
 * the ones paused for a day.
 *
 * A model that fails [FAILS_TO_PAUSE] times in a row (an error, no answer, or out of free use; a
 * short rate limit does not count) is paused for [PAUSE_MS], and the other free models take its
 * calls. When the day is over it is tried again from a clean count. Pauses are kept in [pauseFile]
 * across restarts.
 */
class OpenCodeCatalog(
  private val server: OpenCodeServer,
  private val http: HttpClient,
  private val pauseFile: Path? = null,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val log = LoggerFactory.getLogger(OpenCodeCatalog::class.java)
  private val lock = Mutex()
  @Volatile private var models: List<String> = emptyList()
  @Volatile private var listedAt = 0L
  private val fails = ConcurrentHashMap<String, Int>()
  private val pausedUntil = ConcurrentHashMap<String, Long>(read())

  /** The free models known from the last listing, without starting OpenCode. */
  fun known(): List<String> = models

  /**
   * The free models, listed again from the OpenCode server when the last listing is older than
   * [LIST_MS]. Keeps the last list (or empty) when OpenCode can't list them.
   */
  suspend fun freeModels(): List<String> = lock.withLock {
    if (models.isNotEmpty() && clock() - listedAt < LIST_MS) return@withLock models
    try {
      val found = list()
      if (found.isNotEmpty()) {
        if (found != models) log.info("OpenCode free models: {}", found.joinToString())
        models = found
        listedAt = clock()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.warn("Could not list the OpenCode models: {}", e.message)
    }
    models
  }

  /** [preferred] first, then the other free models that are not paused. */
  suspend fun order(preferred: String): List<String> =
    (listOf(preferred.trim()).filter { it.isNotEmpty() } + freeModels()).distinct().filter {
      !isPaused(it)
    }

  /** Whether [model] is paused after failing again and again; a past pause is cleared here. */
  fun isPaused(model: String): Boolean {
    val until = pausedUntil[model] ?: return false
    if (until > clock()) return true
    pausedUntil.remove(model)
    fails.remove(model)
    save()
    return false
  }

  /** The paused models and until when (epoch ms). */
  fun paused(): Map<String, Long> = pausedUntil.filterValues { it > clock() }

  fun succeeded(model: String) {
    fails.remove(model)
  }

  /** Counts a failure of [model]; the [FAILS_TO_PAUSE]th in a row pauses it for a day. */
  fun failed(model: String, reason: String?) {
    val count = fails.merge(model, 1, Int::plus) ?: 1
    if (count < FAILS_TO_PAUSE) return
    fails.remove(model)
    pausedUntil[model] = clock() + PAUSE_MS
    log.warn("OpenCode model {} failed {} times, paused for a day: {}", model, count, reason)
    save()
  }

  private suspend fun list(): List<String> {
    val endpoint = server.endpoint()
    val text =
      http
        .get(endpoint.baseUrl + "/api/model") {
          header(HttpHeaders.Authorization, endpoint.authorization)
          timeout { requestTimeoutMillis = LIST_TIMEOUT_MS }
        }
        .bodyAsText()
    return freeOf(text)
  }

  private fun read(): Map<String, Long> =
    try {
      val file = pauseFile
      if (file == null || !Files.exists(file)) emptyMap()
      else
        Files.readAllLines(file)
          .mapNotNull { line ->
            val model = line.substringBeforeLast('=', "").trim()
            val until = line.substringAfterLast('=').trim().toLongOrNull()
            if (model.isEmpty() || until == null) null else model to until
          }
          .toMap()
    } catch (e: Exception) {
      emptyMap()
    }

  private fun save() {
    val file = pauseFile ?: return
    try {
      Files.createDirectories(file.parent)
      Files.write(file, paused().map { (model, until) -> "$model=$until" })
    } catch (e: Exception) {
      log.warn("Could not keep the OpenCode pauses: {}", e.message)
    }
  }

  companion object {
    private val JSON = Json { ignoreUnknownKeys = true }

    const val FAILS_TO_PAUSE = 3
    const val PAUSE_MS = 24 * 60 * 60_000L
    private const val LIST_MS = 6 * 60 * 60_000L
    private const val LIST_TIMEOUT_MS = 15_000L
    private const val PROVIDER = "opencode"

    /**
     * The ids in OpenCode's `/api/model` answer that cost nothing: OpenCode's own provider, active,
     * text out, and every price 0.
     */
    internal fun freeOf(text: String): List<String> {
      val data = JSON.parseToJsonElement(text).jsonObject["data"]?.jsonArray ?: return emptyList()
      return data.mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id = model.string("id") ?: return@mapNotNull null
        val free =
          model.string("providerID") == PROVIDER &&
            model.string("status").let { it == null || it == "active" } &&
            model.string("enabled") != "false" &&
            textOut(model) &&
            costsNothing(model["cost"] as? JsonArray)
        id.takeIf { free }
      }
    }

    private fun JsonObject.string(name: String): String? =
      (this[name] as? JsonPrimitive)?.contentOrNull

    private fun textOut(model: JsonObject): Boolean {
      val out = (model["capabilities"] as? JsonObject)?.get("output") as? JsonArray ?: return true
      return out.any { it.jsonPrimitive.contentOrNull == "text" }
    }

    private fun costsNothing(cost: JsonArray?): Boolean {
      if (cost.isNullOrEmpty()) return false
      return cost.all { entry ->
        val o = entry as? JsonObject ?: return@all false
        listOf("input", "output").all { (o[it]?.jsonPrimitive?.doubleOrNull ?: 1.0) == 0.0 }
      }
    }
  }
}
