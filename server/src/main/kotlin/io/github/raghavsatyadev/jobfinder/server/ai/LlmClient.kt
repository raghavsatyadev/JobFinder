package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.Request
import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.SCHEMA_WORDS
import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.anthropicRequest
import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.authorize
import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.hide
import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.name
import io.github.raghavsatyadev.jobfinder.server.ai.LlmWire.openAiRequest
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * One chat client for every provider: the OpenAI-compatible chat API (Ollama, OpenAI, Gemini,
 * OpenRouter, NVIDIA, Groq, custom servers) and Anthropic's Messages API. The answer is always JSON
 * for the given schema.
 *
 * Each call names its [AiTask]; [pool] gives that task's models in order (online keys and the main
 * choice). Each task has its own lane on each online model ([lanes]): a lane takes one request,
 * then rests a short random gap, and meanwhile the task's next call goes to its next free model. So
 * the work of one kind runs on all its models at once. A key out of quota, rate-limited or failing
 * hands the call to the next one. Calls to the main choice share one [semaphore], so the local
 * model gets one request at a time and Ollama never swaps models mid-scan. Exactly one retry on
 * timeout, 5xx or connection error. [config] (the main choice) is read on every call, so Settings
 * apply at once. A target of the OpenCode CLI ([AiRoute.OPEN_CODE_ID]) goes to [openCode], which
 * keeps its own quota and fallbacks; one of the Antigravity CLI ([AiRoute.AGY_ID]) goes to [agy].
 */
