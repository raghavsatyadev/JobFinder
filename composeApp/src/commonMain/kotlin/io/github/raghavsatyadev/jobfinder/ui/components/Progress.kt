@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled

/**
 * The app's progress bar, full width: wavy whatever its value ([progress] `null` for an unknown
 * end), flat with motion reduced. The full amplitude keeps a bar near 0 or 1 from drawing straight.
 */
@Composable
fun WavyProgress(progress: Float? = null, modifier: Modifier = Modifier) {
  val full = modifier.fillMaxWidth()
  if (animationsEnabled()) {
    if (progress != null) {
      LinearWavyProgressIndicator(progress = { progress }, amplitude = { 1f }, modifier = full)
    } else {
      LinearWavyProgressIndicator(modifier = full)
    }
  } else {
    if (progress != null) {
      LinearProgressIndicator(progress = { progress }, strokeCap = StrokeCap.Round, modifier = full)
    } else {
      LinearProgressIndicator(strokeCap = StrokeCap.Round, modifier = full)
    }
  }
}
