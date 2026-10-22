package io.github.raghavsatyadev.jobfinder.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.FrameWindowScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.app_icon
import java.awt.Taskbar
import org.jetbrains.compose.resources.painterResource

private const val ICON_SIZE_PX = 256f

/**
 * Sets the app logo as the window icon. Call it inside the window content: `Window(icon = …)` loads
 * the painter in the `application {}` composition, where Compose graphics are not ready yet and the
 * app crashes with "No Compose UI graphics implementation is registered".
 */
@Composable
fun FrameWindowScope.AppWindowIcon() {
  val painter = painterResource(Res.drawable.app_icon)
  val density = LocalDensity.current
  LaunchedEffect(painter, density) {
    val image = painter.toAwtImage(density, LayoutDirection.Ltr, Size(ICON_SIZE_PX, ICON_SIZE_PX))
    window.iconImage = image
    // macOS shows the Dock icon, not the window's; a packaged app already has its own.
    if (Taskbar.isTaskbarSupported()) {
      val taskbar = Taskbar.getTaskbar()
      if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) taskbar.iconImage = image
    }
  }
}
