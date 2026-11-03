@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.backup_auto_hint
import io.github.raghavsatyadev.jobfinder.resources.backup_export_all
import io.github.raghavsatyadev.jobfinder.resources.backup_export_csv
import io.github.raghavsatyadev.jobfinder.resources.backup_export_next
import io.github.raghavsatyadev.jobfinder.resources.backup_export_settings
import io.github.raghavsatyadev.jobfinder.resources.backup_export_text
import io.github.raghavsatyadev.jobfinder.resources.backup_export_title
import io.github.raghavsatyadev.jobfinder.resources.backup_failed
import io.github.raghavsatyadev.jobfinder.resources.backup_hint
import io.github.raghavsatyadev.jobfinder.resources.backup_import
import io.github.raghavsatyadev.jobfinder.resources.backup_import_button
import io.github.raghavsatyadev.jobfinder.resources.backup_import_full_text
import io.github.raghavsatyadev.jobfinder.resources.backup_import_full_title
import io.github.raghavsatyadev.jobfinder.resources.backup_import_settings_text
import io.github.raghavsatyadev.jobfinder.resources.backup_import_settings_title
import io.github.raghavsatyadev.jobfinder.resources.backup_imported_full
import io.github.raghavsatyadev.jobfinder.resources.backup_imported_replaced
import io.github.raghavsatyadev.jobfinder.resources.backup_imported_settings
import io.github.raghavsatyadev.jobfinder.resources.backup_imported_waiting
import io.github.raghavsatyadev.jobfinder.resources.backup_keys_ignored
import io.github.raghavsatyadev.jobfinder.resources.backup_keys_import_desktop
import io.github.raghavsatyadev.jobfinder.resources.backup_keys_import_phone
import io.github.raghavsatyadev.jobfinder.resources.backup_keys_restored
import io.github.raghavsatyadev.jobfinder.resources.backup_keys_saved
import io.github.raghavsatyadev.jobfinder.resources.backup_mode_merge
import io.github.raghavsatyadev.jobfinder.resources.backup_mode_merge_phone
import io.github.raghavsatyadev.jobfinder.resources.backup_mode_replace
import io.github.raghavsatyadev.jobfinder.resources.backup_mode_replace_phone
import io.github.raghavsatyadev.jobfinder.resources.backup_part_ai
import io.github.raghavsatyadev.jobfinder.resources.backup_part_detail_layout
import io.github.raghavsatyadev.jobfinder.resources.backup_part_matching
import io.github.raghavsatyadev.jobfinder.resources.backup_part_prompts
import io.github.raghavsatyadev.jobfinder.resources.backup_part_resume
import io.github.raghavsatyadev.jobfinder.resources.backup_part_scanner
import io.github.raghavsatyadev.jobfinder.resources.backup_part_searches
import io.github.raghavsatyadev.jobfinder.resources.backup_part_searches_count
import io.github.raghavsatyadev.jobfinder.resources.backup_part_server
import io.github.raghavsatyadev.jobfinder.resources.backup_saved
import io.github.raghavsatyadev.jobfinder.resources.backup_saved_partial
import io.github.raghavsatyadev.jobfinder.resources.backup_working
import io.github.raghavsatyadev.jobfinder.resources.backup_working_allow
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.ic_file_download
import io.github.raghavsatyadev.jobfinder.resources.ic_file_upload
import io.github.raghavsatyadev.jobfinder.resources.ic_inventory_2
import io.github.raghavsatyadev.jobfinder.resources.ic_table_chart
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

/** The backup section of Settings, from its ViewModel; nothing where backups are not available. */
@Composable
fun BackupSettings() {
  val viewModel = koinViewModel<BackupViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  BackupContent(state, viewModel::onEvent)
}

