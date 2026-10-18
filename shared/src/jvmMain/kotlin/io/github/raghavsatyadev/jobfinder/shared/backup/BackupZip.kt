package io.github.raghavsatyadev.jobfinder.shared.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

actual object BackupZip {
  actual fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      for ((name, bytes) in entries) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
    return out.toByteArray()
  }

  actual fun read(bytes: ByteArray, maxBytes: Long): Map<String, ByteArray> {
    val entries = linkedMapOf<String, ByteArray>()
    var total = 0L
    try {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val entry = zip.nextEntry ?: break
          if (entry.isDirectory) continue
          val out = ByteArrayOutputStream()
          while (true) {
            val read = zip.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw BackupFormatException("This backup is too large to read.")
            out.write(buffer, 0, read)
          }
          entries[entry.name] = out.toByteArray()
        }
      }
    } catch (_: IOException) {
      throw BackupFormatException("This file is not a JobFinder backup.")
    }
    if (entries.isEmpty()) throw BackupFormatException("This file is not a JobFinder backup.")
    return entries
  }
}
