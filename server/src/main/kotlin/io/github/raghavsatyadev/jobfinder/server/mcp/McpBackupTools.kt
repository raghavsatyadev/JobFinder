package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.shared.backup.BackupCrypto
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFormatException
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupOtherDeviceException
import io.github.raghavsatyadev.jobfinder.shared.backup.OpenedBackup
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.ImportSummary
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Backup and restore, as in Settings → Backup. Every file written is locked with this computer's
 * own key, like the app's "save without a password": it carries the online API keys, which go from
 * the key store straight into the locked file and never through MCP. No file's bytes are returned.
 */
internal object McpBackupTools {
  private val PARTS = SettingsPart.entries.map { it.wire }

  fun tools(d: McpDependencies): List<McpTool> = buildList {
    add(
      McpTool(
        "list_backups",
        "The automatic daily full backups the app keeps on this computer, newest first: path, size and date.",
        readOnly = true,
      ) {
        val files = d.automaticBackups()
        buildJsonObject {
          putJsonArray("backups") {
            files.forEach { path ->
              add(
                buildJsonObject {
                  put("path", path.toString())
                  put("bytes", Files.size(path))
                  put("saved", iso(Files.getLastModifiedTime(path).toMillis()))
                }
              )
            }
          }
        }
      }
    )
    add(
      McpTool(
        "export_settings_backup",
        "Saves the chosen settings (never jobs) to a new file, locked with this computer's own key so only this computer opens it. Parts: ${PARTS.joinToString()}; the default leaves out resume and server (port and network).",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of a new file; its folder must exist."),
          "parts" to McpSchema.strings("Parts to save: ${PARTS.joinToString()}."),
          required = listOf("path"),
        ),
      ) {
        val file = newFile(it.str("path"))
        val parts = it.optStrings("parts")?.let(::parts) ?: SettingsPart.DEFAULT
        if (parts.isEmpty()) throw McpToolError("Choose at least one part.")
        val bytes = BackupFiles.encodeSettings(backups(d).exportSettings(parts))
        saved(d, file, bytes) { put("parts", mcpJson(parts.map { p -> p.wire })) }
      }
    )
    add(
      McpTool(
        "export_full_backup",
        "Saves everything (settings, searches, jobs, notes, ATS reviews, files) to a new file, locked with this computer's own key so only this computer opens it. Refuses to overwrite a file.",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of a new file; its folder must exist."),
          required = listOf("path"),
        ),
      ) {
        val file = newFile(it.str("path"))
        val bytes = backups(d).exportFull()
        val manifest = withContext(Dispatchers.Default) { BackupFiles.readManifest(bytes) }
        saved(d, file, bytes) { put("holds", mcpJson(manifest)) }
      }
    )
    add(
      McpTool(
        "export_jobs_csv",
        "Saves the jobs as a plain CSV file (no settings, no keys, not locked). Refuses to overwrite a file.",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of a new .csv file; its folder must exist."),
          required = listOf("path"),
        ),
      ) {
        val file = newFile(it.str("path"))
        val bytes = backups(d).exportJobsCsv()
        withContext(Dispatchers.IO) { file.writeBytes(bytes) }
        buildJsonObject {
          put("saved", file.path)
          put("bytes", bytes.size)
        }
      }
    )
    add(
      McpTool(
        "read_backup",
        "Opens a backup file without importing it and says what it holds: a settings file's parts, or a full backup's counts. Use before an import.",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of the backup file."),
          "password" to McpSchema.string("Only for a file locked with a password."),
          required = listOf("path"),
        ),
        readOnly = true,
      ) {
        val opened = open(d, it)
        backupError {
          withContext(Dispatchers.Default) {
            buildJsonObject {
              if (BackupFiles.isZip(opened.content)) {
                put("kind", "full")
                put("holds", mcpJson(BackupFiles.readManifest(opened.content)))
              } else {
                put("kind", "settings")
                val backup = BackupFiles.decodeSettings(opened.content)
                put("parts", mcpJson(backup.parts.map { p -> p.wire }))
                put("appVersion", backup.appVersion)
                put("exported", iso(backup.exportedAt))
              }
              put("apiKeys", opened.apiKeys.size)
            }
          }
        }
      }
    )
    add(
      McpTool(
        "import_backup",
        "Imports a backup file: a settings file's parts, or a full backup added to (merge) or in place of (replace) what the app has. Its API keys go into this computer's key store. Ask the user first; replace removes the current jobs and settings (the app saves a backup before).",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of the backup file."),
          "password" to McpSchema.string("Only for a file locked with a password."),
          "parts" to
            McpSchema.strings(
              "A settings file's parts to bring in: ${PARTS.joinToString()}. Default: all it holds but server (port and network)."
            ),
          "mode" to McpSchema.string("A full backup: merge (default) or replace.", MODES),
          required = listOf("path"),
        ),
        destructive = true,
      ) {
        val backups = backups(d)
        val opened = open(d, it)
        val summary: ImportSummary = backupError {
          if (BackupFiles.isZip(opened.content)) {
            backups.importFull(opened.content, mode(it.optStr("mode")))
          } else {
            val backup =
              withContext(Dispatchers.Default) {
                BackupFiles.decodeSettings(opened.content)
              }
            val parts =
              it.optStrings("parts")?.let(::parts)?.intersect(backup.parts)
                ?: (backup.parts - SettingsPart.SERVER)
            if (parts.isEmpty()) throw McpToolError("None of those parts is in this file.")
            backups.importSettings(backup, parts)
          }
        }
        val keys = if (opened.apiKeys.isEmpty()) 0 else d.keys.restoreKeys(opened.apiKeys)
        buildJsonObject {
          put("imported", mcpJson(summary))
          put("apiKeysRestored", keys)
        }
      }
    )
  }

  private fun backups(d: McpDependencies): BackupRepository =
    d.backups() ?: throw McpToolError("Backups aren't available in this app.")

  /** A file to write: absolute, in a folder that exists, and not there yet. */
  private fun newFile(path: String): File {
    val file = File(path)
    if (!file.isAbsolute) throw McpToolError("Give an absolute path.")
    if (file.exists()) throw McpToolError("${file.path} already exists; choose a new name.")
    if (file.parentFile?.isDirectory != true) throw McpToolError("No folder ${file.parent}.")
    return file
  }

  /** Locks [bytes] with this computer's key and the API keys, and writes [file]. */
  private suspend fun saved(
    d: McpDependencies,
    file: File,
    bytes: ByteArray,
    extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
  ): JsonObject {
    val key =
      d.deviceKeys()?.get()
        ?: throw McpToolError("This computer can't keep a backup key, so it can't lock a file.")
    val apiKeys = d.keys.backupKeys()
    val locked = withContext(Dispatchers.Default) { BackupCrypto.lock(bytes, apiKeys, key) }
    withContext(Dispatchers.IO) { file.writeBytes(locked) }
    return buildJsonObject {
      put("saved", file.path)
      put("bytes", locked.size)
      put("lockedTo", "this computer")
      put("apiKeys", apiKeys.size)
      extra()
    }
  }

  /** Reads and unlocks a backup file: this computer's key, a password, or a plain older file. */
  private suspend fun open(d: McpDependencies, args: JsonObject): OpenedBackup {
    val file = File(args.str("path"))
    if (!file.isFile) throw McpToolError("No file at ${file.path}.")
    if (file.length() > MAX_BACKUP_BYTES) throw McpToolError("The file is over 1 GB.")
    val bytes = withContext(Dispatchers.IO) { file.readBytes() }
    return backupError {
      withContext(Dispatchers.Default) {
        when {
          BackupCrypto.isDeviceLocked(bytes) -> {
            val key = d.deviceKeys()?.get() ?: throw BackupOtherDeviceException()
            BackupCrypto.open(bytes, key)
          }
          BackupCrypto.isLocked(bytes) -> {
            val password =
              args.optStr("password")?.takeIf { it.isNotEmpty() }
                ?: throw McpToolError("This file is locked with a password: ask the user for it.")
            BackupCrypto.open(bytes, password)
          }
          else -> OpenedBackup(bytes, emptyList())
        }
      }
    }
  }

  /** A damaged, wrong-password or other-device file as an error the agent can show. */
  private inline fun <T> backupError(block: () -> T): T =
    try {
      block()
    } catch (e: BackupFormatException) {
      throw McpToolError(e.message ?: "Not a JobFinder backup.")
    }

  private fun parts(wires: List<String>): Set<SettingsPart> =
    wires
      .map { w ->
        SettingsPart.entries.firstOrNull { it.wire == w }
          ?: throw McpToolError("Unknown part \"$w\". Use: ${PARTS.joinToString()}.")
      }
      .toSet()

  private fun mode(wire: String?): ImportMode =
    when (wire ?: "merge") {
      "merge" -> ImportMode.MERGE
      "replace" -> ImportMode.REPLACE
      else -> throw McpToolError("\"mode\" is merge or replace.")
    }

  private val SettingsPart.wire: String
    get() = name.lowercase()

  private val MODES = listOf("merge", "replace")

  /** The same cap the app reads backups with. */
  private const val MAX_BACKUP_BYTES = BackupFiles.MAX_UNPACKED_BYTES
}
