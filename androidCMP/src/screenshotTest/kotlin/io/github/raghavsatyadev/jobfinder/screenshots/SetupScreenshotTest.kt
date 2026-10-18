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
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeSetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.help.HelpScreen
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupPage
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupProblem
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStep
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepKind
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepState
import io.github.raghavsatyadev.jobfinder.ui.setup.AssistantHelpCard
import io.github.raghavsatyadev.jobfinder.ui.setup.AssistantHelpState
import io.github.raghavsatyadev.jobfinder.ui.setup.AssistantResult
import io.github.raghavsatyadev.jobfinder.ui.setup.AssistantTask
import io.github.raghavsatyadev.jobfinder.ui.setup.SetupContent
import io.github.raghavsatyadev.jobfinder.ui.setup.SetupUiState
import io.github.raghavsatyadev.jobfinder.ui.setup.assistantTasks
import org.koin.dsl.module

/** A new computer: the firewall to open, Ollama to install, the rest for the user. */
@PreviewTest
@ScreenPreviews
@Composable
fun SetupFirstRunScreenshot() {
  Setup(
    SetupUiState(
      steps =
        listOf(
          SetupStep(SetupStepKind.SERVER, SetupStepState.DONE),
          SetupStep(SetupStepKind.NETWORK, SetupStepState.DONE),
          SetupStep(SetupStepKind.FIREWALL, SetupStepState.TODO, SetupProblem.NO_FIREWALL_RULE),
          SetupStep(SetupStepKind.OLLAMA, SetupStepState.TODO, SetupProblem.OLLAMA_MISSING),
          SetupStep(SetupStepKind.MODEL, SetupStepState.TODO),
          SetupStep(SetupStepKind.SEARCHES, SetupStepState.TODO),
          SetupStep(SetupStepKind.SIGN_IN, SetupStepState.TODO, SetupProblem.NEEDS_SEARCH),
          SetupStep(SetupStepKind.PHONE, SetupStepState.TODO),
          SetupStep(SetupStepKind.EXTENSION, SetupStepState.TODO),
        ),
      computerName = "DESK-PC",
      modelBytes = 22_000_000_000L,
      profile = PcProfile(ramGb = 63.8, gpuName = "RTX 5070 Ti", vramGb = 15.9, freeDiskGb = 300.0),
      pairing = PairingInfo("http://192.168.1.20:47821", "{\"url\":\"http://192.168.1.20:47821\"}"),
    )
  )
}

/** The searches page: a search saved, sign-in to do, the extension connected. */
@PreviewTest
@StatePreviews
@Composable
fun SetupSearchesPageScreenshot() {
  Setup(
    SetupUiState(
      steps =
        SetupStepKind.entries.map {
          when (it) {
            SetupStepKind.SIGN_IN -> SetupStep(it, SetupStepState.TODO)
            else -> SetupStep(it, SetupStepState.DONE)
          }
        },
      computerName = "DESK-PC",
      page = SetupPage.SEARCHES,
      canCopySignIns = true,
    )
  )
}

/** The phone page: the firewall to open and a phone to pair, with the QR code. */
@PreviewTest
@StatePreviews
@Composable
fun SetupPhonePageScreenshot() {
  Setup(
    SetupUiState(
      steps =
        SetupStepKind.entries.map {
          when (it) {
            SetupStepKind.FIREWALL ->
              SetupStep(it, SetupStepState.TODO, SetupProblem.NO_FIREWALL_RULE)
            SetupStepKind.PHONE -> SetupStep(it, SetupStepState.TODO)
            else -> SetupStep(it, SetupStepState.DONE)
          }
        },
      computerName = "DESK-PC",
      page = SetupPage.PHONE,
      pairing = PairingInfo("http://192.168.1.20:47821", "{\"url\":\"http://192.168.1.20:47821\"}"),
    )
  )
}

/** Set up everything at work: the model halfway down, a declined permission prompt. */
@PreviewTest
@StatePreviews
@Composable
fun SetupWorkingScreenshot() {
  Setup(
    SetupUiState(
      steps =
        SetupStepKind.entries.map {
          when (it) {
            SetupStepKind.FIREWALL ->
              SetupStep(it, SetupStepState.FAILED, SetupProblem.PERMISSION_DECLINED)
            SetupStepKind.MODEL ->
              SetupStep(it, SetupStepState.WORKING, null, 9_400_000_000L, 22_000_000_000L)
            else -> SetupStep(it, SetupStepState.DONE)
          }
        },
      computerName = "DESK-PC",
      modelBytes = 22_000_000_000L,
    )
  )
}

@PreviewTest
@StatePreviews
@Composable
fun SetupAllDoneScreenshot() {
  Setup(
    SetupUiState(
      steps = SetupStepKind.entries.map { SetupStep(it, SetupStepState.DONE) },
      computerName = "DESK-PC",
    )
  )
}

/** A laptop too small for a good model: online first, with the free keys to get. */
@PreviewTest
@StatePreviews
@Composable
fun SetupOnlineFirstScreenshot() {
  Setup(
    SetupUiState(
      steps = SetupStepKind.entries.map { SetupStep(it, SetupStepState.DONE) },
      computerName = "LAPTOP",
      profile = PcProfile(ramGb = 7.8, gpuName = null, vramGb = 0.0, freeDiskGb = 80.0),
    )
  )
}

/** Help on the desktop, with the card that opens the setup. */
@PreviewTest
@StatePreviews
@Composable
fun HelpDesktopScreenshot() {
  ScreenshotHost(
    extraModules = listOf(module { single<SetupAssistant> { FakeSetupAssistant() } })
  ) {
    HelpScreen()
  }
}

/** The AI-assistant cards on their own: job boards (answer used), filters and resume. */
@PreviewTest
@StatePreviews
@Composable
fun SetupAssistantHelpScreenshot() {
  ScreenshotHost {
    Column(
      Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      AssistantTask.entries.forEach { AssistantHelpCard(it, Helpers, { _, _ -> }, {}) }
    }
  }
}

@Composable
private fun Setup(state: SetupUiState) {
  ScreenshotHost {
    SetupContent(
      state,
      onEvent = {},
      helpers = { page ->
        page.assistantTasks.forEach { AssistantHelpCard(it, Helpers, { _, _ -> }, {}) }
      },
    )
  }
}

/** The assistant cards with their requests, and one answer already used. */
private val Helpers =
  AssistantHelpState(
    requests = AssistantTask.entries.associateWith { "Request" },
    results =
      mapOf(AssistantTask.JOB_BOARDS to AssistantResult.SearchesAdded(added = 12, failed = 0)),
  )
