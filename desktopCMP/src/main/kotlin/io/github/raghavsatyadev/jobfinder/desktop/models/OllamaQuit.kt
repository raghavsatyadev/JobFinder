package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Frees the GPU when the app quits: Ollama keeps a model in memory for minutes after the last
 * request, so every loaded model is unloaded, except the coding assistant's [KEPT] ones.
 */
object OllamaQuit {
  private val log = LoggerFactory.getLogger("jobfinder.ollama")

  /** Model names (before the `:tag`) left loaded. */
  private val KEPT = setOf("nimble")

  /** Never holds the quit up for long, and does nothing when Ollama is not running. */
  fun unloadModels() {
    LlmClient.defaultHttpClient().use { http ->
      val ollama = HttpOllamaModels(http)
      runBlocking {
        withTimeoutOrNull(LIMIT) {
          val models = ollama.loaded().orEmpty().filter { it.substringBefore(':') !in KEPT }
          for (model in models) {
            try {
              ollama.unload(model)
              log.info("Unloaded {}", model)
            } catch (e: Exception) {
              if (e is CancellationException) throw e
              log.warn("Could not unload {}", model, e)
            }
          }
        } ?: log.warn("Unloading the models took over {}", LIMIT)
      }
    }
  }

  private val LIMIT = 6.seconds
}
