package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings

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
 * A CLI source (OpenCode, agy) is out of quota or resting after a limit: like a rate limit, it is
 * not a fault of the source and does not count against its health.
 */
class ModelRestingException(message: String, cause: Throwable? = null) :
  LlmException(message, cause)

/** The provider refused the key (HTTP 401 or 403): it is wrong, revoked or expired. */
class AuthRefusedException(message: String) : LlmException(message)
