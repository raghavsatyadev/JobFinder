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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.TableChart
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
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
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupCrypto
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFormatException
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupOtherDeviceException
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupPasswordException
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.ImportSummary
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.time.localOffsetMs
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobsClock
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * A settings backup picked for import, and the parts the user keeps ticked. [apiKeys] came with it
 * in a locked file.
 */
@Immutable
data class SettingsImport(
  val backup: SettingsBackup,
  val parts: Set<SettingsPart>,
  val apiKeys: List<BackupApiKey> = emptyList(),
)

/** A full backup picked for import: what it holds and how it goes in. */
@Immutable
class FullImport(
  val bytes: ByteArray,
  val manifest: BackupManifest,
  val mode: ImportMode,
  /** The online API keys that came with it in a locked file. */
  val apiKeys: List<BackupApiKey> = emptyList(),
) {
  fun copy(mode: ImportMode) = FullImport(bytes, manifest, mode, apiKeys)
}

/** What an export saves once its password is chosen. */
sealed interface PendingExport {
  data class Settings(val parts: Set<SettingsPart>) : PendingExport

  data object Everything : PendingExport
}

/** The password dialog of an export: the password, typed twice. */
@Immutable
data class ExportLock(
  val export: PendingExport,
  val password: String = "",
  val again: String = "",
) {
  val strength: PasswordStrength
    get() = PasswordStrength.of(password)

  /** Long enough and typed the same twice. */
  val ready: Boolean
    get() = strength != PasswordStrength.TOO_SHORT && password == again

  override fun toString() = "ExportLock(export=$export)"
}

/** A locked backup picked for import, waiting for its password. */
@Immutable
class ImportUnlock(
  val bytes: ByteArray,
  val password: String = "",
  /** The last password tried did not open it. */
  val wrong: Boolean = false,
  /** The password is being checked (it takes a moment on purpose). */
  val opening: Boolean = false,
) {
  fun copy(
    password: String = this.password,
    wrong: Boolean = this.wrong,
    opening: Boolean = false,
  ) = ImportUnlock(bytes, password, wrong, opening)

  override fun toString() = "ImportUnlock(wrong=$wrong, opening=$opening)"
}

/** What happened to online API keys in the last export or import. */
sealed interface KeysNote {
  /** A locked export from the computer holds [count] keys. */
  data class Saved(val count: Int) : KeysNote

  /** [added] keys from the file were added to this computer (the rest were here already). */
  data class Restored(val added: Int) : KeysNote

  /** The phone never keeps API keys: the file's [count] keys were ignored. */
  data class Ignored(val count: Int) : KeysNote
}

/** What the last export or import did. */
sealed interface BackupResult {
  data object Saved : BackupResult

  /** The computer was out of reach: only the phone's copy, [jobs] jobs, was saved. */
  data class SavedPartial(val jobs: Int) : BackupResult

  data class Settings(val searches: Int) : BackupResult

  data class Full(val jobs: Int, val searches: Int) : BackupResult

  data class Replaced(val jobs: Int, val savedTo: String) : BackupResult

  data class Waiting(val jobs: Int) : BackupResult

  /** [message] is the computer's or the file check's text; `null` shows the generic one. */
  data class Failed(val message: String?) : BackupResult
}

@Immutable
data class BackupUiState(
  /** `false` where there is no file dialog or no backups: the section hides. */
  val available: Boolean = true,
  /** On the computer: the port part and the daily backups show, and a replace needs no approval. */
  val desktop: Boolean = false,
  /** This device keeps its own backup key: an export can be saved without a password. */
  val deviceLock: Boolean = true,
  val working: Boolean = false,
  /** A replace sent from the phone waits for the user's yes on the computer. */
  val awaitingApproval: Boolean = false,
  /** The export dialog is open with these parts ticked. */
  val exporting: Set<SettingsPart>? = null,
  val settingsImport: SettingsImport? = null,
  val fullImport: FullImport? = null,
  /** The export's password dialog is open. */
  val exportLock: ExportLock? = null,
  /** A locked file asks for its password. */
  val importUnlock: ImportUnlock? = null,
  val result: BackupResult? = null,
  /** Shown under [result]: what happened to the API keys. */
  val keysNote: KeysNote? = null,
)

