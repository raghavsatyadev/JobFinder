package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * Whether the bars that make way for a list show: the floating tab bar and the list's own header.
 * Scrolling down through [connection] hides them, scrolling up brings them back.
 */
@Stable
class ScrollBars {
  var visible by mutableStateOf(true)

  // How far the finger or wheel went in one direction since it last turned.
  private var travel = 0f

  val connection =
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        if (dy == 0f) return Offset.Zero
        travel = if (dy < 0 == travel < 0) travel + dy else dy
        when {
          travel < -TURN_PX -> visible = false
          travel > TURN_PX -> visible = true
        }
        return Offset.Zero
      }
    }
}

/** The frame's [ScrollBars], or null where nothing hides (previews). */
@Suppress("CompositionLocalAllowlist")
val LocalScrollBars = staticCompositionLocalOf<ScrollBars?> { null }

// A small jitter of the finger should not flick the bars.
private const val TURN_PX = 24f
