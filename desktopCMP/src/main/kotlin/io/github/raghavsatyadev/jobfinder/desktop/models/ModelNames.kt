package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource

/**
 * How an added model is registered: its own name with the app's prefix, a 16k context (the matching
 * prompt and a long job page need it) and the sampling the model's makers ask for.
 */
object ModelNames {
  const val PREFIX = "jobfinder-"
  const val CONTEXT_SIZE = 16_384
  /** Qwen recommends it for its hybrid (thinking) Qwen3 models; without it they loop. */
  const val QWEN3_PRESENCE_PENALTY = 1.5

  /** What Ollama pulls for [source]; `null` for a link, which is downloaded by the app. */
  fun pullName(source: ModelSource): String? =
    when (source) {
      is ModelSource.Library -> source.name.trim()
      is ModelSource.HuggingFace -> "hf.co/${source.repo.trim()}:${source.quant.trim()}"
      is ModelSource.Link -> null
    }

  /** The name the app registers [source] as, like `jobfinder-qwen3-8b-q4_k_m`. */
  fun appName(source: ModelSource): String {
    val base =
      when (source) {
        is ModelSource.Library -> source.name
        is ModelSource.HuggingFace ->
          source.repo.substringAfterLast('/').removeSuffix("-GGUF").removeSuffix("-gguf") +
            "-" +
            source.quant
        is ModelSource.Link ->
          source.url.substringBefore('?').substringAfterLast('/').removeSuffix(".gguf")
      }
    val slug =
      base.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.').take(MAX_SLUG).ifEmpty {
        "model"
      }
    return PREFIX + slug
  }

  /** The parameters [source] is registered with. */
  fun parameters(source: ModelSource): Map<String, Number> = buildMap {
    put("num_ctx", CONTEXT_SIZE)
    if (isQwen3Hybrid(appName(source))) put("presence_penalty", QWEN3_PRESENCE_PENALTY)
  }

  /** Qwen3 and 3.x models that think: not the Instruct and Coder ones. */
  fun isQwen3Hybrid(name: String): Boolean {
    val n = name.lowercase()
    return Regex("""qwen3(\.\d+)?[^a-z]""").containsMatchIn("$n-") &&
      "instruct" !in n &&
      "coder" !in n
  }

  private const val MAX_SLUG = 60
}
