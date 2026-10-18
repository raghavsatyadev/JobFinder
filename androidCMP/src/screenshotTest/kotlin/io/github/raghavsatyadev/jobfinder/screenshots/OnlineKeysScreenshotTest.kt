package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.AgyChoice
import io.github.raghavsatyadev.jobfinder.shared.model.AgyModels
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiKeyUsage
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.OpenCodeChoice
import io.github.raghavsatyadev.jobfinder.ui.platform.AgyStatus
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeysState
import io.github.raghavsatyadev.jobfinder.ui.platform.OpenCodeStatus
import io.github.raghavsatyadev.jobfinder.ui.settings.KeyNotice
import io.github.raghavsatyadev.jobfinder.ui.settings.OnlineKeysContent
import io.github.raghavsatyadev.jobfinder.ui.settings.OnlineKeysUiState

/** Settings → Free online AI with no keys yet: the free tiers, most free use first. */
@PreviewTest
@StatePreviews
@Composable
fun OnlineKeysEmptyScreenshot() {
  Keys(OnlineKeysUiState(loading = false, draft = "gsk_example"))
}

/** Two keys in use, ATS moved online with its resume warning, a key just added. */
@PreviewTest
@StatePreviews
@Composable
fun OnlineKeysInUseScreenshot() {
  // One key with weeks left, one about to expire.
  val nvidia =
    AiKey("n", AiProvider.NVIDIA, requestsPerMinute = 40, expiresAt = NOW + 61 * AiKey.DAY_MS)
  val groq =
    AiKey(
      "g",
      AiProvider.GROQ,
      requestsPerMinute = 30,
      requestsPerDay = 1000,
      tokensPerDay = 200_000,
      expiresAt = NOW + 5 * AiKey.DAY_MS,
    )
  Keys(
    OnlineKeysUiState(
      loading = false,
      pool =
        AiKeysState(
          keys = listOf(groq, nvidia),
          usage = listOf(AiKeyUsage("n", 212, 640_000), AiKeyUsage("g", 87, 151_500)),
          routing =
            AiRouting(
              mapOf(AiTask.ATS to listOf(AiRoute("g", "openai/gpt-oss-120b"), AiRoute.LOCAL))
            ),
          now = NOW,
        ),
      notice = KeyNotice.Added(groq),
    )
  )
}

/** No keys, the OpenCode CLI installed and on: its model chips and its chip in every task. */
@PreviewTest
@StatePreviews
@Composable
fun OnlineKeysOpenCodeScreenshot() {
  Keys(
    OnlineKeysUiState(
      loading = false,
      pool =
        AiKeysState(
          routing = AiRouting(openCode = OpenCodeChoice(enabled = true)),
          now = NOW,
          openCode = OpenCodeStatus(installed = true, version = "opencode v2.0.31"),
        ),
    )
  )
}

/** No keys, the Antigravity CLI installed and on at the medium level; OpenCode not installed. */
@PreviewTest
@StatePreviews
@Composable
fun OnlineKeysAgyScreenshot() {
  Keys(
    OnlineKeysUiState(
      loading = false,
      pool =
        AiKeysState(
          routing = AiRouting(agy = AgyChoice(enabled = true, model = AgyModels.LEVELS[1])),
          now = NOW,
          agy = AgyStatus(installed = true, version = "1.4.0"),
        ),
    )
  )
}

@Composable
private fun Keys(state: OnlineKeysUiState) {
  ScreenshotHost {
    Column(Modifier.verticalScroll(rememberScrollState())) {
      OnlineKeysContent(state, onEvent = {})
    }
  }
}

private const val NOW = 1_792_000_000_000L
