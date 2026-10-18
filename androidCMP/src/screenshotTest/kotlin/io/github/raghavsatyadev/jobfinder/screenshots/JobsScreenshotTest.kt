package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import io.github.raghavsatyadev.jobfinder.ui.jobs.DescriptionText
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailContent
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailPlaceholder
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobFilterSheetContent
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsListContent
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsListUiState
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsPreviewData

/**
 * The jobs list-detail layout as the app lays it out: list and detail side by side from 840 dp, the
 * list alone below that. The screens get fixed states: a preview draws one frame, before a
 * ViewModel could load anything.
 */
@Composable
private fun JobsListDetail(list: JobsListUiState, selectedJobId: Long?) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    if (maxWidth >= 840.dp) {
      Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.45f).fillMaxHeight()) {
          JobsListContent(list, selectedJobId, JobsPreviewData.NOW)
        }
        VerticalDivider()
        Box(Modifier.weight(0.55f).fillMaxHeight()) {
          if (selectedJobId == null) JobDetailPlaceholder()
          else JobDetailContent(JobsPreviewData.detail(selectedJobId), JobsPreviewData.NOW)
        }
      }
    } else {
      JobsListContent(list, selectedJobId, JobsPreviewData.NOW)
    }
  }
}

@PreviewTest
@ScreenPreviews
@Composable
fun JobsListScreenshot() {
  ScreenshotHost { JobsListDetail(JobsPreviewData.list(), selectedJobId = 1) }
}

/** An applied job with a label and a note. */
@PreviewTest
@ScreenPreviews
@Composable
fun JobDetailScreenshot() {
  ScreenshotHost { JobDetailContent(JobsPreviewData.detail(4), JobsPreviewData.NOW) }
}

/** A job the AI filter skipped, with its reason. */
@PreviewTest
@StatePreviews
@Composable
fun JobDetailExcludedScreenshot() {
  ScreenshotHost { JobDetailContent(JobsPreviewData.detail(6), JobsPreviewData.NOW) }
}

@PreviewTest
@StatePreviews
@Composable
fun JobsEmptyScreenshot() {
  ScreenshotHost { JobsListDetail(JobsPreviewData.empty(), selectedJobId = null) }
}

@PreviewTest
@StatePreviews
@Composable
fun JobsLoadingScreenshot() {
  ScreenshotHost { JobsListDetail(JobsPreviewData.loading(), selectedJobId = null) }
}

@PreviewTest
@StatePreviews
@Composable
fun JobsErrorScreenshot() {
  ScreenshotHost { JobsListDetail(JobsPreviewData.error(), selectedJobId = null) }
}

/** A scan of every search is running, with its progress over the list. */
@PreviewTest
@StatePreviews
@Composable
fun JobsScanningScreenshot() {
  val scan = ScanState(scanning = true, startedAt = SampleData.NOW, linksTotal = 3, linksDone = 1)
  ScreenshotHost { JobsListDetail(JobsPreviewData.list(scan = scan), selectedJobId = null) }
}

/** The filter sheet, drawn in place (a real modal sheet needs a window the renderer lacks). */
@PreviewTest
@ScreenPreviews
@Composable
fun JobsFilterSheetScreenshot() {
  ScreenshotHost {
    Box(Modifier.fillMaxSize()) {
      JobsListContent(JobsPreviewData.list(), selectedJobId = null, now = JobsPreviewData.NOW)
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
      Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth(),
      ) {
        JobFilterSheetContent(
          filter =
            JobFilter(siteIds = listOf(1), workModes = listOf(WorkMode.REMOTE, WorkMode.HYBRID)),
          sites = SampleData.sites,
          links = SampleData.links,
          onApply = {},
          modifier = Modifier.padding(top = 24.dp),
        )
      }
    }
  }
}

/** A board's run-together description, tidied: paragraphs apart, fields one per line, headings. */
@PreviewTest
@StatePreviews
@Composable
fun JobDescriptionTidyScreenshot() {
  ScreenshotHost {
    Box(Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
      DescriptionText(MESSY_DESCRIPTION)
    }
  }
}

private val MESSY_DESCRIPTION =
  """L3Harris is dedicated to recruiting and developing high-performing talent who are passionate about what they do.
L3Harris is the Trusted Disruptor in defense tech.
***Job Title: UK Customer Engagement Lead - Technical Job Location: Picadilly Circus, London*** Job **Description:**
L3Harris Trenchant's expertise is the by-product of the acquisition of two highly-regarded information security businesses.
The UK Customer Engagement Lead is responsible for the strategic direction, performance and growth of Trenchant in the UK.
**Essential Functions:**

• In line with the corporate direction, execute UK strategic plans to meet Trenchant goals.
• Responsible for the financial performance of Trenchant in the UK.
• Develop deep and professional relations with all UK customers.

**Qualifications:**
- Bachelor's degree or equivalent experience in software engineering.
- 3+ years of management experience."""
