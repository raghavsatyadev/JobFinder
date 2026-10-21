package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.ui.theme.AppMotion
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled

/** The [SharedTransitionScope] around the nav host, or null outside it (previews). */
// The nav host provides both scopes; the cards and screens that share bounds sit several layers
// down, and a parameter would have to pass through every screen in between.
@Suppress("CompositionLocalAllowlist")
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The visibility scope of the nav entry this content is in, or null outside the nav host. */
@Suppress("CompositionLocalAllowlist")
val LocalEntryVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** The corner radius of a card, which a container transform starts from and ends on. */
val CardCorner: Dp = 24.dp

/** The corner radius of a FAB, which the screen it opens grows from. */
val FabCorner: Dp = 16.dp

/** The corner radius of a round icon button, which the Help screen grows from. */
val HelpButtonCorner: Dp = 24.dp

/** The shared key of the add-search screen opened from the Searches FAB. */
const val ADD_LINK_KEY = "add-link"

/** The Help screen, opened from the Help button in Settings. */
const val HELP_KEY = "help"

/** A settings section: its row in the menu and its own page. */
fun settingsSectionKey(name: String): String = "settings-$name"

/** The container of job [jobId]: its card in the list and its detail pane. */
fun jobContainerKey(jobId: Long): String = "job-$jobId"

/** The pieces of a job card that fly on their own into the detail: title, company, logo, status. */
fun jobTitleKey(jobId: Long): String = "job-$jobId-title"

fun jobCompanyKey(jobId: Long): String = "job-$jobId-company"

fun jobLogoKey(jobId: Long): String = "job-$jobId-logo"

fun jobMetaKey(jobId: Long): String = "job-$jobId-meta"

fun jobAtsKey(jobId: Long): String = "job-$jobId-ats"

/**
 * A container transform: the small side (a card, a FAB) and the screen it opens mark their
 * containers with the same [key], and the screen grows out of the small side.
 *
 * Each side keeps its own final layout, clipped by the moving bounds, so text never reflows
 * mid-flight (remeasuring a screen to every in-between size is what makes a transform judder). The
 * bounds move on [AppMotion.screenTransformSpec], which does not bounce: a screen edge that
 * overshoots reads as a glitch. A [fullScreen] side rounds its corners from [corner] to square as
 * it opens, so nothing snaps at the end.
 *
 * Does nothing outside the nav host or with motion reduced, where the screens just fade.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.sharedContainer(
  key: String,
  fullScreen: Boolean = false,
  corner: Dp = CardCorner,
): Modifier {
  val transition = LocalSharedTransitionScope.current ?: return this
  val visibility = LocalEntryVisibilityScope.current ?: return this
  if (!animationsEnabled()) return this
  val spec = AppMotion.screenTransformSpec<Rect>()
  val cornerSpec = AppMotion.screenTransformSpec<Dp>()
  // Closing mirrors opening: the screen stays opaque while it shrinks and fades only at the end,
  // where the small side fades back in. On open the small side fades out fast and the screen fades
  // in, so the two are never both clearly visible at once.
  val enter =
    if (fullScreen) fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    else fadeIn(tween(LATE_FADE_MILLIS, delayMillis = LATE_FADE_DELAY_MILLIS))
  val exit =
    if (fullScreen) fadeOut(tween(LATE_FADE_MILLIS, delayMillis = LATE_FADE_DELAY_MILLIS))
    else fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
  val animatedCorner by
    visibility.transition.animateDp(transitionSpec = { cornerSpec }, label = "container corner") {
      if (fullScreen && it == EnterExitState.Visible) 0.dp else corner
    }
  return with(transition) {
    this@sharedContainer.sharedBounds(
        sharedContentState = rememberSharedContentState(key = key),
        animatedVisibilityScope = visibility,
        enter = enter,
        exit = exit,
        boundsTransform = BoundsTransform { _, _ -> spec },
        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(animatedCorner)),
      )
      // Each side keeps its own final layout (no reflow) and the moving bounds clip it. Unlike a
      // scaled container, the pieces inside stay where they are drawn, so they fly true.
      .skipToLookaheadSize()
  }
}

/**
 * A piece of content, such as a job title, that travels from where it sits on one screen to where
 * it sits on the next, on top of the container transform around it. Both sides use the same [key].
 *
 * One copy moves, never two crossfading: the arriving side is drawn in the moving bounds, scaled
 * from [scaleFrom] (the other side's size over this one's, such as 16 sp / 24 sp for a title that
 * grows) to its own size, from its top start corner. So the text starts exactly as big as the one
 * it replaces and grows or shrinks into place. Does nothing outside the nav host or with motion
 * reduced.
 */
@Composable
fun Modifier.sharedContent(key: String, scaleFrom: Float = 1f): Modifier {
  val transition = LocalSharedTransitionScope.current ?: return this
  val visibility = LocalEntryVisibilityScope.current ?: return this
  if (!animationsEnabled()) return this
  val spec = AppMotion.screenTransformSpec<Rect>()
  val progressSpec = AppMotion.screenTransformSpec<Float>()
  val progress by
    visibility.transition.animateFloat(transitionSpec = { progressSpec }, label = "piece scale") {
      if (it == EnterExitState.Visible) 1f else 0f
    }
  return with(transition) {
    val state = rememberSharedContentState(key = key)
    this@sharedContent.sharedElement(
        sharedContentState = state,
        animatedVisibilityScope = visibility,
        boundsTransform = BoundsTransform { _, _ -> spec },
        // Not clipped to the growing or shrinking container: a piece that flies ahead of it, or is
        // drawn bigger than its bounds on the way, would lose its edges.
        clipInOverlayDuringTransition = NoClip,
      )
      // Measured at its final size, not the moving bounds': text would wrap and a logo lose its
      // shape at the in-between sizes. The scale below does the resizing.
      .skipToLookaheadSize()
      .graphicsLayer {
        // Only a piece that is flying: the other cards of a list that leaves keep their size.
        if (!state.isMatchFound) return@graphicsLayer
        val scale = scaleFrom + (1f - scaleFrom) * progress
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0f, 0f)
      }
  }
}

/** How long the closing screen holds before it fades, and how long the fade takes. */
private const val LATE_FADE_DELAY_MILLIS = 200
private const val LATE_FADE_MILLIS = 120

/** No clip in the overlay: a flying piece is drawn whole. */
private object NoClip : SharedTransitionScope.OverlayClip {
  override fun getClipPath(
    sharedContentState: SharedTransitionScope.SharedContentState,
    bounds: Rect,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Path? = null
}
