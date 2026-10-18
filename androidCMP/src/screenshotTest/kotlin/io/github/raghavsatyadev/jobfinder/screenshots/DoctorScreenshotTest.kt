package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.pairing.ConnectionBanner
import io.github.raghavsatyadev.jobfinder.ui.pairing.ConnectionDoctorContent
import io.github.raghavsatyadev.jobfinder.ui.pairing.DoctorUiState
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckKind
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckState
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorProblem

/** The firewall case: found on the network, but the port times out. */
@PreviewTest
@ScreenPreviews
@Composable
fun DoctorFirewallScreenshot() {
  Doctor(
    DoctorUiState(
      computer = "DESK-PC",
      checks =
        listOf(
          DoctorCheck(DoctorCheckKind.WIFI, DoctorCheckState.PASSED),
          DoctorCheck(DoctorCheckKind.SAME_NETWORK, DoctorCheckState.PASSED),
          DoctorCheck(DoctorCheckKind.FOUND, DoctorCheckState.PASSED),
          DoctorCheck(
            DoctorCheckKind.PORT,
            DoctorCheckState.FAILED,
            DoctorProblem.PORT_TIMEOUT,
          ),
          DoctorCheck(DoctorCheckKind.APP, DoctorCheckState.SKIPPED),
          DoctorCheck(DoctorCheckKind.TOKEN, DoctorCheckState.SKIPPED),
        ),
    )
  )
}

/** A renewed pairing code: everything answers but the token. */
@PreviewTest
@StatePreviews
@Composable
fun DoctorTokenScreenshot() {
  Doctor(
    DoctorUiState(
      computer = "DESK-PC",
      checks =
        DoctorCheckKind.entries.map {
          if (it == DoctorCheckKind.TOKEN) {
            DoctorCheck(it, DoctorCheckState.FAILED, DoctorProblem.TOKEN_REJECTED)
          } else {
            DoctorCheck(it, DoctorCheckState.PASSED)
          }
        },
    )
  )
}

@PreviewTest
@StatePreviews
@Composable
fun ConnectionBannerScreenshot() {
  ScreenshotHost { ConnectionBanner("DESK-PC", onFix = {}) }
}

@Composable
private fun Doctor(state: DoctorUiState) {
  ScreenshotHost { ConnectionDoctorContent(state, onEvent = {}) }
}
