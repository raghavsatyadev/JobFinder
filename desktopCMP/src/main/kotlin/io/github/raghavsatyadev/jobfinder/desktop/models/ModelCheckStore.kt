package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The last accuracy check of each model, in a JSON file next to the database. */
class ModelCheckStore(private val file: File) {
  private val lock = Mutex()
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
  }

  suspend fun all(): Map<String, ModelCheck> = lock.withLock { read() }

  suspend fun put(check: ModelCheck) = lock.withLock { write(read() + (check.model to check)) }

  suspend fun remove(model: String) = lock.withLock { write(read() - model) }

  private suspend fun read(): Map<String, ModelCheck> =
    withContext(Dispatchers.IO) {
      if (!file.isFile) return@withContext emptyMap()
      runCatching { json.decodeFromString<List<ModelCheck>>(file.readText()) }
        .getOrDefault(emptyList())
        .associateBy { it.model }
    }

  private suspend fun write(checks: Map<String, ModelCheck>) =
    withContext(Dispatchers.IO) {
      file.parentFile?.mkdirs()
      val temp = File(file.path + ".tmp")
      temp.writeText(json.encodeToString(checks.values.sortedBy { it.model }))
      Files.move(
        temp.toPath(),
        file.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
      )
    }
}
