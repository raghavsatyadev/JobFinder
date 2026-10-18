package io.github.raghavsatyadev.jobfinder.ui.filters

import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import kotlinx.collections.immutable.toImmutableList

/** Fixed states of the AI filters screen for previews and screenshot tests. */
object AiFiltersPreviewData {
  /** The sample config with unsaved edits: another company, and a rejected duplicate. */
  fun edited(
    prompt: String = SampleData.matching.prompt + " Prefer product companies.",
    companyInput: String = "initech",
    companyError: CompanyError? = CompanyError.Duplicate("Initech"),
    saving: Boolean = false,
  ): AiFiltersUiState =
    AiFiltersUiState(
      saved = SampleData.matching,
      prompt = prompt,
      companies =
        (SampleData.matching.blacklistedCompanies + "Globex" + "Umbrella Corporation")
          .toImmutableList(),
      companyInput = companyInput,
      companyError = companyError,
      loading = false,
      saving = saving,
    )

  /** As loaded: nothing changed. */
  fun loaded(): AiFiltersUiState =
    AiFiltersUiState(
      saved = SampleData.matching,
      prompt = SampleData.matching.prompt,
      companies = SampleData.matching.blacklistedCompanies.toImmutableList(),
      loading = false,
    )

  /** Nothing set up yet: an empty prompt and no companies. */
  fun empty(): AiFiltersUiState =
    AiFiltersUiState(saved = AdvancedMatchingConfig(), loading = false)

  fun loading(): AiFiltersUiState = AiFiltersUiState()

  fun error(): AiFiltersUiState = AiFiltersUiState(loading = false, loadFailed = true)

  /** A prompt over the limit, which cannot be saved. */
  fun tooLong(): AiFiltersUiState =
    edited(
      prompt = "Skip crypto. ".repeat(AdvancedMatchingConfig.MAX_PROMPT_LENGTH / 12 + 1),
      companyInput = "",
      companyError = null,
    )
}
