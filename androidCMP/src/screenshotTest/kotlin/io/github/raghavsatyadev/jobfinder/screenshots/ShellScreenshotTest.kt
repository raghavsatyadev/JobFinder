package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.navigation.AppShell
import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel

@PreviewTest
@ScreenPreviews
@Composable
fun AppShellScreenshot() {
  ScreenshotHost { AppShell() }
}

/** Settings: the menu alone on a phone, the menu beside an empty page on wider windows. */
@PreviewTest
@ScreenPreviews
@Composable
fun AppShellSettingsScreenshot() {
  ScreenshotHost { AppShell(initial = TopLevel.Settings) }
}
