package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.offline_read_only
import org.jetbrains.compose.resources.stringResource

/**
 * `true` on a paired phone while the computer is out of reach. Changes only the computer can make
 * (settings, searches, resume, notes, scans) then show read-only with [OfflineNote]; job status and
 * label changes, AI filters and AI instructions are kept and sent later. Always `false` on the
 * desktop.
 */
@Suppress("CompositionLocalAllowlist") val LocalComputerOffline = compositionLocalOf { false }

/** One line under a read-only part: changes wait for the computer. */
@Composable
fun OfflineNote(
  modifier: Modifier = Modifier,
  text: String = stringResource(Res.string.offline_read_only),
) {
  Row(
    modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Rounded.CloudOff,
      contentDescription = null,
      modifier = Modifier.size(16.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
