package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.backup_dialog_open
import io.github.raghavsatyadev.jobfinder.resources.backup_dialog_save
import io.github.raghavsatyadev.jobfinder.resources.linkedin_import
import io.github.raghavsatyadev.jobfinder.resources.settings_resume_dialog_title
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.github.raghavsatyadev.jobfinder.ui.platform.PickedFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import java.awt.KeyboardFocusManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * The system open and save dialogs (the modern Explorer picker on Windows), over the active app
 * window.
 */
class DesktopFilePicker : FilePicker {
  override suspend fun pickResume(): PickedFile? {
    val file = open(getString(Res.string.settings_resume_dialog_title), TYPES) ?: return null
    return withContext(Dispatchers.IO) {
      // A file too large is still sent, so the computer says why it is refused.
      val bytes = file.inputStream().use { it.readNBytes(AtsRepository.MAX_RESUME_BYTES + 1) }
      PickedFile(file.name, bytes)
    }
  }

  override suspend fun pickBackup(): PickedFile? {
    val file = open(getString(Res.string.backup_dialog_open), BACKUP_TYPES) ?: return null
    return withContext(Dispatchers.IO) {
      if (file.length() > BackupFiles.MAX_UNPACKED_BYTES) null
      else PickedFile(file.name, file.readBytes())
    }
  }

  override suspend fun pickLinkedInExport(): PickedFile? {
    val file = open(getString(Res.string.linkedin_import), setOf("zip")) ?: return null
    return withContext(Dispatchers.IO) {
      // A file too large is still sent, so the computer says why it is refused.
      val bytes = file.inputStream().use { it.readNBytes(ResumePdfRepository.MAX_EXPORT_BYTES + 1) }
      PickedFile(file.name, bytes)
    }
  }

  override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
    val extension = fileName.substringAfterLast('.', "").ifEmpty { null }
    val file =
      FileKit.openFileSaver(
          suggestedName = fileName,
          defaultExtension = extension,
          dialogSettings = settings(getString(Res.string.backup_dialog_save)),
        )
        ?.file ?: return false
    withContext(Dispatchers.IO) { file.writeBytes(bytes) }
    return true
  }

  private suspend fun open(title: String, extensions: Set<String>): File? =
    FileKit.openFilePicker(FileKitType.File(extensions), dialogSettings = settings(title))?.file

  /**
   * Owned by the focused app window, read on the Swing thread, so the dialog stays on top of it.
   */
  private suspend fun settings(title: String): FileKitDialogSettings {
    val owner =
      withContext(Dispatchers.Swing) {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
      }
    return FileKitDialogSettings(title = title, parent = owner?.let { FileKitDialogParent.awt(it) })
  }

  private companion object {
    val TYPES = setOf("pdf", "docx", "txt", "md")
    val BACKUP_TYPES = setOf("f2a", "json", "zip")
  }
}
