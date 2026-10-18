package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptWarning
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.filters.AiPromptsContent
import io.github.raghavsatyadev.jobfinder.ui.filters.AiPromptsUiState
import io.github.raghavsatyadev.jobfinder.ui.filters.PromptConfirm

/** Desktop: the matching instructions open with an unsaved edit, the parsing one changed. */
@PreviewTest
@ScreenPreviews
@Composable
fun AiPromptsEditingScreenshot() {
  Prompts(
    AiPromptsUiState(
      prompts = PROMPTS,
      open = PromptId.MATCHING,
      drafts =
        mapOf(PromptId.MATCHING to "Keep only remote Android jobs.\nNever exclude for salary."),
    )
  )
}

/** Desktop: saving parsing instructions without their field words asks twice. */
@PreviewTest
@StatePreviews
@Composable
fun AiPromptsStrongWarningScreenshot() {
  Prompts(
    AiPromptsUiState(
      prompts = PROMPTS,
      confirm =
        PromptConfirm(
          PromptId.PARSE_LIST,
          listOf(PromptWarning.MissingTerms(listOf("externalUrl")), PromptWarning.DrivesParsing),
        ),
    )
  )
}

/** Phone offline: still editable, with the note that edits are sent later. */
@PreviewTest
@StatePreviews
@Composable
fun AiPromptsPhoneScreenshot() {
  CompositionLocalProvider(LocalComputerOffline provides true) {
    Prompts(AiPromptsUiState(prompts = PROMPTS, open = PromptId.ATS_REVIEW))
  }
}

@Composable
private fun Prompts(state: AiPromptsUiState) {
  ScreenshotHost {
    Box(Modifier.verticalScroll(rememberScrollState())) { AiPromptsContent(state, onEvent = {}) }
  }
}

private val PROMPTS =
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
    AiPrompt(
      PromptId.PARSE_DESCRIPTION,
      "Copy the description and a summary.",
      "Copy the description and a summary.",
    ),
    AiPrompt(
      PromptId.ATS_REVIEW,
      "Score keywords, then the fixes.",
      "Score keywords, then the fixes.",
    ),
  )
