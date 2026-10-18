package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** The AI filter: the user's matching prompt and companies that are always excluded. */
@Serializable
data class AdvancedMatchingConfig(
  /** The old `chatgpt_prompt`. Sent to the local LLM as is. */
  val prompt: String = "",
  /** Compared with the job company name in lower case, before the LLM runs. */
  val blacklistedCompanies: List<String> = emptyList(),
) {
  companion object {
    /** The UI limit on [prompt]. */
    const val MAX_PROMPT_LENGTH = 5000
  }
}
