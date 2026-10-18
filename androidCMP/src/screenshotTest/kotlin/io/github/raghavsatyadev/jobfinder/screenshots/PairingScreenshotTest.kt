package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.pairing.PairingContent
import io.github.raghavsatyadev.jobfinder.ui.pairing.PairingUiState
import io.github.raghavsatyadev.jobfinder.ui.pairing.PendingApproval
import io.github.raghavsatyadev.jobfinder.ui.platform.FoundComputer
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingError
import io.github.raghavsatyadev.jobfinder.ui.platform.SavedComputer

/** The first screen on an unpaired phone, with two computers found on the Wi-Fi. */
@PreviewTest
@ScreenPreviews
@Composable
fun PairingScreenshot() {
  Pairing(PairingUiState(found = FOUND))
}

/** Adding a second computer: a way back, and the other saved computer. */
@PreviewTest
@StatePreviews
@Composable
fun PairingAddingScreenshot() {
  Pairing(
    PairingUiState(
      found = FOUND,
      saved = listOf(SavedComputer("id-2", "LAPTOP", "192.168.1.41:47821", active = false)),
      canClose = true,
    )
  )
}

/** Waiting for Allow on the computer, with the code to compare. */
@PreviewTest
@ScreenPreviews
@Composable
fun PairingApprovalScreenshot() {
  Pairing(PairingUiState(busy = true, approval = PendingApproval("DESK-PC", "042917")))
}

@PreviewTest
@StatePreviews
@Composable
fun PairingDeniedScreenshot() {
  Pairing(PairingUiState(found = FOUND, error = PairingError.DENIED))
}

/** A pasted code being checked with the computer. */
@PreviewTest
@StatePreviews
@Composable
fun PairingBusyScreenshot() {
  Pairing(PairingUiState(code = CODE, busy = true))
}

@PreviewTest
@StatePreviews
@Composable
fun PairingUnreachableScreenshot() {
  Pairing(PairingUiState(code = CODE, error = PairingError.DESKTOP_DID_NOT_ANSWER))
}

@PreviewTest
@StatePreviews
@Composable
fun PairingNotOnLanScreenshot() {
  Pairing(PairingUiState(code = CODE, error = PairingError.NOT_A_LAN_ADDRESS))
}

@Composable
private fun Pairing(state: PairingUiState) {
  ScreenshotHost { PairingContent(state, onEvent = {}) }
}

private const val CODE =
  "{\"url\":\"http://192.168.1.20:47821\",\"token\":\"q8Zt3nV1xY0aB7cD9eF2gH4jK6mN8pR1\",\"version\":1}"

private val FOUND =
  listOf(
    FoundComputer("id-1", "DESK-PC", "192.168.1.20:47821", savedKey = null),
    FoundComputer("id-2", "LAPTOP", "192.168.1.41:47821", savedKey = "id-2"),
  )
