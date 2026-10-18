package io.github.raghavsatyadev.jobfinder.ui.platform

import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings

/** The AI choice as saved, and which providers have a key. Never the keys themselves. */
data class AiChoiceState(
  val settings: AiSettings,
  val keysSaved: Set<AiProvider>,
  /** `false` where keys can't be kept safely: only the local model can be chosen. */
  val keysAvailable: Boolean,
)

/** What the "Test" button found. */
sealed interface AiTestResult {
  data object Ok : AiTestResult

  data object KeyMissing : AiTestResult

  data class Failed(val reason: String) : AiTestResult
}

/**
 * The model behind the AI filter, the custom parser and ATS scores. Only the desktop binds it: API
 * keys never leave the computer, so the phone has no AI settings.
 */
interface AiChoice {
  suspend fun load(): AiChoiceState

  /** Saves [settings]. [key]: `null` keeps the saved one, blank removes it. */
  suspend fun save(settings: AiSettings, key: String?): AiChoiceState

  /** Asks the model one small question, with [key] or the saved one. */
  suspend fun test(settings: AiSettings, key: String?): AiTestResult
}
