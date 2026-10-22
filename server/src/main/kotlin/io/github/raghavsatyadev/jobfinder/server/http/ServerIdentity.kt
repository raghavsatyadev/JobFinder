package io.github.raghavsatyadev.jobfinder.server.http

import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Who this desktop is to phones: [id] never changes (it survives new addresses and new pairing
 * tokens), [name] is the computer's name.
 */
data class ServerIdentity(val id: String, val name: String) {
  companion object {
    /** Reads the id from [file], creating it on first use. */
    fun load(file: Path, name: String = computerName()): ServerIdentity {
      val saved = if (file.exists()) file.readText().trim() else ""
      val id =
        saved.takeIf { ID.matches(it) }
          ?: UUID.randomUUID().toString().also {
            Files.createDirectories(file.parent)
            file.writeText(it)
          }
      return ServerIdentity(id, name)
    }

    /**
     * `COMPUTERNAME` on Windows, the Computer Name from Sharing settings on macOS, else the host
     * name, else a plain label.
     */
    fun computerName(env: Map<String, String> = System.getenv()): String =
      env["COMPUTERNAME"]?.takeIf { it.isNotBlank() }
        ?: macComputerName()
        ?: runCatching { InetAddress.getLocalHost().hostName }
          .getOrNull()
          ?.takeIf { it.isNotBlank() }
        ?: "JobFinder computer"

    /** `scutil --get ComputerName`, as Finder and AirDrop show the Mac; `null` off macOS. */
    private fun macComputerName(): String? {
      if (!System.getProperty("os.name").orEmpty().startsWith("Mac")) return null
      return runCatching {
        val process = ProcessBuilder("/usr/sbin/scutil", "--get", "ComputerName").start()
        val name = process.inputStream.bufferedReader().readText().trim()
        name.takeIf { process.waitFor() == 0 && it.isNotBlank() }
      }
        .getOrNull()
    }

    private val ID = Regex("[0-9a-fA-F-]{36}")
  }
}