@Composable
fun BackupContent(state: BackupUiState, onEvent: (BackupUiEvent) -> Unit) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.backup_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.available)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = { onEvent(BackupUiEvent.ExportSettings) },
          enabled = !state.working,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(
            vectorResource(Res.drawable.ic_file_download),
            null,
            Modifier.size(ButtonDefaults.IconSize),
          )
          Text(
            stringResource(Res.string.backup_export_settings),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
        OutlinedButton(
          onClick = { onEvent(BackupUiEvent.ExportAll) },
          enabled = !state.working,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(
            vectorResource(Res.drawable.ic_inventory_2),
            null,
            Modifier.size(ButtonDefaults.IconSize),
          )
          Text(
            stringResource(Res.string.backup_export_all),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
        OutlinedButton(
          onClick = { onEvent(BackupUiEvent.Import) },
          enabled = !state.working,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(
            vectorResource(Res.drawable.ic_file_upload),
            null,
            Modifier.size(ButtonDefaults.IconSize),
          )
          Text(
            stringResource(Res.string.backup_import),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
        OutlinedButton(
          onClick = { onEvent(BackupUiEvent.ExportJobsCsv) },
          enabled = !state.working,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(
            vectorResource(Res.drawable.ic_table_chart),
            null,
            Modifier.size(ButtonDefaults.IconSize),
          )
          Text(
            stringResource(Res.string.backup_export_csv),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
      }
    if (state.working) {
      Text(
        stringResource(
          if (state.awaitingApproval) Res.string.backup_working_allow else Res.string.backup_working
        ),
        style = MaterialTheme.typography.bodyMedium,
      )
      WavyProgress()
    }
    state.result?.let { ResultText(it) }
    state.keysNote
      ?.takeIf { state.result != null && state.result !is BackupResult.Failed }
      ?.let { KeysNoteText(it) }
    if (state.desktop) {
      Text(
        stringResource(Res.string.backup_auto_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
  state.exporting?.let { parts ->
    PartsDialog(
      title = stringResource(Res.string.backup_export_title),
      text = stringResource(Res.string.backup_export_text),
      offered = SettingsPart.entries.filter { it != SettingsPart.SERVER || state.desktop },
      ticked = parts,
      searches = null,
      keysNote = null,
      confirmLabel = stringResource(Res.string.backup_export_next),
      onToggle = { onEvent(BackupUiEvent.ToggleExportPart(it)) },
      onConfirm = { onEvent(BackupUiEvent.ConfirmExport) },
      onDismiss = { onEvent(BackupUiEvent.Dismiss) },
    )
  }
  state.settingsImport?.let { chosen ->
    PartsDialog(
      title = stringResource(Res.string.backup_import_settings_title),
      text = stringResource(Res.string.backup_import_settings_text),
      offered = SettingsPart.entries.filter { it in chosen.backup.parts },
      ticked = chosen.parts,
      searches = chosen.backup.searches?.size,
      keysNote = importKeysNote(chosen.apiKeys.size, state.desktop),
      confirmLabel = stringResource(Res.string.backup_import_button),
      onToggle = { onEvent(BackupUiEvent.ToggleImportPart(it)) },
      onConfirm = { onEvent(BackupUiEvent.ConfirmSettingsImport) },
      onDismiss = { onEvent(BackupUiEvent.Dismiss) },
    )
  }
  state.fullImport?.let { FullImportDialog(it, state.desktop, onEvent) }
  state.exportLock?.let { ExportPasswordDialog(it, state.desktop, state.deviceLock, onEvent) }
  state.importUnlock?.let { ImportPasswordDialog(it, onEvent) }
}

/** What happens to the API keys of a file being imported; `null` when it has none. */
@Composable
internal fun importKeysNote(count: Int, desktop: Boolean): String? =
  when {
    count == 0 -> null
    desktop -> stringResource(Res.string.backup_keys_import_desktop, count)
    else -> stringResource(Res.string.backup_keys_import_phone, count)
  }

@Composable
internal fun KeysNoteText(note: KeysNote) {
  Text(
    when (note) {
      is KeysNote.Saved -> stringResource(Res.string.backup_keys_saved, note.count)
      is KeysNote.Restored -> stringResource(Res.string.backup_keys_restored, note.added)
      is KeysNote.Ignored -> stringResource(Res.string.backup_keys_ignored, note.count)
    },
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
internal fun ResultText(result: BackupResult) {
  val text =
    when (result) {
      BackupResult.Saved -> stringResource(Res.string.backup_saved)
      is BackupResult.SavedPartial -> stringResource(Res.string.backup_saved_partial, result.jobs)
      is BackupResult.Settings ->
        stringResource(Res.string.backup_imported_settings, result.searches)
      is BackupResult.Full ->
        stringResource(Res.string.backup_imported_full, result.jobs, result.searches)
      is BackupResult.Replaced ->
        stringResource(Res.string.backup_imported_replaced, result.jobs, result.savedTo)
      is BackupResult.Waiting -> stringResource(Res.string.backup_imported_waiting, result.jobs)
      is BackupResult.Failed -> result.message ?: stringResource(Res.string.backup_failed)
    }
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color =
      if (result is BackupResult.Failed) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.onSurface,
  )
}

internal val SettingsPart.label: StringResource
  get() =
    when (this) {
      SettingsPart.SEARCHES -> Res.string.backup_part_searches
      SettingsPart.MATCHING -> Res.string.backup_part_matching
      SettingsPart.SCANNER -> Res.string.backup_part_scanner
      SettingsPart.RESUME -> Res.string.backup_part_resume
      SettingsPart.SERVER -> Res.string.backup_part_server
      SettingsPart.AI -> Res.string.backup_part_ai
      SettingsPart.PROMPTS -> Res.string.backup_part_prompts
      SettingsPart.DETAIL_LAYOUT -> Res.string.backup_part_detail_layout
    }

@Composable
internal fun PartsDialog(
  title: String,
  text: String,
  offered: List<SettingsPart>,
  ticked: Set<SettingsPart>,
  /** How many searches the file holds, for the import dialog. */
  searches: Int?,
  /** What happens to the file's API keys, for the import dialog. */
  keysNote: String?,
  confirmLabel: String,
  onToggle: (SettingsPart) -> Unit,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(text, Modifier.padding(bottom = 8.dp))
        offered.forEach { part ->
          Row(
            Modifier.fillMaxWidth()
              .toggleable(part in ticked, role = Role.Checkbox) { onToggle(part) }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(part in ticked, onCheckedChange = null)
            Text(
              if (part == SettingsPart.SEARCHES && searches != null)
                stringResource(Res.string.backup_part_searches_count, searches)
              else stringResource(part.label),
              Modifier.padding(start = 12.dp),
            )
          }
        }
        keysNote?.let { Text(it, Modifier.padding(top = 8.dp)) }
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm, enabled = ticked.any { it in offered }) { Text(confirmLabel) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
    },
  )
}

@Composable
internal fun FullImportDialog(
  chosen: FullImport,
  desktop: Boolean,
  onEvent: (BackupUiEvent) -> Unit,
) {
  val m = chosen.manifest
  AlertDialog(
    onDismissRequest = { onEvent(BackupUiEvent.Dismiss) },
    title = { Text(stringResource(Res.string.backup_import_full_title)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
          stringResource(
            Res.string.backup_import_full_text,
            m.jobs,
            m.searches,
            m.notes,
            isoDate(m.exportedAt),
          ),
          Modifier.padding(bottom = 8.dp),
        )
        // The phone's own copy can only be added.
        val modes = if (m.partial) listOf(ImportMode.MERGE) else ImportMode.entries
        modes.forEach { mode ->
          Row(
            Modifier.fillMaxWidth()
              .selectable(chosen.mode == mode, role = Role.RadioButton) {
                onEvent(BackupUiEvent.SetMode(mode))
              }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(chosen.mode == mode, onClick = null)
            Text(
              stringResource(
                when {
                  mode == ImportMode.MERGE && desktop -> Res.string.backup_mode_merge
                  mode == ImportMode.MERGE -> Res.string.backup_mode_merge_phone
                  desktop -> Res.string.backup_mode_replace
                  else -> Res.string.backup_mode_replace_phone
                }
              ),
              Modifier.padding(start = 12.dp),
            )
          }
        }
        importKeysNote(chosen.apiKeys.size, desktop)?.let {
          Text(it, Modifier.padding(top = 8.dp))
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onEvent(BackupUiEvent.ConfirmFullImport) }) {
        Text(
          stringResource(Res.string.backup_import_button),
          color =
            if (chosen.mode == ImportMode.REPLACE) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(BackupUiEvent.Dismiss) }) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}
