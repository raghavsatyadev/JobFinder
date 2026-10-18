package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.Leverage
import io.github.raghavsatyadev.jobfinder.shared.model.NegotiationTips
import io.github.raghavsatyadev.jobfinder.shared.model.TipsState
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import io.github.raghavsatyadev.jobfinder.ui.tips.TipsSection

/** Tips written, and a job with none yet. */
@PreviewTest
@StatePreviews
@Composable
fun TipsScreenshot() {
  ScreenshotHost {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
      TipsSection(
        TipsStatus(
          TipsState.READY,
          NegotiationTips(
            Leverage.MEDIUM,
            "A strong Kotlin and Compose match, but the role is in London and you are not.",
            listOf(
              "Research London pay for senior Android roles on Levels.fyi before the first call.",
              "Settle the location question first: remote from India or a relocation package.",
              "Stress your Kotlin and Jetpack Compose depth: they are the role's core skills.",
            ),
            createdAt = 0,
          ),
        ),
        loadFailed = false,
        onWrite = {},
      )
      TipsSection(TipsStatus(TipsState.NONE), loadFailed = false, onWrite = {})
    }
  }
}
