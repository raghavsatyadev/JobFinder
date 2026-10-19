package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Where the model lives. Local Ollama is the default; an online [provider] needs the user's own
 * [apiKey], which never reaches logs or error messages. A model name starting with `nimble` (the
 * agent kit's decision model) is rejected for every provider.
 */
class LlmConfig(
  val provider: AiProvider = AiProvider.OLLAMA,
  val baseUrl: String = provider.defaultBaseUrl,
  val model: String = DEFAULT_MODEL,
  val apiKey: String? = null,
  val connectTimeoutMs: Long = 10_000,
  /** Ollama may swap models on the shared GPU before answering. */
  val requestTimeoutMs: Long = 15 * 60_000,
) {
  init {
    require(model.isNotBlank()) { "Model name is blank" }
    require(!model.trim().lowercase().startsWith("nimble")) {
      "Model '$model' is the agent kit decision model and must not be used by the app"
    }
    require(baseUrl.isNotBlank()) { "The address of the AI server is blank" }
  }

  /** Never prints the key. */
  override fun toString(): String =
    "LlmConfig(provider=$provider, baseUrl=$baseUrl, model=$model, key=${if (apiKey.isNullOrEmpty()) "none" else "set"})"

  companion object {
    const val DEFAULT_BASE_URL = "http://localhost:11434/v1"
    const val DEFAULT_MODEL = "jobfinder-qwen:latest"

    /** Online answers come in seconds; no model swap to wait for. */
    private const val ONLINE_TIMEOUT_MS = 3 * 60_000L

    /** The config for [settings], with the key read from the protected store. */
    fun of(settings: AiSettings, apiKey: String?): LlmConfig =
      LlmConfig(
        provider = settings.provider,
        baseUrl = settings.endpoint,
        model = settings.model.trim(),
        apiKey = apiKey?.takeIf { it.isNotBlank() },
        requestTimeoutMs =
          if (settings.provider.online) ONLINE_TIMEOUT_MS else LlmConfig().requestTimeoutMs,
      )
  }
}

/** Records token counts after a successful parse (the old `logAiUsage`, without cost). */
fun interface AiUsageRecorder {
  suspend fun record(inputTokens: Long, outputTokens: Long)

  companion object {
    val NONE = AiUsageRecorder { _, _ -> }
  }
}

/** What a chat completion gave back. */
data class ChatCompletion(val content: String, val inputTokens: Long, val outputTokens: Long)

/** Any failure talking to the model; callers turn it into a failed result. */
open class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The model's per-request size limit (HTTP 413), such as a free key's tokens per minute. */
class RequestTooLargeException(message: String) : LlmException(message)

/**
 * One chat client for every provider: the OpenAI-compatible chat API (Ollama, OpenAI, Gemini,
 * OpenRouter, NVIDIA, Groq, custom servers) and Anthropic's Messages API. The answer is always JSON
 * for the given schema.
 *
 * Each call names its [AiTask]; [pool] gives that task's models in order (online keys and the main
 * choice). A key out of quota, rate-limited or failing hands the call to the next one. Calls to the
 * main choice share one [semaphore], so the local model gets one request at a time and Ollama never
 * swaps models mid-scan; online keys answer in parallel. Exactly one retry on timeout, 5xx or
 * connection error. [config] (the main choice) is read on every call, so Settings apply at once. A
 * target of the OpenCode CLI ([AiRoute.OPEN_CODE_ID]) goes to [openCode], which keeps its own quota
 * and fallbacks; one of the Antigravity CLI ([AiRoute.AGY_ID]) goes to [agy].
 */
class LlmClient(
  private val http: HttpClient,
  private val semaphore: Semaphore = Semaphore(1),
  private val pool: AiKeyPool? = null,
  private val openCode: OpenCodeClient? = null,
  private val agy: AgyClient? = null,
  private val config: suspend () -> LlmConfig,
) {
  constructor(
    http: HttpClient,
    config: LlmConfig = LlmConfig(),
    semaphore: Semaphore = Semaphore(1),
  ) : this(http, semaphore, null, null, null, { config })

  suspend fun chat(
    task: AiTask,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
  ): ChatCompletion {
    val main = config()
    val targets = pool?.targets(task, main) ?: listOf(LlmTarget(main, null))
    if (targets.isEmpty()) throw LlmException("No model is set up for ${task.name.lowercase()}")
    var last: Exception? = null
    for ((index, target) in targets.withIndex()) {
      val more = index < targets.lastIndex
      val started = System.nanoTime()
      // Which model answered, and how long each took: the way to see a slow or failing route.
      fun took() = (System.nanoTime() - started) / NANOS_PER_SECOND
      val name = "${target.config.provider.name.lowercase()} ${target.config.model}"
      try {
        val answer = send(target, system, user, schemaName, schema, maxTokens, !more)
        if (answer == null) {
          log.debug("{}: {} skipped (resting or out of quota)", task.name.lowercase(), name)
          continue
        }
        log.info("{}: answered by {} in {} s", task.name.lowercase(), name, took())
        return answer
      } catch (e: CancellationException) {
        throw e
      } catch (e: RateLimitedException) {
        log.info("{}: {} busy after {} s", task.name.lowercase(), name, took())
        last = e
      } catch (e: LlmException) {
        log.info("{}: {} failed after {} s: {}", task.name.lowercase(), name, took(), e.message)
        if (!more) throw e
        last = e
      }
    }
    throw LlmException("Every model for ${task.name.lowercase()} is busy or out of quota", last)
  }

  /**
   * The model all of [tasks] try first, when it is the same online model (an added key or a CLI):
   * their work for one job can then go in one [MergedCall]. `null` for the model on this computer,
   * for different first models, or when a task has none.
   */
  suspend fun sharedTarget(tasks: Collection<AiTask>): LlmTarget? {
    val pool = pool ?: return null
    val main = config()
    val firsts = tasks.map { pool.targets(it, main).firstOrNull() ?: return null }
    val first = firsts.firstOrNull() ?: return null
    if (first.keyId == null) return null
    return first.takeIf {
      firsts.all { it.keyId == first.keyId && it.config.model == first.config.model }
    }
  }

  /**
   * One call on [target] only, counted once against its quota, with no fallback to other models:
   * the caller does the work the usual way when it fails.
   */
  suspend fun chatOn(
    target: LlmTarget,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
  ): ChatCompletion =
    try {
      send(target, system, user, schemaName, schema, maxTokens, last = false)
        ?: throw LlmException("${target.config.model} is busy or out of quota")
    } catch (e: RateLimitedException) {
      throw LlmException("${target.config.model} is out of quota for now", e)
    }

  /**
   * One model of a task's list; `null` when it can't take a request now (resting, out of quota or
   * its CLI is off). A rate limit rests its key and goes up as [RateLimitedException].
   */
  private suspend fun send(
    target: LlmTarget,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
    last: Boolean,
  ): ChatCompletion? {
    val keyId = target.keyId
    val openCodeCli = keyId == AiRoute.OPEN_CODE_ID
    val agyCli = keyId == AiRoute.AGY_ID
    val cli = openCodeCli || agyCli
    if (openCodeCli && openCode == null) return null
    if (agyCli && agy == null) return null
    if (keyId != null && !cli && pool?.take(keyId) == false) return null
    try {
      val answer =
        when {
          openCodeCli -> openCode!!.chat(target.config.model, system, user, schema)
          agyCli -> agy!!.chat(target.config.model, system, user, schema)
          keyId == null ->
            semaphore.withPermit {
              chatWith(target.config, system, user, schemaName, schema, maxTokens, last)
            }
          else -> chatWith(target.config, system, user, schemaName, schema, maxTokens, last)
        }
      if (keyId != null && !cli) pool?.record(keyId, answer.inputTokens + answer.outputTokens)
      return answer
    } catch (e: RateLimitedException) {
      keyId?.let { pool?.rest(it) }
      throw e
    } catch (e: LlmException) {
      // A stuck or empty model would cost every following call the same wait.
      if (keyId != null && !cli) pool?.rest(keyId, AiKeyPool.FAILED_REST_MS)
      throw e
    }
  }

  /** One call with [config] outside the shared queue's order, for the "Test" button. */
  suspend fun test(config: LlmConfig, schema: JsonObject): ChatCompletion = semaphore.withPermit {
    chatWith(
      config,
      "Answer with JSON only.",
      """Reply with {"ok": true}.""",
      "ConnectionTest",
      schema,
      200,
    )
  }

  /**
   * One model: one retry on a passing failure. A rate limit is waited out only when [last] (no
   * other model to try); otherwise it goes up as [RateLimitedException]. A server that refuses the
   * strict JSON schema is asked again in plain JSON mode, with the schema in the instructions.
   */
  private suspend fun chatWith(
    config: LlmConfig,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
    last: Boolean = true,
  ): ChatCompletion {
    var request =
      if (config.provider == AiProvider.ANTHROPIC)
        anthropicRequest(config, system, user, schemaName, schema, maxTokens)
      else openAiRequest(config, system, user, schemaName, schema, maxTokens, strict = true)
    var plainTried = false
    var retried = false
    while (true) {
      try {
        return attempt(config, request)
      } catch (e: SchemaRefusedException) {
        if (plainTried || config.provider == AiProvider.ANTHROPIC) {
          throw LlmException("${name(config)} refused the request: ${e.message}", e)
        }
        plainTried = true
        log.info("{} refused the strict JSON schema, asking in JSON mode", name(config))
        request = openAiRequest(config, system, user, schemaName, schema, maxTokens, strict = false)
      } catch (e: RateLimitedException) {
        if (!last || retried) throw e
        retried = true
      } catch (e: RetryableException) {
        if (retried) throw LlmException("${name(config)} failed twice: ${e.message}", e)
        // An online model with others behind it: they answer sooner than a second try.
        if (!last && config.apiKey != null) throw LlmException("${name(config)}: ${e.message}", e)
        retried = true
      }
    }
  }

  private val log = LoggerFactory.getLogger(LlmClient::class.java)

  private class Request(val path: String, val body: String, val read: (String) -> ChatCompletion)

  private fun openAiRequest(
    config: LlmConfig,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
    strict: Boolean,
  ): Request {
    val local = config.provider == AiProvider.OLLAMA
    val body = buildJsonObject {
      put("model", config.model)
      put(
        "messages",
        buildJsonArray {
          add(
            message(
              "system",
              if (strict) system
              else
                "$system" +
                  "\n\nReply with one JSON object that fits this JSON schema:\n" +
                  "$schema",
            )
          )
          add(message("user", user))
        },
      )
      if (config.provider == AiProvider.OPENAI) {
        // OpenAI's reasoning models take this name, and spend part of it thinking.
        put("max_completion_tokens", maxTokens * ONLINE_TOKEN_ROOM)
      } else {
        put("max_tokens", if (local) maxTokens else maxTokens * ONLINE_TOKEN_ROOM)
      }
      put("stream", false)
      // The same job always gets the same answer. OpenAI's reasoning models only take the default.
      if (config.provider != AiProvider.OPENAI) put("temperature", 0.0)
      // No thinking on the local model: the content stays plain JSON and comes faster.
      if (local) put("reasoning_effort", "none")
      put(
        "response_format",
        buildJsonObject {
          if (strict) {
            put("type", "json_schema")
            put(
              "json_schema",
              buildJsonObject {
                put("name", schemaName)
                put("strict", true)
                put("schema", schema)
              },
            )
          } else {
            put("type", "json_object")
          }
        },
      )
    }
    return Request("chat/completions", body.toString()) { text -> readOpenAi(config, text) }
  }

  private fun readOpenAi(config: LlmConfig, text: String): ChatCompletion {
    val parsed =
      try {
        JSON.decodeFromString(ChatResponse.serializer(), text)
      } catch (e: Exception) {
        throw LlmException("Unreadable ${name(config)} response: ${e.message}", e)
      }
    val choice =
      parsed.choices.firstOrNull() ?: throw LlmException("${name(config)} returned no choices")
    if (choice.finishReason != "stop") {
      throw LlmException("${name(config)} response did not finish: ${choice.finishReason}")
    }
    val content = choice.message?.content
    if (content.isNullOrBlank()) throw LlmException("${name(config)} response has no content")
    return ChatCompletion(
      content = content,
      inputTokens = parsed.usage?.promptTokens ?: 0,
      outputTokens = parsed.usage?.completionTokens ?: 0,
    )
  }

  /** Anthropic gets the schema as the one tool it must call; the tool's input is the answer. */
  private fun anthropicRequest(
    config: LlmConfig,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
  ): Request {
    val body = buildJsonObject {
      put("model", config.model)
      put("max_tokens", maxTokens * ONLINE_TOKEN_ROOM)
      put("temperature", 0.0)
      put("system", system)
      put("messages", buildJsonArray { add(message("user", user)) })
      put(
        "tools",
        buildJsonArray {
          add(
            buildJsonObject {
              put("name", schemaName)
              put("description", "Give the answer.")
              put("input_schema", schema)
            }
          )
        },
      )
      put(
        "tool_choice",
        buildJsonObject {
          put("type", "tool")
          put("name", schemaName)
        },
      )
    }
    return Request("messages", body.toString()) { text -> readAnthropic(text) }
  }

  private fun readAnthropic(text: String): ChatCompletion {
    val parsed =
      try {
        JSON.decodeFromString(AnthropicResponse.serializer(), text)
      } catch (e: Exception) {
        throw LlmException("Unreadable Anthropic response: ${e.message}", e)
      }
    if (parsed.stopReason != "tool_use" && parsed.stopReason != "end_turn") {
      throw LlmException("Anthropic response did not finish: ${parsed.stopReason}")
    }
    val input =
      parsed.content.firstOrNull { it.type == "tool_use" }?.input
        ?: throw LlmException("Anthropic response has no answer")
    return ChatCompletion(
      content = input.toString(),
      inputTokens = parsed.usage?.inputTokens ?: 0,
      outputTokens = parsed.usage?.outputTokens ?: 0,
    )
  }

  private suspend fun attempt(config: LlmConfig, request: Request): ChatCompletion {
    val text =
      try {
        val response =
          http.post("${config.baseUrl.trimEnd('/')}/${request.path}") {
            contentType(ContentType.Application.Json)
            authorize(config)
            timeout {
              connectTimeoutMillis = config.connectTimeoutMs
              requestTimeoutMillis = config.requestTimeoutMs
              socketTimeoutMillis = config.requestTimeoutMs
            }
            setBody(request.body)
          }
        val payload = response.bodyAsText()
        val status = response.status.value
        if (status == 429) {
          throw RateLimitedException("HTTP 429: ${hide(config, payload.take(200))}")
        }
        if (status >= 500) {
          throw RetryableException("HTTP $status: ${hide(config, payload.take(200))}")
        }
        if (status == 400 && SCHEMA_WORDS.any { payload.contains(it, ignoreCase = true) }) {
          throw SchemaRefusedException("HTTP 400: ${hide(config, payload.take(200))}")
        }
        if (status == 413) {
          throw RequestTooLargeException(
            "${name(config)} HTTP 413: ${hide(config, payload.take(200))}"
          )
        }
        if (status == 401 || status == 403) {
          throw LlmException("${name(config)} refused the API key (HTTP $status)")
        }
        if (!response.status.isSuccess()) {
          throw LlmException("${name(config)} HTTP $status: ${hide(config, payload.take(300))}")
        }
        payload
      } catch (e: HttpRequestTimeoutException) {
        throw RetryableException("timeout", e)
      } catch (e: CancellationException) {
        throw e
      } catch (e: LlmException) {
        throw e
      } catch (e: RetryableException) {
        throw e
      } catch (e: RateLimitedException) {
        throw e
      } catch (e: SchemaRefusedException) {
        throw e
      } catch (e: Exception) {
        throw RetryableException(hide(config, e.message ?: e::class.simpleName.orEmpty()), e)
      }
    return request.read(text)
  }

  private fun HttpRequestBuilder.authorize(config: LlmConfig) {
    val key = config.apiKey ?: return
    when (config.provider) {
      AiProvider.ANTHROPIC -> {
        header("x-api-key", key)
        header("anthropic-version", ANTHROPIC_VERSION)
      }
      AiProvider.OLLAMA -> Unit
      else -> header(HttpHeaders.Authorization, "Bearer $key")
    }
    if (config.provider == AiProvider.OPENROUTER) header("X-Title", "JobFinder")
  }

  private fun message(role: String, content: String) = buildJsonObject {
    put("role", role)
    put("content", content)
  }

  private class RetryableException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

  /** HTTP 429: the key is out of quota for now. */
  private class RateLimitedException(message: String) : Exception(message)

  /** HTTP 400 about the response format: the server doesn't take strict JSON schemas. */
  private class SchemaRefusedException(message: String) : Exception(message)

  @Serializable
  private data class ChatResponse(val choices: List<Choice> = emptyList(), val usage: Usage? = null)

  @Serializable
  private data class Choice(
    val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
  )

  @Serializable private data class ResponseMessage(val content: String? = null)

  @Serializable
  private data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Long = 0,
    @SerialName("completion_tokens") val completionTokens: Long = 0,
  )

  @Serializable
  private data class AnthropicResponse(
    val content: List<AnthropicBlock> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
    val usage: AnthropicUsage? = null,
  )

  @Serializable private data class AnthropicBlock(val type: String, val input: JsonElement? = null)

  @Serializable
  private data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Long = 0,
    @SerialName("output_tokens") val outputTokens: Long = 0,
  )

  companion object {
    private val JSON = Json { ignoreUnknownKeys = true }

    private const val NANOS_PER_SECOND = 1_000_000_000L

    private const val ANTHROPIC_VERSION = "2023-06-01"

    /** Words in a 400 answer that mean the strict schema was the problem. */
    private val SCHEMA_WORDS = listOf("response_format", "json_schema", "schema", "structured")

    /**
     * Online models may think before answering, inside the same token budget; the local model is
     * told not to. Room for that, so a long review doesn't stop at the limit.
     */
    private const val ONLINE_TOKEN_ROOM = 4

    private fun name(config: LlmConfig) =
      when (config.provider) {
        AiProvider.OLLAMA -> "Ollama"
        AiProvider.OPENAI -> "OpenAI"
        AiProvider.ANTHROPIC -> "Anthropic"
        AiProvider.GEMINI -> "Gemini"
        AiProvider.OPENROUTER -> "OpenRouter"
        AiProvider.NVIDIA -> "NVIDIA"
        AiProvider.GROQ -> "Groq"
        AiProvider.HUGGINGFACE -> "Hugging Face"
        AiProvider.CUSTOM -> "The AI server"
      }

    /** Some providers echo part of a wrong key back; never let it reach a log. */
    private fun hide(config: LlmConfig, text: String): String {
      val key = config.apiKey ?: return text
      var out = text.replace(key, "***")
      if (key.length > 8) out = out.replace(key.take(8), "***").replace(key.takeLast(4), "***")
      return out
    }

    /** A client on OkHttp with the timeout plugin the per-request settings need. */
    fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) { install(HttpTimeout) }
  }
}
