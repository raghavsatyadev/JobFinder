package io.github.raghavsatyadev.jobfinder.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import io.github.raghavsatyadev.jobfinder.desktop.system.WindowMotion
import io.github.raghavsatyadev.jobfinder.desktop.system.WindowsCaption
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.app_icon
import io.github.raghavsatyadev.jobfinder.resources.window_close
import io.github.raghavsatyadev.jobfinder.resources.window_maximize
import io.github.raghavsatyadev.jobfinder.resources.window_minimize
import io.github.raghavsatyadev.jobfinder.resources.window_restore
import io.github.raghavsatyadev.jobfinder.ui.theme.AppTheme
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val TitleBarHeight = 40.dp
private val ButtonWidth = 46.dp
private const val BUTTON_COUNT = 3

/** Windows 11's colour for a hovered close button. */
private val CloseRed = Color(0xFFC42B1C)

/**
 * The window's chrome around [content]: on Windows, the app's own title bar in the app's colours
 * (logo, [title], and minimise, maximise and close at the right end), with dragging, snapping and
 * resizing left to Windows; elsewhere, the system title bar. Minimise and maximise go through
 * [motion], so Windows animates them as for its own buttons. The close button runs the window's
 * `onCloseRequest`.
 */
@Composable
fun FrameWindowScope.AppWindowFrame(
  title: String,
  motion: WindowMotion = remember(window) { WindowMotion(window) },
  content: @Composable () -> Unit,
) {
  var caption by remember { mutableStateOf<WindowsCaption?>(null) }
  DisposableEffect(window) {
    caption = WindowsCaption.install(window)
    // A window that starts hidden gets its native handle only when first shown.
    val listener =
      object : WindowAdapter() {
        override fun windowOpened(e: WindowEvent) {
          if (caption == null) caption = WindowsCaption.install(window)
        }
      }
    window.addWindowListener(listener)
    onDispose { window.removeWindowListener(listener) }
  }
  val hook = caption
  if (hook == null) {
    content()
    return
  }
  val density = LocalDensity.current
  SideEffect {
    with(density) {
      hook.captionHeightPx = TitleBarHeight.roundToPx()
      hook.buttonsWidthPx = (ButtonWidth * BUTTON_COUNT).roundToPx()
    }
  }
  Column(Modifier.fillMaxSize()) {
    AppTheme { TitleBar(title, motion) }
    Box(Modifier.fillMaxWidth().weight(1f)) { content() }
  }
}

@Composable
private fun FrameWindowScope.TitleBar(title: String, motion: WindowMotion) {
  var maximized by remember { mutableStateOf(window.placement == WindowPlacement.Maximized) }
  DisposableEffect(window) {
    val listener =
      object : WindowAdapter() {
        override fun windowStateChanged(e: WindowEvent) {
          maximized = window.placement == WindowPlacement.Maximized
        }
      }
    window.addWindowStateListener(listener)
    onDispose { window.removeWindowStateListener(listener) }
  }
  TitleBar(
    title = title,
    maximized = maximized,
    onMinimize = motion::minimize,
    onToggleMaximize = motion::toggleMaximize,
    // The same event the system close button sends, so `onCloseRequest` decides what happens.
    onClose = { window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING)) },
  )
}

/**
 * The title bar itself: logo, [title], and the minimise, maximise or restore, and close buttons.
 */
@Composable
internal fun TitleBar(
  title: String,
  maximized: Boolean,
  onMinimize: () -> Unit,
  onToggleMaximize: () -> Unit,
  onClose: () -> Unit,
) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
    Row(
      Modifier.fillMaxWidth().height(TitleBarHeight),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Image(
        painterResource(Res.drawable.app_icon),
        contentDescription = null,
        modifier = Modifier.padding(start = 12.dp).size(20.dp),
      )
      Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 10.dp).weight(1f),
      )
      CaptionButton(
        stringResource(Res.string.window_minimize),
        onClick = onMinimize,
      ) { color ->
        drawLine(
          color,
          Offset(0f, size.height / 2),
          Offset(size.width, size.height / 2),
          1.dp.toPx(),
        )
      }
      val maximizeLabel =
        stringResource(if (maximized) Res.string.window_restore else Res.string.window_maximize)
      CaptionButton(
        maximizeLabel,
        onClick = onToggleMaximize,
      ) { color ->
        val stroke = Stroke(1.dp.toPx())
        if (maximized) {
          val offset = size.width * 0.2f
          val box = Size(size.width - offset, size.height - offset)
          drawRect(color, Offset(0f, offset), box, style = stroke)
          drawLine(color, Offset(offset, offset), Offset(offset, 0f), stroke.width)
          drawLine(color, Offset(offset, 0f), Offset(size.width, 0f), stroke.width)
          drawLine(color, Offset(size.width, 0f), Offset(size.width, box.height), stroke.width)
          drawLine(
            color,
            Offset(size.width, box.height),
            Offset(box.width, box.height),
            stroke.width,
          )
        } else {
          drawRect(color, style = stroke)
        }
      }
      CaptionButton(
        stringResource(Res.string.window_close),
        hoverColor = CloseRed,
        hoverContent = Color.White,
        onClick = onClose,
      ) { color ->
        val width = 1.dp.toPx()
        drawLine(color, Offset.Zero, Offset(size.width, size.height), width)
        drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), width)
      }
    }
  }
}

/**
 * One window button: full title-bar height, so the top screen edge is on it when maximised. The
 * glyph is drawn as thin lines, like the system's own buttons.
 */
@Composable
private fun CaptionButton(
  label: String,
  onClick: () -> Unit,
  hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
  hoverContent: Color = MaterialTheme.colorScheme.onSurface,
  glyph: DrawScope.(Color) -> Unit,
) {
  val interaction = remember { MutableInteractionSource() }
  val hovered by interaction.collectIsHoveredAsState()
  val pressed by interaction.collectIsPressedAsState()
  val background =
    when {
      pressed -> hoverColor.copy(alpha = (hoverColor.alpha * 0.8f).coerceAtLeast(0.12f))
      hovered -> hoverColor
      else -> Color.Transparent
    }
  val tint = if (hovered || pressed) hoverContent else MaterialTheme.colorScheme.onSurface
  Box(
    Modifier.width(ButtonWidth)
      .fillMaxHeight()
      .background(background)
      .hoverable(interaction)
      .clickable(
        interactionSource = interaction,
        indication = null,
        onClickLabel = label,
        role = Role.Button,
        onClick = onClick,
      )
      .semantics { contentDescription = label },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(10.dp)) { glyph(tint) }
  }
}
