package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_4_hours
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.SavedComputer
import io.github.raghavsatyadev.jobfinder.ui.settings.ScanFrequency
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsContent
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsSection
import io.github.raghavsatyadev.jobfinder.ui.settings.SettingsUiState

// The ViewModel loads in a coroutine that a single preview frame does not wait for, so these
// render the stateless screen with the state the ViewModel reaches (see SettingsViewModelTest).

/** Desktop look (DesktopFeatures bound): pairing QR, port and sign-in browser. */
@PreviewTest
@ScreenPreviews
@Composable
fun SettingsDesktopScreenshot() {
  Settings(LOADED.copy(desktop = true, pairing = PAIRED), SettingsSection.PAIRING)
}

/** Android look (PhonePairing bound): two paired computers, the active one connected. */
@PreviewTest
@ScreenPreviews
@Composable
fun SettingsAndroidScreenshot() {
  Settings(
    LOADED.copy(phone = true, computers = COMPUTERS, phoneConnected = true),
    SettingsSection.COMPUTERS,
  )
}

/** Android with the computer unreachable, and the forget dialog open. */
@PreviewTest
@StatePreviews
@Composable
fun SettingsAndroidOfflineScreenshot() {
  Settings(
    LOADED.copy(phone = true, computers = COMPUTERS, confirmForget = COMPUTERS.first()),
    SettingsSection.COMPUTERS,
  )
}

/** Android with the computer out of reach: the scanner settings show read-only with a note. */
@PreviewTest
@StatePreviews
@Composable
fun SettingsAndroidReadOnlyScreenshot() {
  CompositionLocalProvider(LocalComputerOffline provides true) {
    Settings(LOADED.copy(phone = true, computers = COMPUTERS), SettingsSection.SCANNING)
  }
}

/** Desktop with the phone server off (no LAN address) and an invalid port typed. */
@PreviewTest
@StatePreviews
@Composable
fun SettingsServerOffScreenshot() {
  Settings(LOADED.copy(desktop = true, pairing = null, portInput = "80"), SettingsSection.PAIRING)
}

/** A frequency more often than every 12 hours: the warning under the picker and the dialog. */
@PreviewTest
@StatePreviews
@Composable
fun SettingsRiskyFrequencyScreenshot() {
  Settings(
    LOADED.copy(
      scanner = ScannerSettings(cronRule = "0 */4 * * *"),
      confirmFrequency = ScanFrequency("0 */4 * * *", Res.string.settings_frequency_4_hours),
    ),
    SettingsSection.SCANNING,
  )
}

/** As the app opens it: the section menu under the search. */
@PreviewTest
@ScreenPreviews
@Composable
fun SettingsClosedScreenshot() {
  Settings(LOADED.copy(desktop = true, pairing = PAIRED))
}

/** A search keeps the sections it finds and hides the rest. */
@PreviewTest
@StatePreviews
@Composable
fun SettingsSearchScreenshot() {
  Settings(LOADED.copy(desktop = true, pairing = PAIRED), query = "port")
}

/** A search that finds nothing. */
@PreviewTest
@StatePreviews
@Composable
fun SettingsSearchNoneScreenshot() {
  Settings(LOADED.copy(desktop = true, pairing = PAIRED), query = "zebra")
}

@PreviewTest
@StatePreviews
@Composable
fun SettingsLoadingScreenshot() {
  Settings(SettingsUiState(loading = true))
}

@PreviewTest
@StatePreviews
@Composable
fun SettingsErrorScreenshot() {
  Settings(SettingsUiState(loading = false, loadFailed = true))
}

@Composable
private fun Settings(
  state: SettingsUiState,
  section: SettingsSection? = null,
  query: String = "",
) {
  ScreenshotHost {
    SettingsContent(
      state,
      onEvent = {},
      snackbar = remember { SnackbarHostState() },
      section = section,
      onBack = if (section != null) ({}) else null,
      initialQuery = query,
    )
  }
}

private val LOADED =
  SettingsUiState(
    loading = false,
    scanner = ScannerSettings(),
    portInput = ServerSettings.DEFAULT_PORT.toString(),
    savedPort = ServerSettings.DEFAULT_PORT,
  )

private val PAIRED =
  PairingInfo(
    baseUrl = "http://192.168.1.20:47821",
    pairingCode =
      "{\"url\":\"http://192.168.1.20:47821\",\"token\":\"q8Zt3nV1xY0aB7cD9eF2gH4jK6mN8pR1\",\"version\":1}",
  )

private val COMPUTERS =
  listOf(
    SavedComputer("id-1", "DESK-PC", "192.168.1.20:47821", active = true),
    SavedComputer("id-2", "LAPTOP", "192.168.1.41:47821", active = false),
  )
