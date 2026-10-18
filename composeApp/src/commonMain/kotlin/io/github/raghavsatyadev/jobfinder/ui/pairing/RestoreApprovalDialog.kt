package io.github.raghavsatyadev.jobfinder.ui.pairing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.restore_request_allow
import io.github.raghavsatyadev.jobfinder.resources.restore_request_deny
import io.github.raghavsatyadev.jobfinder.resources.restore_request_text
import io.github.raghavsatyadev.jobfinder.resources.restore_request_title
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import io.github.raghavsatyadev.jobfinder.ui.settings.isoDate
import org.jetbrains.compose.resources.stringResource

/**
 * Asks the user to allow a phone's request to replace everything here with its backup. Only the two
 * buttons answer it; unanswered, it expires as refused.
 */
@Composable
fun RestoreApprovalDialog(
  /** What the phone's backup holds. */
  manifest: BackupManifest,
  onAllow: () -> Unit,
  onDeny: () -> Unit,
) {
  val m = manifest
  AlertDialog(
    onDismissRequest = {},
    icon = { Icon(Icons.Rounded.SettingsBackupRestore, contentDescription = null) },
    title = { Text(stringResource(Res.string.restore_request_title)) },
    text = {
      Text(
        stringResource(
          Res.string.restore_request_text,
          m.jobs,
          m.searches,
          m.notes,
          isoDate(m.exportedAt),
        )
      )
    },
    confirmButton = {
      Button(
        onClick = onAllow,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
          ),
      ) {
        Text(stringResource(Res.string.restore_request_allow))
      }
    },
    dismissButton = {
      TextButton(onClick = onDeny) { Text(stringResource(Res.string.restore_request_deny)) }
    },
  )
}
