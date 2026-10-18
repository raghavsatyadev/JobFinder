package io.github.raghavsatyadev.jobfinder.ui.platform

/** A file the user picked: its name and contents. */
class PickedFile(val name: String, val bytes: ByteArray)

/**
 * The system's open and save dialogs. Each app binds it in Koin; without it the screens hide their
 * upload buttons (resolve it with `getOrNull()`).
 */
interface FilePicker {
  /** Asks for a PDF, DOCX or text resume; `null` when cancelled. */
  suspend fun pickResume(): PickedFile?

  /**
   * Asks for a backup: a password-locked `.f2a`, a settings `.json` or a full backup `.zip`; `null`
   * when cancelled.
   */
  suspend fun pickBackup(): PickedFile? = null

  /**
   * Asks where to save [bytes], suggesting [fileName]; `false` when cancelled or not supported.
   * [mimeType] is `application/octet-stream` (a locked backup), `application/json` or
   * `application/zip`.
   */
  suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean = false
}
