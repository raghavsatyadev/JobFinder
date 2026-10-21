@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import io.github.raghavsatyadev.jobfinder.resources.settings_ai_paused_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_ai_paused_title
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_cleanup_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_cleanup_title
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_keep_days
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_keep_fewer
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_keep_more
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_custom
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risky
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risky_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_title
import io.github.raghavsatyadev.jobfinder.resources.settings_in_app_browser_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_in_app_browser_title
import io.github.raghavsatyadev.jobfinder.resources.settings_load_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_prevent_sleep_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_prevent_sleep_title
import io.github.raghavsatyadev.jobfinder.resources.settings_sound_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_sound_title
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.translate.TranslateToPicker
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScanningSection(scanner: ScannerSettings?, onEvent: (SettingsUiEvent) -> Unit) {
  if (scanner == null) {
    ScannerUnavailable(onRetry = { onEvent(SettingsUiEvent.Retry) })
    return
  }
  val enabled = !LocalComputerOffline.current
  FrequencyPicker(scanner.cronRule, enabled) { onEvent(SettingsUiEvent.SetFrequency(it)) }
  HorizontalDivider(Modifier.padding(horizontal = 16.dp))
  SwitchRow(
    Res.string.settings_ai_paused_title,
    Res.string.settings_ai_paused_hint,
    scanner.aiPaused,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetAiPaused(it))
  }
  TranslateToPicker(scanner.translateTo, enabled) { onEvent(SettingsUiEvent.SetTranslateTo(it)) }
  SwitchRow(
    Res.string.settings_prevent_sleep_title,
    Res.string.settings_prevent_sleep_hint,
    scanner.preventSleep,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetPreventSleep(it))
  }
  SwitchRow(
    Res.string.settings_sound_title,
    Res.string.settings_sound_hint,
    scanner.useSound,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetSound(it))
  }
  SwitchRow(
    Res.string.settings_in_app_browser_title,
    Res.string.settings_in_app_browser_hint,
    scanner.inAppBrowserEnabled,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetInAppBrowser(it))
  }
  HorizontalDivider(Modifier.padding(horizontal = 16.dp))
  val keepDays = scanner.excludedKeepDays
  SwitchRow(
    Res.string.settings_excluded_cleanup_title,
    Res.string.settings_excluded_cleanup_hint,
    keepDays != null,
    enabled,
  ) {
    onEvent(
      SettingsUiEvent.SetExcludedKeepDays(
        if (it) ScannerSettings.DEFAULT_EXCLUDED_KEEP_DAYS else null
      )
    )
  }
  if (keepDays != null) {
    KeepDaysStepper(keepDays, enabled) { onEvent(SettingsUiEvent.SetExcludedKeepDays(it)) }
  }
  if (!enabled) OfflineNote()
}

/** Steps [days] through [KEEP_DAY_STEPS]; a value between steps moves to the next one. */
@Composable
private fun KeepDaysStepper(days: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  Row(
    Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(Res.string.settings_excluded_keep_days, days),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    val fewer = KEEP_DAY_STEPS.lastOrNull { it < days }
    val more = KEEP_DAY_STEPS.firstOrNull { it > days }
    IconButton(onClick = { fewer?.let(onChange) }, enabled = enabled && fewer != null) {
      Icon(
        Icons.Rounded.Remove,
        contentDescription = stringResource(Res.string.settings_excluded_keep_fewer),
      )
    }
    IconButton(onClick = { more?.let(onChange) }, enabled = enabled && more != null) {
      Icon(
        Icons.Rounded.Add,
        contentDescription = stringResource(Res.string.settings_excluded_keep_more),
      )
    }
  }
}

private val KEEP_DAY_STEPS = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365)

@Composable
private fun SwitchRow(
  title: StringResource,
  hint: StringResource,
  checked: Boolean,
  enabled: Boolean,
  onChange: (Boolean) -> Unit,
) {
  ListItem(
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(hint)) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onChange,
      ),
  )
}

@Composable
private fun FrequencyPicker(cronRule: String?, enabled: Boolean, onPick: (String?) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val current = SCAN_FREQUENCIES.firstOrNull { it.cronRule == cronRule }
  val label =
    if (current != null) stringResource(current.label)
    else stringResource(Res.string.settings_frequency_custom, cronRule.orEmpty())
  Column(
    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      stringResource(Res.string.settings_frequency_title),
      style = MaterialTheme.typography.bodyLarge,
    )
    Text(
      stringResource(Res.string.settings_frequency_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = it && enabled },
    ) {
      OutlinedTextField(
        value = label,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(Res.string.settings_frequency_title)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier =
          Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SCAN_FREQUENCIES.forEach { option ->
          DropdownMenuItem(
            text = { Text(stringResource(option.label)) },
            leadingIcon =
              if (ScannerSettings.isRisky(option.cronRule)) {
                {
                  Icon(
                    Icons.Rounded.Warning,
                    contentDescription = stringResource(Res.string.settings_frequency_risky),
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              } else {
                null
              },
            onClick = {
              expanded = false
              onPick(option.cronRule)
            },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
          )
        }
      }
    }
    if (ScannerSettings.isRisky(cronRule)) {
      Text(
        stringResource(Res.string.settings_frequency_risky_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

/** The scanning section while its settings can't be read: the computer is out of reach. */
@Composable
private fun ScannerUnavailable(onRetry: () -> Unit) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.settings_load_failed),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
    )
    OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
      Text(stringResource(Res.string.common_retry))
    }
  }
}