sealed interface BackupUiEvent {
  data object ExportSettings : BackupUiEvent

  data class ToggleExportPart(val part: SettingsPart) : BackupUiEvent

  data object ConfirmExport : BackupUiEvent

  data object ExportAll : BackupUiEvent

  /** Saves every job as a CSV spreadsheet. */
  data object ExportJobsCsv : BackupUiEvent

  data object Import : BackupUiEvent

  data class ToggleImportPart(val part: SettingsPart) : BackupUiEvent

  data object ConfirmSettingsImport : BackupUiEvent

  data class SetMode(val mode: ImportMode) : BackupUiEvent

  data object ConfirmFullImport : BackupUiEvent

  data class SetPassword(val password: String) : BackupUiEvent {
    override fun toString() = "SetPassword"
  }

  data class SetPasswordAgain(val password: String) : BackupUiEvent {
    override fun toString() = "SetPasswordAgain"
  }

  /** Saves the export locked with the password. */
  data object ExportLocked : BackupUiEvent

  /** Saves the export locked with this device's own key: only this device opens it. */
  data object ExportDevice : BackupUiEvent

  data class SetImportPassword(val password: String) : BackupUiEvent {
    override fun toString() = "SetImportPassword"
  }

  data object OpenLocked : BackupUiEvent

  data object Dismiss : BackupUiEvent
}

/**
 * Settings and full backups, on both apps: export to a file the user picks, import from one. Every
 * export is locked ([BackupCrypto]): with a password the user chooses, so it opens anywhere, or
 * with this device's own key, so it opens only here. On the computer it carries the online API keys
 * too. A password-locked file asks for its password first; plain files from older apps still
 * import. A picked settings file asks which parts to bring in; a full backup shows what it holds
 * and whether to add or replace (from the phone, a replace waits for the user's yes on the
 * computer). The phone never keeps API keys: a file's keys are ignored there.
 */
