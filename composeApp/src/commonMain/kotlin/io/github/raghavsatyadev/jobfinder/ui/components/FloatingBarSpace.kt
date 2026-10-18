package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The room the floating tab bar takes above the navigation bar, `0` where there is none (the rail).
 * Screens scroll under the bar and keep their buttons and last items above it.
 */
// The frame sets it once; every top-level screen's Scaffold reads it, several layers down.
@Suppress("CompositionLocalAllowlist") val LocalFloatingBarSpace = compositionLocalOf { 0.dp }

/** A screen Scaffold's insets: the system bars plus the floating tab bar. */
@Composable
fun screenInsets(): WindowInsets =
  ScaffoldDefaults.contentWindowInsets.add(WindowInsets(bottom = LocalFloatingBarSpace.current))

/** [padding] without its bottom, so the content runs on under the floating tab bar. */
@Composable
fun Modifier.paddingAbove(padding: PaddingValues): Modifier {
  val direction = LocalLayoutDirection.current
  return padding(
    start = padding.calculateStartPadding(direction),
    top = padding.calculateTopPadding(),
    end = padding.calculateEndPadding(direction),
  )
}

/**
 * The space a scrolling screen leaves after its last item: navigation bar plus floating tab bar.
 */
@Composable
fun scrollBottom(): Dp =
  LocalFloatingBarSpace.current +
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
