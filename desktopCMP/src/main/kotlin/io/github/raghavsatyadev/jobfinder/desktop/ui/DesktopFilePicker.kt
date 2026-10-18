package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.backup_dialog_open
import io.github.raghavsatyadev.jobfinder.resources.backup_dialog_save
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_dialog_title
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.github.raghavsatyadev.jobfinder.ui.platform.PickedFile
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/** The native open and save dialogs on the Swing thread, over the active app window. */
class DesktopFilePicker : FilePicker {
  override suspend fun pickResume(): PickedFile? {
    val file =
      choose(getString(Res.string.settings_resume_dialog_title), FileDialog.LOAD, RESUME_PATTERN) {
        it.substringAfterLast('.').lowercase() in TYPES
      } ?: return null
    return withContext(Dispatchers.IO) {
      // A file too large is still sent, so the computer says why it is refused.
      val bytes = file.inputStream().use { it.readNBytes(AtsRepository.MAX_RESUME_BYTES + 1) }
      PickedFile(file.name, bytes)
    }
  }

  override suspend fun pickBackup(): PickedFile? {
    val file =
      choose(getString(Res.string.backup_dialog_open), FileDialog.LOAD, BACKUP_PATTERN) {
        it.substringAfterLast('.').lowercase() in BACKUP_TYPES
      } ?: return null
    return withContext(Dispatchers.IO) {
      if (file.length() > BackupFiles.MAX_UNPACKED_BYTES) null
      else PickedFile(file.name, file.readBytes())
    }
  }

  override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
    val file =
      choose(getString(Res.string.backup_dialog_save), FileDialog.SAVE, fileName) { true }
        ?: return false
    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
    return true
  }

  private suspend fun choose(
    title: String,
    mode: Int,
    file: String,
    accept: (String) -> Boolean,
  ): File? =
    withContext(Dispatchers.Swing) {
      val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
      val dialog = FileDialog(owner, title, mode)
      // Windows filters by this pattern (or suggests this name); other systems use the filter.
      dialog.file = file
      if (mode == FileDialog.LOAD) dialog.setFilenameFilter { _, name -> accept(name) }
      dialog.isVisible = true
      val name = dialog.file
      val directory = dialog.directory
      dialog.dispose()
      name?.let { if (directory == null) File(it) else File(directory, it) }
    }

  private companion object {
    val TYPES = setOf("pdf", "docx", "txt", "md")
    const val RESUME_PATTERN = "*.pdf;*.docx;*.txt;*.md"
    val BACKUP_TYPES = setOf("f2a", "json", "zip")
    const val BACKUP_PATTERN = "*.f2a;*.json;*.zip"
  }
}
