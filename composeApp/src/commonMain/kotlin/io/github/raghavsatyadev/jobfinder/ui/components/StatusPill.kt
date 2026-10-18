@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The colour role of a [StatusPill]. */
enum class StatusTone {
  Positive,
  Negative,
  Neutral,
  Active,
  /** Out of the way: archived or otherwise done with. */
  Muted,
}

/** A short state label on a container colour, such as a job status. */
@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  val motion = MaterialTheme.motionScheme
  val container: Color
  val onContainer: Color
  when (tone) {
    StatusTone.Positive -> {
      container = colors.primaryContainer
      onContainer = colors.onPrimaryContainer
    }
    StatusTone.Negative -> {
      container = colors.errorContainer
      onContainer = colors.onErrorContainer
    }
    StatusTone.Neutral -> {
      container = colors.tertiaryContainer
      onContainer = colors.onTertiaryContainer
    }
    StatusTone.Active -> {
      container = colors.secondaryContainer
      onContainer = colors.onSecondaryContainer
    }
    StatusTone.Muted -> {
      container = colors.surfaceContainerHighest
      onContainer = colors.onSurfaceVariant
    }
  }
  Surface(
    shape = MaterialTheme.shapes.small,
    color = container,
    contentColor = onContainer,
    modifier = modifier.animateContentSize(motion.fastSpatialSpec()),
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
  }
}
