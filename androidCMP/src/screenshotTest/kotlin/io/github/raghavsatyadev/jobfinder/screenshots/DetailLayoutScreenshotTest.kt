package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.DetailSection
import io.github.raghavsatyadev.jobfinder.ui.settings.DetailLayoutContent
import io.github.raghavsatyadev.jobfinder.ui.settings.DetailLayoutUiState

/** Settings: the description moved first, ghost check and tips hidden, with Reset. */
@PreviewTest
@StatePreviews
@Composable
fun DetailLayoutScreenshot() {
  ScreenshotHost {
    Box(Modifier.verticalScroll(rememberScrollState())) {
      DetailLayoutContent(
        DetailLayoutUiState(
          layout =
            DetailLayout(
              order =
                listOf(
                  DetailSection.DESCRIPTION,
                  DetailSection.ATS,
                  DetailSection.LABELS,
                  DetailSection.COMPANY,
                  DetailSection.GHOST,
                  DetailSection.TIPS,
                  DetailSection.NOTES,
                ),
              hidden = setOf(DetailSection.GHOST, DetailSection.TIPS),
            )
        ),
        onEvent = {},
      )
    }
  }
}
