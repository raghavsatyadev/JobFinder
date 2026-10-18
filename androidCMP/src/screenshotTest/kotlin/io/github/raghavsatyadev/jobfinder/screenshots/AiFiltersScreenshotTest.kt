package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeBackend
import io.github.raghavsatyadev.jobfinder.ui.filters.AiFiltersContent
import io.github.raghavsatyadev.jobfinder.ui.filters.AiFiltersPreviewData
import io.github.raghavsatyadev.jobfinder.ui.filters.AiFiltersScreen
import io.github.raghavsatyadev.jobfinder.ui.filters.AiFiltersUiState
import io.github.raghavsatyadev.jobfinder.ui.filters.AiPromptsContent
import io.github.raghavsatyadev.jobfinder.ui.filters.AiPromptsUiState

// The ViewModel loads in a coroutine that a single preview frame does not wait for, so most of
// these render the stateless screen with the state the ViewModel reaches (see
// AiFiltersViewModelTest).

/** As saved: the prompt and one blacklisted company; nothing to save. */
@PreviewTest
@ScreenPreviews
@Composable
fun AiFiltersScreenshot() {
  Filters(AiFiltersPreviewData.loaded())
}

/** Desktop: the AI instructions under the filters, one open. */
@PreviewTest
@ScreenPreviews
@Composable
fun AiFiltersWithInstructionsScreenshot() {
  ScreenshotHost {
    AiFiltersContent(
      state = AiFiltersPreviewData.loaded(),
      onEvent = {},
      prompts = {
        AiPromptsContent(
          AiPromptsUiState(
            prompts =
              listOf(
                AiPrompt(
                  PromptId.MATCHING,
                  "Decide if the job should be excluded.",
                  "Decide if the job should be excluded.",
                ),
                AiPrompt(
                  PromptId.PARSE_LIST,
                  "Read title and companyName only.",
                  "Read externalUrl, title, companyName.",
                ),
                AiPrompt(PromptId.ATS_REVIEW, "Score the resume.", "Score the resume."),
              ),
            open = PromptId.MATCHING,
          ),
          onEvent = {},
        )
      },
    )
  }
}

/** Unsaved edits, and a duplicate company rejected inline. */
@PreviewTest
@ScreenPreviews
@Composable
fun AiFiltersEditedScreenshot() {
  Filters(AiFiltersPreviewData.edited())
}

@PreviewTest
@StatePreviews
@Composable
fun AiFiltersSavingScreenshot() {
  Filters(AiFiltersPreviewData.edited(companyInput = "", companyError = null, saving = true))
}

/** A prompt over 5000 characters cannot be saved. */
@PreviewTest
@StatePreviews
@Composable
fun AiFiltersTooLongScreenshot() {
  Filters(AiFiltersPreviewData.tooLong())
}

@PreviewTest
@StatePreviews
@Composable
fun AiFiltersEmptyScreenshot() {
  Filters(AiFiltersPreviewData.empty())
}

@PreviewTest
@StatePreviews
@Composable
fun AiFiltersLoadingScreenshot() {
  ScreenshotHost(FakeBackend().apply { latencyMillis = 60_000 }) { AiFiltersScreen() }
}

@PreviewTest
@StatePreviews
@Composable
fun AiFiltersErrorScreenshot() {
  ScreenshotHost(FakeBackend().apply { failWith = IllegalStateException("offline") }) {
    AiFiltersScreen()
  }
}

@Composable
private fun Filters(state: AiFiltersUiState) {
  ScreenshotHost { AiFiltersContent(state = state, onEvent = {}) }
}
