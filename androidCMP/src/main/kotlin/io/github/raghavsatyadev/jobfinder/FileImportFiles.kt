package io.github.raghavsatyadev.jobfinder

import io.github.raghavsatyadev.jobfinder.cache.ImportFiles
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ImportFiles] in a folder of the app's own storage. */
class FileImportFiles(private val dir: File) : ImportFiles {
  override suspend fun write(bytes: ByteArray): String =
    withContext(Dispatchers.IO) {
      dir.mkdirs()
      val name = "${UUID.randomUUID()}.zip"
      File(dir, name).writeBytes(bytes)
      name
    }

  override suspend fun read(name: String): ByteArray? =
    withContext(Dispatchers.IO) { File(dir, name).takeIf { it.isFile }?.readBytes() }

  override suspend fun delete(name: String) {
    withContext(Dispatchers.IO) { File(dir, name).delete() }
  }
}
