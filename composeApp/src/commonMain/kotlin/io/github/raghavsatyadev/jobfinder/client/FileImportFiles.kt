package io.github.raghavsatyadev.jobfinder.client

import io.github.raghavsatyadev.jobfinder.cache.ImportFiles
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray

/** [ImportFiles] in a folder of the app's own storage. */
class FileImportFiles(private val dir: String) : ImportFiles {
  @OptIn(ExperimentalUuidApi::class)
  override suspend fun write(bytes: ByteArray): String =
    withContext(Dispatchers.IO) {
      SystemFileSystem.createDirectories(Path(dir))
      val name = "${Uuid.random()}.zip"
      SystemFileSystem.sink(Path(dir, name)).buffered().use { it.write(bytes) }
      name
    }

  override suspend fun read(name: String): ByteArray? =
    withContext(Dispatchers.IO) {
      val file = Path(dir, name)
      if (!SystemFileSystem.exists(file)) return@withContext null
      SystemFileSystem.source(file).buffered().use { it.readByteArray() }
    }

  override suspend fun delete(name: String) {
    withContext(Dispatchers.IO) { SystemFileSystem.delete(Path(dir, name), mustExist = false) }
  }
}
