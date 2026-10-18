package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import io.github.raghavsatyadev.jobfinder.ui.insights.InsightsSection

/** The job detail's company section: both sites' ratings and the nearest role's salary. */
@PreviewTest
@StatePreviews
@Composable
fun InsightsScreenshot() {
  ScreenshotHost {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
      InsightsSection(InsightsStatus(InsightsState.READY, SampleData.insights), false) {}
    }
  }
}

/** Waiting for the browser, and a read the sites blocked. */
@PreviewTest
@StatePreviews
@Composable
fun InsightsWaitingScreenshot() {
  ScreenshotHost {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
      InsightsSection(InsightsStatus(InsightsState.QUEUED), false) {}
      InsightsSection(InsightsStatus(InsightsState.FAILED), false) {}
    }
  }
}