class BackupViewModel(
  private val backups: BackupRepository?,
  private val picker: FilePicker?,
  private val clock: JobsClock,
  desktop: Boolean,
  /** The computer's online keys; `null` on the phone, which never holds them. */
  private val aiKeys: AiKeys? = null,
  /** This device's own backup key, for files saved without a password. */
  private val deviceKeys: DeviceBackupKeys? = null,
  /** Reads picked files and locks or opens them off the main thread. */
  private val reader: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
  private val _state =
    MutableStateFlow(
      BackupUiState(
        available = backups != null && picker != null,
        desktop = desktop,
        deviceLock = deviceKeys != null,
      )
    )
  val state: StateFlow<BackupUiState> = _state.asStateFlow()

  fun onEvent(event: BackupUiEvent) {
    when (event) {
      BackupUiEvent.ExportSettings ->
        _state.update { it.copy(exporting = SettingsPart.DEFAULT, result = null) }
      is BackupUiEvent.ToggleExportPart ->
        _state.update { s -> s.copy(exporting = s.exporting?.toggle(event.part)) }
      BackupUiEvent.ConfirmExport -> chooseSettings()
      BackupUiEvent.ExportJobsCsv -> exportJobsCsv()
      BackupUiEvent.ExportAll ->
        _state.update {
          it.copy(exportLock = ExportLock(PendingExport.Everything), result = null, keysNote = null)
        }
      is BackupUiEvent.SetPassword ->
        _state.update { s -> s.copy(exportLock = s.exportLock?.copy(password = event.password)) }
      is BackupUiEvent.SetPasswordAgain ->
        _state.update { s -> s.copy(exportLock = s.exportLock?.copy(again = event.password)) }
      BackupUiEvent.ExportLocked -> exportLocked()
      BackupUiEvent.ExportDevice -> exportDevice()
      is BackupUiEvent.SetImportPassword ->
        _state.update { s ->
          s.copy(importUnlock = s.importUnlock?.copy(password = event.password, wrong = false))
        }
      BackupUiEvent.OpenLocked -> openLocked()
      BackupUiEvent.Import -> pickImport()
      is BackupUiEvent.ToggleImportPart ->
        _state.update { s ->
          s.copy(
            settingsImport = s.settingsImport?.let { it.copy(parts = it.parts.toggle(event.part)) }
          )
        }
      BackupUiEvent.ConfirmSettingsImport -> importSettings()
      is BackupUiEvent.SetMode ->
        _state.update { s -> s.copy(fullImport = s.fullImport?.copy(mode = event.mode)) }
      BackupUiEvent.ConfirmFullImport -> importFull()
      BackupUiEvent.Dismiss ->
        _state.update {
          if (it.importUnlock?.opening == true) it
          else
            it.copy(
              exporting = null,
              settingsImport = null,
              fullImport = null,
              exportLock = null,
              importUnlock = null,
            )
        }
    }
  }

  /** The settings parts are chosen: the password comes next. */
  private fun chooseSettings() {
    val parts = _state.value.exporting?.takeIf { it.isNotEmpty() } ?: return
    _state.update {
      it.copy(
        exporting = null,
        exportLock = ExportLock(PendingExport.Settings(parts)),
        keysNote = null,
      )
    }
  }

  private fun exportLocked() {
    val lock = _state.value.exportLock?.takeIf { it.ready } ?: return
    export(lock.export) { bytes, keys -> BackupCrypto.lock(bytes, keys, lock.password) }
  }

  /** Locked with this device's own key: no password to remember, but only this device opens it. */
  private fun exportDevice() {
    val export = _state.value.exportLock?.export ?: return
    val deviceKeys = deviceKeys ?: return
    export(export) { bytes, keys ->
      val key = deviceKeys.get() ?: throw BackupFormatException(DEVICE_KEY_MISSING)
      BackupCrypto.lock(bytes, keys, key)
    }
  }

  /** Saves [export] locked by [lock], with the computer's API keys. */
  private fun export(
    export: PendingExport,
    lock: suspend (ByteArray, List<BackupApiKey>) -> ByteArray,
  ) {
    _state.update { it.copy(exportLock = null) }
    work {
      val file = exportFile(export)
      val keys = aiKeys?.backupKeys().orEmpty()
      val locked = withContext(reader) { lock(file.bytes, keys) }
      if (keys.isNotEmpty()) _state.update { it.copy(keysNote = KeysNote.Saved(keys.size)) }
      save("${file.name}.$LOCKED_EXTENSION", OCTET_STREAM, locked)?.let { file.result(it) }
    }
  }

  /** Jobs only, as plain data: no settings and no keys, so no password either. */
  private fun exportJobsCsv() {
    val backups = backups ?: return
    _state.update { it.copy(result = null, keysNote = null) }
    work {
      val bytes = backups.exportJobsCsv()
      save("jobfinder-jobs-${isoDate(clock.now())}.csv", CSV, bytes)
    }
  }

  /** A plain backup file to lock and save, as [BackupFiles] writes it. */
  private class ExportFile(
    val name: String,
    val bytes: ByteArray,
    /** Jobs in the phone's own copy, saved when the computer was out of reach. */
    val partialJobs: Int? = null,
  ) {
    fun result(saved: BackupResult): BackupResult =
      partialJobs?.let { BackupResult.SavedPartial(it) } ?: saved
  }

  private suspend fun exportFile(export: PendingExport): ExportFile {
    val date = isoDate(clock.now())
    return when (export) {
      is PendingExport.Settings -> {
        val backup = requireNotNull(backups).exportSettings(export.parts)
        ExportFile("jobfinder-settings-$date", BackupFiles.encodeSettings(backup))
      }
      PendingExport.Everything -> {
        val bytes = requireNotNull(backups).exportFull()
        val manifest = withContext(reader) { BackupFiles.readManifest(bytes) }
        val name = if (manifest.partial) "jobfinder-phone-copy" else "jobfinder-backup"
        ExportFile("$name-$date", bytes, partialJobs = manifest.jobs.takeIf { manifest.partial })
      }
    }
  }

  private suspend fun save(name: String, mime: String, bytes: ByteArray): BackupResult? =
    if (requireNotNull(picker).save(name, mime, bytes)) BackupResult.Saved else null

  private fun pickImport() {
    val picker = picker ?: return
    if (_state.value.working) return
    _state.update { it.copy(result = null, keysNote = null) }
    viewModelScope.launch {
      val file =
        try {
          picker.pickBackup()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        } ?: return@launch
      when {
        BackupCrypto.isDeviceLocked(file.bytes) -> openDeviceLocked(file.bytes)
        BackupCrypto.isLocked(file.bytes) ->
          _state.update { it.copy(importUnlock = ImportUnlock(file.bytes)) }
        else -> readPlain(file.bytes, emptyList())
      }
    }
  }

  /**
   * A file saved without a password: this device's key opens it, or it says another one locked it.
   */
  private suspend fun openDeviceLocked(bytes: ByteArray) {
    try {
      val key = deviceKeys?.get() ?: throw BackupOtherDeviceException()
      val opened = withContext(reader) { BackupCrypto.open(bytes, key) }
      readPlain(opened.content, opened.apiKeys)
    } catch (e: CancellationException) {
      throw e
    } catch (e: BackupFormatException) {
      _state.update { it.copy(result = BackupResult.Failed(e.message)) }
    } catch (_: Exception) {
      _state.update { it.copy(result = BackupResult.Failed(null)) }
    }
  }

  /** Tries the typed password; a wrong one keeps the dialog open and says so. */
  private fun openLocked() {
    val unlock = _state.value.importUnlock ?: return
    if (unlock.opening || unlock.password.isEmpty()) return
    _state.update { it.copy(importUnlock = unlock.copy(wrong = false, opening = true)) }
    viewModelScope.launch {
      try {
        val opened = withContext(reader) { BackupCrypto.open(unlock.bytes, unlock.password) }
        _state.update { it.copy(importUnlock = null) }
        readPlain(opened.content, opened.apiKeys)
      } catch (e: CancellationException) {
        throw e
      } catch (_: BackupPasswordException) {
        _state.update { it.copy(importUnlock = unlock.copy(wrong = true)) }
      } catch (e: BackupFormatException) {
        _state.update { it.copy(importUnlock = null, result = BackupResult.Failed(e.message)) }
      } catch (_: Exception) {
        _state.update { it.copy(importUnlock = null, result = BackupResult.Failed(null)) }
      }
    }
  }

  /** A plain backup file, picked or opened: asks which parts, or how to bring it in. */
  private suspend fun readPlain(bytes: ByteArray, apiKeys: List<BackupApiKey>) {
    try {
      withContext(reader) {
        if (BackupFiles.isZip(bytes)) {
          val manifest = BackupFiles.readManifest(bytes)
          _state.update {
            it.copy(fullImport = FullImport(bytes, manifest, ImportMode.MERGE, apiKeys))
          }
        } else {
          val backup = BackupFiles.decodeSettings(bytes)
          // The port and network stay unticked: another computer may need others.
          val parts = backup.parts - SettingsPart.SERVER
          _state.update { it.copy(settingsImport = SettingsImport(backup, parts, apiKeys)) }
        }
      }
    } catch (e: BackupFormatException) {
      _state.update { it.copy(result = BackupResult.Failed(e.message)) }
    }
  }

  private fun importSettings() {
    val chosen = _state.value.settingsImport ?: return
    if (chosen.parts.isEmpty()) return
    _state.update { it.copy(settingsImport = null) }
    work {
      val summary = requireNotNull(backups).importSettings(chosen.backup, chosen.parts)
      restoreKeys(chosen.apiKeys)
      BackupResult.Settings(summary.searchesAdded)
    }
  }

  private fun importFull() {
    val chosen = _state.value.fullImport ?: return
    val approval = chosen.mode == ImportMode.REPLACE && !_state.value.desktop
    _state.update { it.copy(fullImport = null, awaitingApproval = approval) }
    work {
      val summary = requireNotNull(backups).importFull(chosen.bytes, chosen.mode)
      restoreKeys(chosen.apiKeys)
      summary.toResult()
    }
  }

  /** The file's API keys go into this computer's protected store; the phone ignores them. */
  private suspend fun restoreKeys(apiKeys: List<BackupApiKey>) {
    if (apiKeys.isEmpty()) return
    val note = aiKeys?.let { KeysNote.Restored(it.restoreKeys(apiKeys)) }
    _state.update { it.copy(keysNote = note ?: KeysNote.Ignored(apiKeys.size)) }
  }

  /** Runs [block] with the progress shown; `null` from it (cancelled) shows nothing. */
  private fun work(block: suspend () -> BackupResult?) {
    if (_state.value.working) return
    _state.update { it.copy(working = true, result = null, keysNote = null) }
    viewModelScope.launch {
      val result =
        try {
          block()
        } catch (e: CancellationException) {
          throw e
        } catch (e: BackupFormatException) {
          BackupResult.Failed(e.message)
        } catch (e: ApiFailure) {
          BackupResult.Failed(e.message)
        } catch (_: Exception) {
          BackupResult.Failed(null)
        }
      _state.update { it.copy(working = false, awaitingApproval = false, result = result) }
    }
  }

  private fun ImportSummary.toResult(): BackupResult =
    when {
      waitingForComputer -> BackupResult.Waiting(jobsAdded)
      replaced -> BackupResult.Replaced(jobsAdded, savedBefore.orEmpty())
      else -> BackupResult.Full(jobsAdded, searchesAdded)
    }

  private fun Set<SettingsPart>.toggle(part: SettingsPart) =
    if (part in this) this - part else this + part

  companion object {
    const val CSV = "text/csv"
    const val OCTET_STREAM = "application/octet-stream"

    /** A password-locked backup, settings or everything. */
    const val LOCKED_EXTENSION = "f2a"

    private const val DEVICE_KEY_MISSING =
      "This device can't keep a backup key. Lock the file with a password instead."
  }
}

