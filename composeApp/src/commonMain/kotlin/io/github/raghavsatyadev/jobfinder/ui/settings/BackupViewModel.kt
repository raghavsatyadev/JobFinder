@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupCrypto
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFormatException
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupOtherDeviceException
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupPasswordException
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.ImportSummary
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.time.localOffsetMs
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
