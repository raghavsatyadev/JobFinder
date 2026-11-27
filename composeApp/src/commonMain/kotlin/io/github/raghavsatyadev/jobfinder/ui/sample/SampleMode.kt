@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.sample_banner_exit
import io.github.raghavsatyadev.jobfinder.resources.sample_banner_title
import io.github.raghavsatyadev.jobfinder.resources.sample_entry_button
import io.github.raghavsatyadev.jobfinder.resources.sample_entry_text
import io.github.raghavsatyadev.jobfinder.resources.sample_entry_title
import org.jetbrains.compose.resources.stringResource

/**
 * Lets someone without a computer (a store reviewer, a curious user) look around the phone app on
 * sample jobs. Bound on Android only. Switching restarts the app, so no real data mixes in.
 */
interface SampleMode {
  /** True while the app shows sample jobs instead of a paired computer's. */
  val active: Boolean

  /** Restarts the app on sample jobs. */
  fun start()

  /** Restarts the app on the pairing screen. */
  fun exit()
}

/** The way into sample mode, at the bottom of the pairing screen. */
@Composable
fun SampleModeEntry(mode: SampleMode, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(Res.string.sample_entry_title),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { heading() },
    )
    Text(
      stringResource(Res.string.sample_entry_text),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FilledTonalButton(
      onClick = mode::start,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(stringResource(Res.string.sample_entry_button))
    }
  }
}

/** Always on top in sample mode, so nobody takes the sample jobs for real ones. */
@Composable
fun SampleModeBanner(mode: SampleMode, modifier: Modifier = Modifier) {
  Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier.fillMaxWidth()) {
    Row(
      Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        stringResource(Res.string.sample_banner_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = mode::exit,
        shapes = ButtonDefaults.shapes(),
        colors =
          ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
          ),
      ) {
        Text(stringResource(Res.string.sample_banner_exit))
      }
    }
  }
}
