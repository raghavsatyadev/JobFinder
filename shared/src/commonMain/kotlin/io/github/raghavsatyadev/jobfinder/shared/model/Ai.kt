package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Who answers the app's AI questions (matching, the custom parser, ATS). [OLLAMA] runs on this
 * computer and is the default; the rest are online and use the user's own API key.
 */
@Serializable
enum class AiProvider(
  /** The OpenAI-compatible (or, for [ANTHROPIC], Messages API) base URL. */
  val defaultBaseUrl: String,
  /** A model to start from; the user can type any other. */
  val suggestedModel: String,
  val needsKey: Boolean,
) {
  @SerialName("ollama") OLLAMA("http://localhost:11434/v1", "jobfinder-qwen:latest", false),
  @SerialName("openai") OPENAI("https://api.openai.com/v1", "gpt-5-mini", true),
  @SerialName("anthropic") ANTHROPIC("https://api.anthropic.com/v1", "claude-haiku-5-5", true),
  @SerialName("gemini")
  GEMINI("https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true),
  @SerialName("openrouter")
  OPENROUTER("https://openrouter.ai/api/v1", "google/gemini-2.5-flash", true),
  @SerialName("nvidia")
  NVIDIA("https://integrate.api.nvidia.com/v1", "deepseek-ai/deepseek-v4.1-flash", true),
  @SerialName("groq") GROQ("https://api.groq.com/openai/v1", "openai/gpt-oss-120b", true),
  /** Any OpenAI-compatible server; the key is optional. */
  @SerialName("custom") CUSTOM("", "", false);

  val online: Boolean
    get() = this != OLLAMA

  companion object {
    /** Providers whose keys start the same way every time, longest prefix first. */
    private val PREFIXES =
      listOf(
        "sk-or-v1-" to OPENROUTER,
        "sk-ant-" to ANTHROPIC,
        "sk-proj-" to OPENAI,
        "nvapi-" to NVIDIA,
        "gsk_" to GROQ,
        "AIza" to GEMINI,
        "sk-" to OPENAI,
      )

    /** The provider [key] comes from, by its prefix; `null` when the key doesn't say. */
    fun detect(key: String): AiProvider? =
      key.trim().let { k -> PREFIXES.firstOrNull { k.startsWith(it.first) }?.second }
  }
}

/** The AI choice. Never holds the API key: that stays in the computer's protected store. */
@Serializable
data class AiSettings(
  val provider: AiProvider = AiProvider.OLLAMA,
  val model: String = AiProvider.OLLAMA.suggestedModel,
  /** `null` uses [AiProvider.defaultBaseUrl]. */
  val baseUrl: String? = null,
) {
  val endpoint: String
    get() = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: provider.defaultBaseUrl
}
