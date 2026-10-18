package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.navigation.AppShell

@PreviewTest
@ScreenPreviews
@Composable
fun AppShellScreenshot() {
  ScreenshotHost { AppShell() }
}
