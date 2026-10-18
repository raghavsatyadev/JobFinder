package io.github.raghavsatyadev.jobfinder

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import io.github.raghavsatyadev.jobfinder.pairing.CurrentActivity
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.github.raghavsatyadev.jobfinder.ui.platform.PickedFile
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** The system file picker (Storage Access Framework): no storage permission needed. */
class AndroidFilePicker(private val activities: CurrentActivity) : FilePicker {
  override suspend fun pickResume(): PickedFile? =
    pick(RESUME_TYPES, "resume", AtsRepository.MAX_RESUME_BYTES + 1)

  override suspend fun pickBackup(): PickedFile? =
    pick(BACKUP_TYPES, "backup", BackupFiles.MAX_UNPACKED_BYTES.toInt())

  override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
    val activity = activities.current as? ComponentActivity ?: return false
    val uri =
      launch(activity, ActivityResultContracts.CreateDocument(mimeType), fileName) ?: return false
    return withContext(Dispatchers.IO) {
      activity.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
    }
  }

  private suspend fun pick(types: Array<String>, fallbackName: String, maxBytes: Int): PickedFile? {
    val activity = activities.current as? ComponentActivity ?: return null
    val uri = launch(activity, ActivityResultContracts.OpenDocument(), types) ?: return null
    return withContext(Dispatchers.IO) {
      val resolver = activity.contentResolver
      val name =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
          if (it.moveToFirst()) it.getString(0) else null
        } ?: fallbackName
      val bytes =
        resolver.openInputStream(uri)?.use { it.readNBytes(maxBytes) } ?: return@withContext null
      PickedFile(name, bytes)
    }
  }

  private suspend fun <I> launch(
    activity: ComponentActivity,
    contract: ActivityResultContract<I, Uri?>,
    input: I,
  ): Uri? = suspendCancellableCoroutine { continuation ->
    lateinit var launcher: ActivityResultLauncher<I>
    launcher =
      activity.activityResultRegistry.register("file-${UUID.randomUUID()}", contract) { uri ->
        launcher.unregister()
        if (continuation.isActive) continuation.resume(uri)
      }
    continuation.invokeOnCancellation { launcher.unregister() }
    launcher.launch(input)
  }

  private companion object {
    val RESUME_TYPES =
      arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown",
      )

    /** Files shared through Drive or mail often come as octet-stream. */
    val BACKUP_TYPES = arrayOf("application/json", "application/zip", "application/octet-stream")
  }
}
