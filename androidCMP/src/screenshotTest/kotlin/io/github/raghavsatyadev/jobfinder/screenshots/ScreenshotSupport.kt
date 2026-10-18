package io.github.raghavsatyadev.jobfinder.screenshots

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import io.github.raghavsatyadev.jobfinder.appModule
import io.github.raghavsatyadev.jobfinder.fakeRepositoriesModule
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeBackend
import io.github.raghavsatyadev.jobfinder.ui.theme.AppTheme
import org.koin.compose.KoinApplicationPreview
import org.koin.core.module.Module

/**
 * Every screen at the three widths the app lays out for (phone, tablet, desktop window), in light
 * and dark, plus the phone at font scale 1.5.
 */
@Preview(name = "phone-light", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(
  name = "phone-dark",
  widthDp = 412,
  heightDp = 915,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "tablet-light", widthDp = 840, heightDp = 1280, showBackground = true)
@Preview(
  name = "tablet-dark",
  widthDp = 840,
  heightDp = 1280,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "desktop-light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(
  name = "desktop-dark",
  widthDp = 1280,
  heightDp = 800,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
  name = "phone-font150",
  widthDp = 412,
  heightDp = 915,
  showBackground = true,
  fontScale = 1.5f,
)
annotation class ScreenPreviews

/** A state (empty, loading, error) shown on the phone only, in light and dark. */
@Preview(name = "phone-light", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(
  name = "phone-dark",
  widthDp = 412,
  heightDp = 915,
  showBackground = true,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
annotation class StatePreviews

/**
 * Hosts a screen the way the app does: the app theme (dark when the preview's ui mode is night),
 * Koin with the app's ViewModels and [backend] behind every repository, and a surface.
 * [extraModules] adds bindings such as a fake `DesktopFeatures`. Each preview gets its own Koin, so
 * one preview's backend never leaks into the next.
 */
@Composable
fun ScreenshotHost(
  backend: FakeBackend = FakeBackend(),
  extraModules: List<Module> = emptyList(),
  content: @Composable () -> Unit,
) {
  check(LocalInspectionMode.current) { "ScreenshotHost is for previews only" }
  KoinApplicationPreview(
    application = { modules(listOf(appModule, fakeRepositoriesModule(backend)) + extraModules) }
  ) {
    AppTheme {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
  }
}
