package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.backup_password
import io.github.raghavsatyadev.jobfinder.resources.backup_password_again
import io.github.raghavsatyadev.jobfinder.resources.backup_password_export
import io.github.raghavsatyadev.jobfinder.resources.backup_password_fair
import io.github.raghavsatyadev.jobfinder.resources.backup_password_keys
import io.github.raghavsatyadev.jobfinder.resources.backup_password_mismatch
import io.github.raghavsatyadev.jobfinder.resources.backup_password_plain
import io.github.raghavsatyadev.jobfinder.resources.backup_password_plain_hint
import io.github.raghavsatyadev.jobfinder.resources.backup_password_plain_keys
import io.github.raghavsatyadev.jobfinder.resources.backup_password_strong
import io.github.raghavsatyadev.jobfinder.resources.backup_password_text
import io.github.raghavsatyadev.jobfinder.resources.backup_password_title
import io.github.raghavsatyadev.jobfinder.resources.backup_password_too_short
import io.github.raghavsatyadev.jobfinder.resources.backup_password_weak
import io.github.raghavsatyadev.jobfinder.resources.backup_unlock_button
import io.github.raghavsatyadev.jobfinder.resources.backup_unlock_opening
import io.github.raghavsatyadev.jobfinder.resources.backup_unlock_text
import io.github.raghavsatyadev.jobfinder.resources.backup_unlock_title
import io.github.raghavsatyadev.jobfinder.resources.backup_unlock_wrong
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupCrypto
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import org.jetbrains.compose.resources.stringResource

/** A rough strength of an export password, for the hint under it. */
enum class PasswordStrength {
  TOO_SHORT,
  WEAK,
  FAIR,
  STRONG;

  companion object {
    /** Kinds of characters used, plus a point for each step of extra length. */
    fun of(password: String): PasswordStrength {
      if (password.length < BackupCrypto.MIN_PASSWORD_LENGTH) return TOO_SHORT
      val kinds =
        listOf(
            password.any { it.isLowerCase() },
            password.any { it.isUpperCase() },
            password.any { it.isDigit() },
            password.any { !it.isLetterOrDigit() },
          )
          .count { it }
      val length = listOf(14, 18, 24).count { password.length >= it }
      return when (kinds + length) {
        in 0..2 -> WEAK
        3 -> FAIR
        else -> STRONG
      }
    }
  }
}

/**
 * The export's password, typed twice, with a strength hint. On the computer it says the API keys go
 * in the file. "Save without a password" locks it with this device's own key instead, shown only
 * where the device keeps one ([deviceLock]).
 */
@Composable
fun ExportPasswordDialog(
  lock: ExportLock,
  desktop: Boolean,
  deviceLock: Boolean,
  onEvent: (BackupUiEvent) -> Unit,
) {
  AlertDialog(
    onDismissRequest = { onEvent(BackupUiEvent.Dismiss) },
    title = { Text(stringResource(Res.string.backup_password_title)) },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(stringResource(Res.string.backup_password_text))
        if (desktop) Text(stringResource(Res.string.backup_password_keys))
        PasswordField(
          value = lock.password,
          label = stringResource(Res.string.backup_password),
          imeAction = ImeAction.Next,
          onValue = { onEvent(BackupUiEvent.SetPassword(it)) },
          supporting = strengthText(lock.strength),
          error = false,
        )
        val mismatch = lock.again.isNotEmpty() && lock.again != lock.password
        PasswordField(
          value = lock.again,
          label = stringResource(Res.string.backup_password_again),
          imeAction = ImeAction.Done,
          onValue = { onEvent(BackupUiEvent.SetPasswordAgain(it)) },
          onDone = { onEvent(BackupUiEvent.ExportLocked) },
          supporting = if (mismatch) stringResource(Res.string.backup_password_mismatch) else null,
          error = mismatch,
        )
        if (deviceLock) {
          HorizontalDivider(Modifier.padding(top = 4.dp))
          Text(
            stringResource(
              if (desktop) Res.string.backup_password_plain_keys
              else Res.string.backup_password_plain_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          TextButton(onClick = { onEvent(BackupUiEvent.ExportDevice) }) {
            Text(stringResource(Res.string.backup_password_plain))
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onEvent(BackupUiEvent.ExportLocked) }, enabled = lock.ready) {
        Text(stringResource(Res.string.backup_password_export))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(BackupUiEvent.Dismiss) }) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}

/** A locked backup's password; a wrong one says so and lets the user try again. */
@Composable
fun ImportPasswordDialog(unlock: ImportUnlock, onEvent: (BackupUiEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(BackupUiEvent.Dismiss) },
    title = { Text(stringResource(Res.string.backup_unlock_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.backup_unlock_text))
        PasswordField(
          value = unlock.password,
          label = stringResource(Res.string.backup_password),
          imeAction = ImeAction.Done,
          onValue = { onEvent(BackupUiEvent.SetImportPassword(it)) },
          onDone = { onEvent(BackupUiEvent.OpenLocked) },
          supporting = if (unlock.wrong) stringResource(Res.string.backup_unlock_wrong) else null,
          error = unlock.wrong,
          enabled = !unlock.opening,
        )
        if (unlock.opening) {
          Text(
            stringResource(Res.string.backup_unlock_opening),
            style = MaterialTheme.typography.bodyMedium,
          )
          WavyProgress()
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onEvent(BackupUiEvent.OpenLocked) },
        enabled = unlock.password.isNotEmpty() && !unlock.opening,
      ) {
        Text(stringResource(Res.string.backup_unlock_button))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(BackupUiEvent.Dismiss) }, enabled = !unlock.opening) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}

@Composable
private fun strengthText(strength: PasswordStrength): String =
  when (strength) {
    PasswordStrength.TOO_SHORT ->
      stringResource(Res.string.backup_password_too_short, BackupCrypto.MIN_PASSWORD_LENGTH)
    PasswordStrength.WEAK -> stringResource(Res.string.backup_password_weak)
    PasswordStrength.FAIR -> stringResource(Res.string.backup_password_fair)
    PasswordStrength.STRONG -> stringResource(Res.string.backup_password_strong)
  }

@Composable
private fun PasswordField(
  value: String,
  label: String,
  imeAction: ImeAction,
  onValue: (String) -> Unit,
  supporting: String?,
  error: Boolean,
  onDone: () -> Unit = {},
  enabled: Boolean = true,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onValue,
    label = { Text(label) },
    supportingText = supporting?.let { { Text(it) } },
    isError = error,
    singleLine = true,
    enabled = enabled,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions =
      KeyboardOptions(
        keyboardType = KeyboardType.Password,
        autoCorrectEnabled = false,
        imeAction = imeAction,
      ),
    keyboardActions = KeyboardActions(onDone = { onDone() }),
    modifier = Modifier.fillMaxWidth(),
  )
}