/** `yyyy-MM-dd` of [epochMs] in this device's time zone, for file names and the import dialog. */
fun isoDate(epochMs: Long): String {
  // Days to a civil date (Howard Hinnant's algorithm).
  val z = (epochMs + localOffsetMs(epochMs)).floorDiv(86_400_000L) + 719_468
  val era = z.floorDiv(146_097L)
  val doe = z - era * 146_097
  val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
  val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
  val mp = (5 * doy + 2) / 153
  val d = doy - (153 * mp + 2) / 5 + 1
  val m = if (mp < 10) mp + 3 else mp - 9
  val y = yoe + era * 400 + if (m <= 2) 1 else 0
  return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

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
          Icon(Icons.Outlined.FileDownload, null, Modifier.size(ButtonDefaults.IconSize))
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
          Icon(Icons.Outlined.Inventory2, null, Modifier.size(ButtonDefaults.IconSize))
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
          Icon(Icons.Outlined.FileUpload, null, Modifier.size(ButtonDefaults.IconSize))
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
          Icon(Icons.Outlined.TableChart, null, Modifier.size(ButtonDefaults.IconSize))
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
private fun importKeysNote(count: Int, desktop: Boolean): String? =
  when {
    count == 0 -> null
    desktop -> stringResource(Res.string.backup_keys_import_desktop, count)
    else -> stringResource(Res.string.backup_keys_import_phone, count)
  }

@Composable
private fun KeysNoteText(note: KeysNote) {
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
private fun ResultText(result: BackupResult) {
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

private val SettingsPart.label: StringResource
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
private fun PartsDialog(
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
private fun FullImportDialog(
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