class LlmClient(
  private val http: HttpClient,
  private val semaphore: Semaphore = Semaphore(1),
  private val pool: AiKeyPool? = null,
  private val openCode: OpenCodeClient? = null,
  private val agy: AgyClient? = null,
  /** Paces each task on each online model; `null` sends without pacing (tools). */
  private val lanes: AiLanes? = null,
  /** Hears which models answer and which keep failing. */
  private val health: AiHealth? = null,
  private val config: suspend () -> LlmConfig,
) {
  constructor(
    http: HttpClient,
    config: LlmConfig = LlmConfig(),
    semaphore: Semaphore = Semaphore(1),
  ) : this(http, semaphore, null, null, null, null, null, { config })

  /**
   * How many calls of [task] are worth running side by side: one per online model in its list (at
   * least one, at most [MAX_PARALLEL]). [chat] spreads them over the models.
   */
  suspend fun parallelism(task: AiTask): Int {
    val pool = pool ?: return 1
    if (lanes == null) return 1
    return pool.targets(task, config()).count { it.keyId != null }.coerceIn(1, MAX_PARALLEL)
  }

  /**
   * [task]'s models in order: the first whose lane is free takes the call. While an online model is
   * only busy (its lane rests), the call waits for one rather than go to the slow model on this
   * computer, for at most [LOCAL_AFTER_MS]. A model that fails hands the call to the next.
   */
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
    val work = task.name.lowercase()
    val begun = System.currentTimeMillis()
    val failed = HashSet<Int>()
    var last: Exception? = null
    while (true) {
      var waiting = false
      val patient = System.currentTimeMillis() - begun < LOCAL_AFTER_MS
      for ((index, target) in targets.withIndex()) {
        if (index in failed) continue
        // Online models only busy: wait for one of them, not the slow local model.
        if (target.keyId == null && waiting && patient) break
        val more = targets.indices.any { it > index && it !in failed }
        val started = System.nanoTime()
        // Which model answered, and how long each took: the way to see a slow or failing route.
        fun took() = (System.nanoTime() - started) / NANOS_PER_SECOND
        val name = "${target.config.provider.name.lowercase()} ${target.config.model}"
        try {
          when (val sent = send(task, target, system, user, schemaName, schema, maxTokens, !more)) {
            is Sent.Answer -> {
              log.info("{}: answered by {} in {} s", work, name, took())
              return sent.completion
            }
            Sent.Busy -> waiting = true
            Sent.Unavailable -> log.debug("{}: {} skipped (resting or out of quota)", work, name)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: RateLimitedException) {
          log.info("{}: {} busy after {} s", work, name, took())
          failed += index
          last = e
        } catch (e: LlmException) {
          log.info("{}: {} failed after {} s: {}", work, name, took(), e.message)
          failed += index
          if (!more && !waiting) throw e
          last = e
        }
      }
      if (!waiting) break
      lanes?.awaitChange(LANE_POLL_MS) ?: break
    }
    throw LlmException("Every model for $work is busy or out of quota", last)
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
   * One call on [target] only, in [task]'s lane, counted once against its quota, with no fallback
   * to other models: the caller does the work the usual way when it fails or the lane is busy.
   */
  suspend fun chatOn(
    target: LlmTarget,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
    task: AiTask = AiTask.MATCHING,
  ): ChatCompletion =
    try {
      (send(task, target, system, user, schemaName, schema, maxTokens, last = false)
          as? Sent.Answer)
        ?.completion ?: throw LlmException("${target.config.model} is busy or out of quota")
    } catch (e: RateLimitedException) {
      throw LlmException("${target.config.model} is out of quota for now", e)
    }

  /** What one model of a task's list did with a call. */
  private sealed interface Sent {
    class Answer(val completion: ChatCompletion) : Sent

    /** The task's lane on it is taken or resting: free again soon. */
    data object Busy : Sent

    /** Resting after errors, out of quota, or its CLI is off. */
    data object Unavailable : Sent
  }

  /**
   * One model of a task's list, in [task]'s lane on it. A rate limit rests its key and goes up as
   * [RateLimitedException]; other failures go up as [LlmException] and count against its health.
   */
  private suspend fun send(
    task: AiTask,
    target: LlmTarget,
    system: String,
    user: String,
    schemaName: String,
    schema: JsonObject,
    maxTokens: Int,
    last: Boolean,
  ): Sent {
    val keyId = target.keyId
    val openCodeCli = keyId == AiRoute.OPEN_CODE_ID
    val agyCli = keyId == AiRoute.AGY_ID
    val cli = openCodeCli || agyCli
    if (openCodeCli && openCode == null) return Sent.Unavailable
    if (agyCli && agy == null) return Sent.Unavailable
    val lanes = lanes?.takeIf { keyId != null }
    if (lanes != null && !lanes.tryEnter(task, target.routeId)) return Sent.Busy
    var used = false
    try {
      if (keyId != null && !cli && pool?.take(keyId) == false) return Sent.Unavailable
      used = true
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
      health?.succeeded(target.routeId)
      return Sent.Answer(answer)
    } catch (e: RateLimitedException) {
      keyId?.let { pool?.rest(it) }
      throw e
    } catch (e: RequestTooLargeException) {
      // This job is too long for the key's limit; the key itself is fine.
      throw e
    } catch (e: ModelRestingException) {
      // A CLI source at its limit is busy, not broken.
      throw e
    } catch (e: LlmException) {
      // A stuck or empty model would cost every following call the same wait.
      if (keyId != null && !cli) pool?.rest(keyId, AiKeyPool.FAILED_REST_MS)
      health?.failed(target.routeId, target.name, e)
      throw e
    } finally {
      lanes?.leave(task, target.routeId, used, target.requestsPerMinute)
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
          throw AuthRefusedException("${name(config)} refused the API key (HTTP $status)")
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

  companion object {
    private const val NANOS_PER_SECOND = 1_000_000_000L

    /** How long a call waits for a busy online model before it takes the local model instead. */
    private const val LOCAL_AFTER_MS = 5 * 60_000L

    /** The longest single wait for a lane, before the call looks at all its models again. */
    private const val LANE_POLL_MS = 15_000L

    /** At most this many calls of one task side by side. */
    private const val MAX_PARALLEL = 6

    /** A client on OkHttp with the timeout plugin the per-request settings need. */
    fun defaultHttpClient(): HttpClient = HttpClient(OkHttp) { install(HttpTimeout) }
  }
}
