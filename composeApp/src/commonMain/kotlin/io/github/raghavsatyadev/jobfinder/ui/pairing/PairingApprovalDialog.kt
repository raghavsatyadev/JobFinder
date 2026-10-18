package io.github.raghavsatyadev.jobfinder.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_allow
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_code_label
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_deny
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_text
import io.github.raghavsatyadev.jobfinder.resources.pairing_request_title
import org.jetbrains.compose.resources.stringResource

/**
 * Asks the user to allow or deny a phone's pairing request. The code is shown large so the user can
 * compare it with the phone in their hand. Only the two buttons answer it: a click beside the
 * dialog or Escape must not deny a phone the user meant to allow. Unanswered, it expires.
 */
@Composable
fun PairingApprovalDialog(
  deviceName: String,
  /** The code the phone shows. */
  code: String,
  onAllow: () -> Unit,
  onDeny: () -> Unit,
) {
  val codeLabel = stringResource(Res.string.pairing_request_code_label)
  val spaced = code.chunked(3).joinToString(" ")
  AlertDialog(
    onDismissRequest = {},
    icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
    title = { Text(stringResource(Res.string.pairing_request_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.pairing_request_text, deviceName))
        Text(codeLabel, style = MaterialTheme.typography.labelLarge)
        Text(
          spaced,
          style = MaterialTheme.typography.displaySmall,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.semantics { contentDescription = "$codeLabel ${code}" },
        )
      }
    },
    confirmButton = {
      Button(onClick = onAllow) { Text(stringResource(Res.string.pairing_request_allow)) }
    },
    dismissButton = {
      TextButton(onClick = onDeny) { Text(stringResource(Res.string.pairing_request_deny)) }
    },
  )
}
