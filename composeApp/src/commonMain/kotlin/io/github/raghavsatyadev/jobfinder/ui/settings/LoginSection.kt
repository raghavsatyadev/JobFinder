package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.help_setup_open
import io.github.raghavsatyadev.jobfinder.resources.settings_login_all_ok
import io.github.raghavsatyadev.jobfinder.resources.settings_login_button
import io.github.raghavsatyadev.jobfinder.resources.settings_login_checking
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied_missing
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copying
import io.github.raghavsatyadev.jobfinder.resources.settings_login_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_login_no_extension
import io.github.raghavsatyadev.jobfinder.resources.settings_login_no_searches
import io.github.raghavsatyadev.jobfinder.resources.settings_login_open_anyway
import io.github.raghavsatyadev.jobfinder.resources.settings_login_opened
import io.github.raghavsatyadev.jobfinder.resources.settings_login_sharing_off
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupAssistant
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin

@Composable
internal fun LoginSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.settings_login_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      onClick = { onEvent(SettingsUiEvent.OpenLoginBrowser) },
      enabled = !state.checkingSignIns,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.AutoMirrored.Rounded.Login,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(
          if (state.checkingSignIns) Res.string.settings_login_checking
          else Res.string.settings_login_button
        ),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    state.signInCheck?.let { SignInCheckResult(it, onEvent) }
    HorizontalDivider()
    Text(
      stringResource(Res.string.settings_login_copy_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = { onEvent(SettingsUiEvent.CopyBrowserSignIns) },
      enabled = !state.copyingSignIns,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.Rounded.ContentCopy,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(
          if (state.copyingSignIns) Res.string.settings_login_copying
          else Res.string.settings_login_copy
        ),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    state.signInCopy?.let { result ->
      val failed = result !is SignInCopy.Copied || result.missing.isNotEmpty()
      Text(
        when (result) {
          is SignInCopy.Copied ->
            if (result.missing.isEmpty()) {
              stringResource(Res.string.settings_login_copied, result.cookies)
            } else {
              stringResource(
                Res.string.settings_login_copied_missing,
                result.cookies,
                result.missing.joinToString(", "),
              )
            }
          SignInCopy.NoExtension -> stringResource(Res.string.settings_login_no_extension)
          SignInCopy.SharingOff -> stringResource(Res.string.settings_login_sharing_off)
          is SignInCopy.Failed ->
            stringResource(Res.string.settings_login_copy_failed, result.message)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
      if (result == SignInCopy.NoExtension) {
        val setup = getKoin().getOrNull<SetupAssistant>()
        if (setup != null) {
          TextButton(onClick = { setup.show(true) }, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(Res.string.help_setup_open))
          }
        }
      }
    }
  }
}

/** What the look at the job sites found, under the sign-in button. */
@Composable
private fun SignInCheckResult(check: SignInCheck, onEvent: (SettingsUiEvent) -> Unit) {
  val text =
    when (check) {
      SignInCheck.NoSearches -> stringResource(Res.string.settings_login_no_searches)
      is SignInCheck.Checked ->
        if (check.needed.isEmpty())
          stringResource(
            Res.string.settings_login_all_ok,
            check.ready.joinToString(", ") { it.site },
          )
        else
          stringResource(
            Res.string.settings_login_opened,
            check.needed.joinToString(", ") { it.site },
          )
    }
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
  if (check is SignInCheck.Checked && check.needed.isEmpty()) {
    TextButton(
      onClick = { onEvent(SettingsUiEvent.OpenLoginBrowserAnyway) },
      shapes = ButtonDefaults.shapes(),
    ) {
      Text(stringResource(Res.string.settings_login_open_anyway))
    }
  }
}
