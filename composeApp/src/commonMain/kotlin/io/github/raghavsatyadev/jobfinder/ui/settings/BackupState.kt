@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart

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
