package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.ui.platform.HfQuant
import io.github.raghavsatyadev.jobfinder.ui.platform.HfRepo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Hugging Face's public model API, no sign-in: GGUF repositories by search, and the quantisations a
 * repository has. Ollama pulls one as `hf.co/<repo>:<quant>`.
 */
class HuggingFace(private val http: HttpClient, private val baseUrl: String = BASE_URL) {
  /** GGUF repositories matching [query], most downloaded first. */
  suspend fun search(query: String): List<HfRepo> {
    val response =
      http.get("$baseUrl/api/models") {
        parameter("search", query.trim())
        parameter("filter", "gguf")
        parameter("sort", "downloads")
        parameter("direction", "-1")
        parameter("limit", SEARCH_LIMIT)
        timeout { requestTimeoutMillis = TIMEOUT_MS }
      }
    check(response.status.isSuccess()) { "Hugging Face answered ${response.status.value}" }
    return parseRepos(response.bodyAsText())
  }

  /** The quantisations in [repo], smallest first. */
  suspend fun quants(repo: String): List<HfQuant> {
    val path = repo.split('/').joinToString("/") { it.encodeURLPathPart() }
    val response =
      http.get("$baseUrl/api/models/$path/tree/main") {
        parameter("recursive", "true")
        timeout { requestTimeoutMillis = TIMEOUT_MS }
      }
    check(response.status.isSuccess()) { "Hugging Face answered ${response.status.value}" }
    return parseQuants(response.bodyAsText())
  }

  companion object {
    const val BASE_URL = "https://huggingface.co"
    private const val SEARCH_LIMIT = 20
    private const val TIMEOUT_MS = 20_000L

    /** `Q4_K_M`, `IQ3_XXS`, `Q8_0`, `BF16` and so on, as Ollama's `hf.co` tags name them. */
    private val QUANT = Regex("""(?i)(?<![A-Z0-9])(I?Q\d(?:_[A-Z0-9]+)*|BF16|F16|F32)(?=[-.])""")

    fun parseRepos(body: String): List<HfRepo> =
      (Json.parseToJsonElement(body) as JsonArray).mapNotNull { element ->
        val repo = element as? JsonObject ?: return@mapNotNull null
        val id = repo["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        HfRepo(
          id,
          repo["downloads"]?.jsonPrimitive?.longOrNull ?: 0,
          repo["likes"]?.jsonPrimitive?.longOrNull ?: 0,
        )
      }

    /**
     * The GGUF files in a `tree` answer, grouped by quantisation: split files (`-00001-of-00002`)
     * add up, and vision projector files (`mmproj`) are left out.
     */
    fun parseQuants(body: String): List<HfQuant> =
      (Json.parseToJsonElement(body) as JsonArray)
        .mapNotNull { element ->
          val file = element as? JsonObject ?: return@mapNotNull null
          if (file["type"]?.jsonPrimitive?.contentOrNull != "file") return@mapNotNull null
          val path = file["path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
          val name = path.substringAfterLast('/')
          if (!name.endsWith(".gguf", ignoreCase = true)) return@mapNotNull null
          if (name.contains("mmproj", ignoreCase = true)) return@mapNotNull null
          val quant = QUANT.find(name)?.value?.uppercase() ?: return@mapNotNull null
          val size =
            file["lfs"]?.jsonObject?.get("size")?.jsonPrimitive?.longOrNull
              ?: file["size"]?.jsonPrimitive?.longOrNull
              ?: 0L
          quant to size
        }
        .groupBy({ it.first }, { it.second })
        .map { (quant, sizes) -> HfQuant(quant, sizes.sum()) }
        .sortedBy { it.sizeBytes }
  }
}
