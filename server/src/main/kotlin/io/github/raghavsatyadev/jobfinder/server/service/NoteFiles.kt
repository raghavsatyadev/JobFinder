package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

/**
 * Note attachments under [root] (`%LOCALAPPDATA%\jobfinder-data\notes\`). An upload is stored as
 * `<noteId>/<safe file name>` and that relative path goes into the note's `files`. Notes imported
 * from the old app keep their absolute paths; those are opened on the desktop only and never
 * served.
 */
class NoteFiles(private val root: Path, val maxBytes: Long = DEFAULT_MAX_BYTES) {

  /**
   * Copies [input] to the note's folder under a safe version of [fileName] and returns the relative
   * path to store. Throws [ApiException] when the name is unusable or the file is over [maxBytes].
   */
  fun store(noteId: Long, fileName: String, input: InputStream): String {
    val name = safeName(fileName)
    val dir = resolveInside("$noteId")
    dir.createDirectories()
    val target = uniqueTarget(dir, name)
    val temp = Files.createTempFile(dir, ".upload-", ".part")
    try {
      var total = 0L
      temp.outputStream().use { out ->
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          total += read
          if (total > maxBytes) {
            throw ApiException(
              ApiErrorCode.PAYLOAD_TOO_LARGE,
              "Attachments can be up to ${maxBytes / (1024 * 1024)} MB.",
            )
          }
          out.write(buffer, 0, read)
        }
      }
      Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE)
    } finally {
      temp.deleteIfExists()
    }
    return root.relativize(target).joinToString("/")
  }

  /**
   * The file for a stored relative path, or `null` when it is a legacy absolute path, points
   * outside [root], or is missing.
   */
  fun resolve(stored: String): Path? {
    val file =
      try {
        if (stored.isBlank() || stored.startsWith("/") || Path.of(stored).isAbsolute) return null
        resolveInside(stored)
      } catch (_: ApiException) {
        return null
      } catch (_: InvalidPathException) {
        return null
      }
    return file.takeIf { it.exists() && it.isRegularFile() }
  }

  /** Deletes the uploads of note [noteId]. */
  @OptIn(ExperimentalPathApi::class)
  fun deleteAll(noteId: Long) {
    resolveInside("$noteId").deleteRecursively()
  }

  private fun resolveInside(relative: String): Path {
    val base = root.toAbsolutePath().normalize()
    val path = base.resolve(relative).normalize()
    if (!path.startsWith(base) || path == base) {
      throw ApiException(ApiErrorCode.BAD_REQUEST, "Invalid file path.")
    }
    return path
  }

  private fun uniqueTarget(dir: Path, name: String): Path {
    var candidate = dir.resolve(name)
    var n = 1
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    while (candidate.exists()) {
      candidate = dir.resolve(name.substring(0, dot) + " ($n)" + name.substring(dot))
      n++
    }
    return candidate
  }

  companion object {
    const val DEFAULT_MAX_BYTES = 25L * 1024 * 1024
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_NAME_LENGTH = 120
    private val UNSAFE = Regex("[^A-Za-z0-9._ ()\\-]")
    private val WINDOWS_RESERVED =
      Regex("^(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?$", RegexOption.IGNORE_CASE)

    /**
     * Keeps the last path segment, replaces anything outside a small character set with `_` and
     * refuses names that would mean "this folder", "the parent" or a Windows device.
     */
    fun safeName(fileName: String): String {
      val last = fileName.substringAfterLast('/').substringAfterLast('\\')
      val cleaned = last.replace(UNSAFE, "_").trim().trimEnd('.').take(MAX_NAME_LENGTH)
      if (cleaned.isEmpty() || cleaned.all { it == '.' } || WINDOWS_RESERVED.matches(cleaned)) {
        throw ApiException(ApiErrorCode.BAD_REQUEST, "Invalid file name.")
      }
      return cleaned
    }
  }
}
