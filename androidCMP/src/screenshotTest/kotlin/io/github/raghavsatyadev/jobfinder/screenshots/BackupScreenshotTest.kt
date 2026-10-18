package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.ui.settings.BackupContent
import io.github.raghavsatyadev.jobfinder.ui.settings.BackupResult
import io.github.raghavsatyadev.jobfinder.ui.settings.BackupUiState
import io.github.raghavsatyadev.jobfinder.ui.settings.ExportLock
import io.github.raghavsatyadev.jobfinder.ui.settings.ImportUnlock
import io.github.raghavsatyadev.jobfinder.ui.settings.KeysNote
import io.github.raghavsatyadev.jobfinder.ui.settings.PendingExport

/** Desktop export: the password typed twice, a fair strength, the second one still different. */
@PreviewTest
@StatePreviews
@Composable
fun BackupExportPasswordScreenshot() {
  Backup(
    BackupUiState(
      desktop = true,
      exportLock = ExportLock(PendingExport.Everything, "Summer jobs 2026", "Summer jobs"),
    )
  )
}

/** Phone export: too short yet, so Export is off; the plain file has no keys to leave out. */
@PreviewTest
@StatePreviews
@Composable
fun BackupExportPasswordPhoneScreenshot() {
  Backup(BackupUiState(exportLock = ExportLock(PendingExport.Everything, "jobs", "")))
}

/** Import of a locked file after a wrong password. */
@PreviewTest
@StatePreviews
@Composable
fun BackupImportPasswordScreenshot() {
  Backup(
    BackupUiState(
      desktop = true,
      importUnlock = ImportUnlock(ByteArray(0), password = "wrong password", wrong = true),
    )
  )
}

/** A locked export saved on the computer, with its API keys. */
@PreviewTest
@StatePreviews
@Composable
fun BackupSavedWithKeysScreenshot() {
  Backup(BackupUiState(desktop = true, result = BackupResult.Saved, keysNote = KeysNote.Saved(3)))
}

@Composable
private fun Backup(state: BackupUiState) {
  ScreenshotHost { BackupContent(state, onEvent = {}) }
}
