package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.CareersCheck
import io.github.raghavsatyadev.jobfinder.shared.model.CareersHost
import io.github.raghavsatyadev.jobfinder.shared.model.CareersResult
import io.github.raghavsatyadev.jobfinder.shared.model.GhostCheck
import io.github.raghavsatyadev.jobfinder.shared.model.GhostSignal
import io.github.raghavsatyadev.jobfinder.shared.model.GhostStatus
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.ui.ghost.GhostSection

private const val NOW = 1_760_000_000_000L
private const val DAY = 86_400_000L

/** A reposted job missing from the company's Greenhouse board, and one found on Lever. */
@PreviewTest
@StatePreviews
@Composable
fun GhostCheckScreenshot() {
  ScreenshotHost {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
      GhostSection(
        GhostStatus(
          InsightsState.READY,
          GhostCheck(
            listOf(
              GhostSignal.Reposted(3, NOW - 52 * DAY),
              GhostSignal.OldPosting(52),
              GhostSignal.NoSalary,
              GhostSignal.NotOnCareersSite,
            ),
            CareersCheck(
              CareersResult.NOT_FOUND,
              CareersHost.GREENHOUSE,
              "https://job-boards.greenhouse.io/acme",
              openJobs = 42,
              checkedAt = NOW,
            ),
          ),
        ),
        loadFailed = false,
        now = NOW,
        onRetry = {},
      )
      GhostSection(
        GhostStatus(
          InsightsState.READY,
          GhostCheck(
            emptyList(),
            CareersCheck(
              CareersResult.FOUND,
              CareersHost.LEVER,
              "https://jobs.lever.co/northwind",
              "https://jobs.lever.co/northwind/1",
              matchedTitle = "Senior Android Engineer (Kotlin)",
              openJobs = 12,
              checkedAt = NOW,
            ),
          ),
        ),
        loadFailed = false,
        now = NOW,
        onRetry = {},
      )
      GhostSection(
        GhostStatus(InsightsState.QUEUED, GhostCheck(listOf(GhostSignal.VagueText))),
        loadFailed = false,
        now = NOW,
        onRetry = {},
      )
    }
  }
}
