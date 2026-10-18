package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** One piece of confetti: where it starts, how it flies and spins, and how it looks. */
private class Piece(
  val angle: Float,
  val speed: Float,
  val spin: Float,
  val rotation: Float,
  val color: Color,
  val width: Float,
  val height: Float,
  val round: Boolean,
  val sway: Float,
)

/**
 * A burst of confetti from [origin] (as fractions of the size) that falls and fades, played once
 * for each new [key]. Draws nothing in previews or with animations turned off.
 */
@Composable
fun ConfettiBurst(
  key: Any,
  modifier: Modifier = Modifier,
  origin: Offset = Offset(0.5f, 0.35f),
  pieces: Int = 120,
  durationMillis: Int = 2_800,
) {
  if (!animationsEnabled() || LocalInspectionMode.current) return
  val scheme = MaterialTheme.colorScheme
  val colors =
    listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error, Gold, Pink, Sky)
  val parts =
    remember(key) {
      val random = Random(key.hashCode())
      List(pieces) {
        Piece(
          // Mostly upwards, fanning out to both sides.
          angle = (-PI / 2 + (random.nextFloat() - 0.5f) * PI * 1.1).toFloat(),
          speed = 900f + random.nextFloat() * 1_300f,
          spin = (random.nextFloat() - 0.5f) * 1_400f,
          rotation = random.nextFloat() * 360f,
          color = colors[random.nextInt(colors.size)],
          width = 10f + random.nextFloat() * 10f,
          height = 6f + random.nextFloat() * 8f,
          round = random.nextFloat() < 0.3f,
          sway = random.nextFloat() * 6f,
        )
      }
    }
  val progress = remember(key) { Animatable(0f) }
  LaunchedEffect(key) {
    progress.snapTo(0f)
    progress.animateTo(1f, tween(durationMillis, easing = LinearEasing))
  }
  Canvas(modifier.fillMaxSize()) {
    val p = progress.value
    if (p <= 0f || p >= 1f) return@Canvas
    val t = p * durationMillis / 1_000f
    val start = Offset(size.width * origin.x, size.height * origin.y)
    val alpha = if (p < FADE_FROM) 1f else 1f - (p - FADE_FROM) / (1f - FADE_FROM)
    for (part in parts) {
      // Air drag slows the throw; gravity pulls the piece down; a sway makes it flutter.
      val drag = (1f - kotlin.math.exp(-DRAG * t)) / DRAG
      val x = start.x + cos(part.angle) * part.speed * drag + sin(t * part.sway) * 18f
      val y = start.y + sin(part.angle) * part.speed * drag + GRAVITY * t * t / 2f
      translate(x, y) {
        rotate(part.rotation + part.spin * t, pivot = Offset.Zero) {
          if (part.round) {
            drawCircle(
              part.color.copy(alpha = alpha),
              radius = part.height / 2f,
              center = Offset.Zero,
            )
          } else {
            // The width shrinks and grows as the piece turns over.
            val turn = kotlin.math.abs(cos(t * part.sway + part.rotation))
            drawRect(
              part.color.copy(alpha = alpha),
              topLeft = Offset(-part.width / 2f, -part.height * turn / 2f),
              size = Size(part.width, part.height * turn.coerceAtLeast(0.15f)),
            )
          }
        }
      }
    }
  }
}

/**
 * The end of a list: a check that pops in and draws itself while sparks fly out, with [title] and
 * [hint] below. Plays once each time it comes into view.
 */
@Composable
fun ListEndCelebration(title: String, hint: String?, modifier: Modifier = Modifier) {
  val animate = animationsEnabled() && !LocalInspectionMode.current
  val pop = remember { Animatable(if (animate) 0f else 1f) }
  val draw = remember { Animatable(if (animate) 0f else 1f) }
  val sparks = remember { Animatable(if (animate) 0f else 1f) }
  LaunchedEffect(Unit) {
    if (!animate) return@LaunchedEffect
    pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 300f))
  }
  LaunchedEffect(Unit) {
    if (!animate) return@LaunchedEffect
    kotlinx.coroutines.delay(150)
    draw.animateTo(1f, tween(450))
  }
  LaunchedEffect(Unit) {
    if (!animate) return@LaunchedEffect
    kotlinx.coroutines.delay(250)
    sparks.animateTo(1f, tween(900))
  }
  val scheme = MaterialTheme.colorScheme
  Column(
    modifier.fillMaxWidth().padding(vertical = 24.dp).semantics {
      liveRegion = LiveRegionMode.Polite
    },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
      Canvas(Modifier.fillMaxSize()) {
        val s = sparks.value
        if (s in 0.01f..0.99f) {
          val center = Offset(size.width / 2f, size.height / 2f)
          val colors = listOf(scheme.primary, scheme.tertiary, Gold, scheme.secondary)
          repeat(SPARKS) { i ->
            val a = (2 * PI * i / SPARKS).toFloat()
            val r = size.minDimension * (0.28f + 0.3f * s)
            val tail = size.minDimension * 0.08f * (1f - s)
            val from = center + Offset(cos(a), sin(a)) * (r - tail)
            val to = center + Offset(cos(a), sin(a)) * r
            drawLine(
              colors[i % colors.size].copy(alpha = 1f - s),
              from,
              to,
              strokeWidth = 4.dp.toPx(),
              cap = StrokeCap.Round,
            )
          }
        }
      }
      Canvas(
        Modifier.size(64.dp).graphicsLayer {
          scaleX = pop.value
          scaleY = pop.value
        }
      ) {
        drawCircle(scheme.primaryContainer)
        val check =
          Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.36f)
          }
        val measure = PathMeasure().apply { setPath(check, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * draw.value, part, true)
        drawPath(
          part,
          scheme.onPrimaryContainer,
          style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
        )
      }
    }
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      textAlign = TextAlign.Center,
      modifier = Modifier.graphicsLayer { alpha = draw.value },
    )
    if (hint != null) {
      Text(
        hint,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp).graphicsLayer { alpha = draw.value },
      )
    }
  }
}

private const val SPARKS = 12
private const val GRAVITY = 1_400f
private const val DRAG = 1.6f
private const val FADE_FROM = 0.7f
private val Gold = Color(0xFFFFC23D)
private val Pink = Color(0xFFFF6FA8)
private val Sky = Color(0xFF4FC3F7)
