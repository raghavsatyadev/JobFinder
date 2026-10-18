package io.github.raghavsatyadev.jobfinder.desktop.models

import io.github.raghavsatyadev.jobfinder.desktop.setup.Progress
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The file at the link is not what was expected. */
class ChecksumMismatch : Exception("The file's SHA-256 is not the one given")

/**
 * Downloads a GGUF file from a direct link into [dir], resuming a partial download (`.part`) with
 * an HTTP range, then checks its SHA-256.
 */
class GgufDownload(private val http: HttpClient, private val dir: File) {
  /** The size the server reports, or `null`. */
  suspend fun size(url: String): Long? = runCatching {
    http
      .head(url) { timeout { requestTimeoutMillis = HEAD_MS } }
      .takeIf { it.status.isSuccess() }
      ?.contentLength()
  }
    .getOrNull()

  /**
   * The downloaded file and its digest (`sha256:<hex>`). Throws [ChecksumMismatch] when
   * [expectedSha256] is given and differs; the file is then deleted.
   */
  suspend fun fetch(
    url: String,
    expectedSha256: String?,
    onProgress: Progress,
  ): Pair<File, String> {
    dir.mkdirs()
    val name = fileName(url)
    val part = File(dir, "$name.part")
    val target = File(dir, name)
    var done = if (part.isFile) part.length() else 0L
    http
      .prepareGet(url) {
        timeout {
          requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
          socketTimeoutMillis = QUIET_MS
        }
        if (done > 0) header(HttpHeaders.Range, "bytes=$done-")
      }
      .execute { response ->
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        // A server that ignores the range sends the whole file again.
        val append = done > 0 && response.status == HttpStatusCode.PartialContent
        if (!append) done = 0
        val total = response.contentLength()?.let { it + done }
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(BUFFER_BYTES)
        withContext(Dispatchers.IO) {
          FileOutputStream(part, append).use { out ->
            while (true) {
              val read = channel.readAvailable(buffer)
              if (read < 0) break
              if (read == 0 && channel.isClosedForRead) break
              out.write(buffer, 0, read)
              done += read
              onProgress(done, total)
            }
          }
        }
      }
    val digest = withContext(Dispatchers.IO) { sha256(part) }
    val expected = expectedSha256?.trim()?.lowercase()?.removePrefix("sha256:")
    if (!expected.isNullOrEmpty() && expected != digest) {
      part.delete()
      throw ChecksumMismatch()
    }
    target.delete()
    check(part.renameTo(target)) { "Could not move the download" }
    return target to "sha256:$digest"
  }

  companion object {
    private const val HEAD_MS = 15_000L
    private const val QUIET_MS = 2 * 60_000L
    private const val BUFFER_BYTES = 256 * 1024

    /** A safe local name for the link's file. */
    fun fileName(url: String): String {
      val last = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
      val safe =
        last.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.').take(MAX_NAME).ifEmpty {
          "model"
        }
      return if (safe.endsWith(".gguf", ignoreCase = true)) safe else "$safe.gguf"
    }

    fun sha256(file: File): String {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().use { input ->
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          digest.update(buffer, 0, read)
        }
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val MAX_NAME = 120
  }
}
