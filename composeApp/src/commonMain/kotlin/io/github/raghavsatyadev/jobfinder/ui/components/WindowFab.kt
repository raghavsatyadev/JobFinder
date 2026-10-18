package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Where a pane's floating action button goes when panes sit side by side: the window's bottom-right
 * corner rather than the corner of a narrow list pane.
 */
class WindowFab {
  internal var content by mutableStateOf<(@Composable () -> Unit)?>(null)
}

/** The window's [WindowFab] when panes sit side by side, or null (one pane, previews). */
@Suppress("CompositionLocalAllowlist")
val LocalWindowFab = staticCompositionLocalOf<WindowFab?> { null }

/**
 * Shows [fab] in the window's corner when there is a [WindowFab], else lets the caller place it.
 */
@Composable
fun WindowFab.Show(fab: @Composable () -> Unit) {
  SideEffect { content = fab }
  DisposableEffect(this) { onDispose { content = null } }
}

/** Draws the [WindowFab]'s button in [BoxScope]'s bottom-right corner. */
@Composable
fun BoxScope.WindowFabSlot(fab: WindowFab?) {
  val content = fab?.content ?: return
  Box(Modifier.align(Alignment.BottomEnd).padding(24.dp)) { content() }
}
