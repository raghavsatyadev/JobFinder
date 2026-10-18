package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailContent
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsPreviewData
import io.github.raghavsatyadev.jobfinder.ui.settings.ResumeContent
import io.github.raghavsatyadev.jobfinder.ui.settings.ResumeUiState

/** The job detail with its full ATS review: verdict, scores, keywords, formatting and fixes. */
@PreviewTest
@ScreenPreviews
@Composable
fun JobDetailAtsScreenshot() {
  ScreenshotHost { JobDetailContent(JobsPreviewData.detail(1), JobsPreviewData.NOW) }
}

@PreviewTest
@StatePreviews
@Composable
fun ResumeSettingsScreenshot() {
  ScreenshotHost {
    ResumeContent(ResumeUiState(resume = SampleData.resume, loading = false), SampleData.NOW) {}
  }
}

/** No resume yet, and a refused upload. */
@PreviewTest
@StatePreviews
@Composable
fun ResumeSettingsEmptyScreenshot() {
  ScreenshotHost {
    ResumeContent(
      ResumeUiState(loading = false, error = "Upload a PDF, DOCX or text file."),
      SampleData.NOW,
    ) {}
  }
}
