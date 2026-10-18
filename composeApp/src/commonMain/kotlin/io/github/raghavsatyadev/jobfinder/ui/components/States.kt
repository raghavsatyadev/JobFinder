@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_loading
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** How long a load must take before the indicator shows; a local load usually finishes sooner. */
private const val LOADING_DELAY_MILLIS = 400L

/**
 * A wait. Shows the expressive [LoadingIndicator] only after [LOADING_DELAY_MILLIS], so a fast load
 * does not flash it.
 */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
  // Previews render one frame, so they show the indicator at once instead of after the delay.
  val inspection = LocalInspectionMode.current
  var visible by remember { mutableStateOf(inspection) }
  LaunchedEffect(Unit) {
    delay(LOADING_DELAY_MILLIS)
    visible = true
  }
  val description = stringResource(Res.string.common_loading)
  Box(
    modifier.fillMaxSize().semantics { stateDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    if (visible) LoadingIndicator(Modifier.size(64.dp))
  }
}

/**
 * Nothing to show: one drifting [MaterialShapes] shape with [icon] on it, a title and a hint. Fades
 * and scales in, so it does not pop.
 */
@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  hint: String,
  modifier: Modifier = Modifier,
  action: (@Composable () -> Unit)? = null,
) {
  val motion = MaterialTheme.motionScheme
  AnimatedVisibility(
    visible = true,
    enter = fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), 0.92f),
    modifier = modifier.fillMaxSize(),
  ) {
    Column(
      Modifier.fillMaxSize().padding(32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(contentAlignment = Alignment.Center) {
        DriftingShape(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = MaterialShapes.Cookie9Sided,
          size = 160.dp,
        )
        Icon(
          icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(56.dp),
        )
      }
      Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 480.dp),
      )
      Text(
        hint,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 480.dp),
      )
      action?.invoke()
    }
  }
}

/** A failed load: what went wrong and a retry button. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier.fillMaxSize().padding(32.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      Icons.Rounded.ErrorOutline,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.error,
      modifier = Modifier.size(48.dp),
    )
    Text(
      message,
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(max = 480.dp),
    )
    Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
      Text(stringResource(Res.string.common_retry))
    }
  }
}

/**
 * Asks before a destructive action. The confirm button uses the error colour; the dialog never acts
 * on a single tap.
 */
@Composable
fun ConfirmDialog(
  icon: ImageVector,
  title: String,
  text: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(icon, contentDescription = null) },
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
      TextButton(
        onClick = onConfirm,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
      ) {
        Text(confirmLabel)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}

/**
 * Old data shown while new data loads or after a failed refresh: drawn at 0.6 alpha and 0.15
 * saturation. [reason] is read to screen readers.
 */
fun Modifier.stale(isStale: Boolean, reason: String): Modifier =
  if (!isStale) this
  else
    this.semantics { stateDescription = reason }
      .drawWithContent {
        val paint =
          Paint().apply {
            alpha = 0.6f
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) })
          }
        drawIntoCanvas { canvas ->
          canvas.saveLayer(Rect(Offset.Zero, size), paint)
          drawContent()
          canvas.restore()
        }
      }
