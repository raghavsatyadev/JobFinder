@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.github.raghavsatyadev.jobfinder.ui.platform.PickedFile
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeZIP
import platform.darwin.NSObject

/** The Files app's open and export sheets. */
class IosFilePicker : FilePicker {
  override suspend fun pickResume(): PickedFile? =
    open(listOfNotNull(UTTypePDF, UTType.typeWithFilenameExtension("docx"), UTTypePlainText))

  override suspend fun pickBackup(): PickedFile? =
    open(listOfNotNull(UTType.typeWithFilenameExtension("f2a"), UTTypeJSON, UTTypeZIP))

  override suspend fun pickLinkedInExport(): PickedFile? = open(listOf(UTTypeZIP))

  override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): Boolean {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
    if (!bytes.toNSData().writeToURL(url, atomically = true)) return false
    return present(UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true))
      .isNotEmpty()
  }

  private suspend fun open(types: List<UTType>): PickedFile? {
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = true)
    val url = present(picker).firstOrNull() ?: return null
    val data = NSData.dataWithContentsOfURL(url) ?: return null
    return PickedFile(url.lastPathComponent ?: "file", data.toByteArray())
  }

  /** Shows [picker]; the chosen URLs, or none when cancelled. */
  private suspend fun present(picker: UIDocumentPickerViewController): List<NSURL> =
    withContext(Dispatchers.Main) {
      val presenter = topViewController() ?: return@withContext emptyList()
      suspendCancellableCoroutine { continuation ->
        val delegate = Delegate { urls -> if (continuation.isActive) continuation.resume(urls) }
        picker.delegate = delegate
        // UIKit keeps only a weak reference to its delegate.
        continuation.invokeOnCancellation { delegate.hashCode() }
        presenter.presentViewController(picker, animated = true) { delegate.hashCode() }
        keep += delegate
      }
    }

  private val keep = mutableListOf<Delegate>()

  private inner class Delegate(val onDone: (List<NSURL>) -> Unit) :
    NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
      controller: UIDocumentPickerViewController,
      didPickDocumentsAtURLs: List<*>,
    ) {
      keep -= this
      onDone(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
      keep -= this
      onDone(emptyList())
    }
  }
}
