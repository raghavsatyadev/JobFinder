package io.github.raghavsatyadev.jobfinder.shared.backup

import io.github.raghavsatyadev.jobfinder.shared.model.BackupData
import io.github.raghavsatyadev.jobfinder.shared.model.BackupFormatVersion
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A file the user picked is not a JobFinder backup, or not one this app can read. */
open class BackupFormatException(message: String) : IllegalArgumentException(message)

/** A full backup, read: its manifest, settings, data and note attachments (by path). */
class FullBackup(
  val manifest: BackupManifest,
  val settings: SettingsBackup,
  val data: BackupData,
  val files: Map<String, ByteArray> = emptyMap(),
)

/** Zip archives, in memory (the JVM's `java.util.zip` on both apps). */
expect object BackupZip {
  fun write(entries: List<Pair<String, ByteArray>>): ByteArray

  /** Every entry; throws [BackupFormatException] when it is no zip or holds over [maxBytes]. */
  fun read(bytes: ByteArray, maxBytes: Long): Map<String, ByteArray>
}

/**
 * The two backup files. A settings backup is one JSON file and never holds jobs. A full backup is a
 * zip with `manifest.json`, `settings.json` (every settings part), `data.json` (searches, jobs,
 * notes, reviews, company insights) and the notes' attachments under `files/`.
 */
object BackupFiles {
  const val MANIFEST = "manifest.json"
  const val SETTINGS = "settings.json"
  const val DATA = "data.json"
  const val FILES = "files/"

  /** Unpacked size refused above this: no backup of a real install comes near. */
  const val MAX_UNPACKED_BYTES = 1L shl 30

  /** Lenient on read, so a backup from an older app with fewer fields still loads. */
  private val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
  }

  /** `true` when [bytes] start like a zip (`PK`). */
  fun isZip(bytes: ByteArray): Boolean =
    bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

  fun encodeSettings(backup: SettingsBackup): ByteArray =
    json.encodeToString(SettingsBackup.serializer(), backup).encodeToByteArray()

  /** A settings backup; a full backup's settings when [bytes] is one. */
  fun decodeSettings(bytes: ByteArray): SettingsBackup {
    if (isZip(bytes)) return decodeFull(bytes).settings
    // The kind must be written in the file: any other JSON would load as an empty backup.
    if (kindOf(bytes) != SettingsBackup.KIND) notABackup()
    val backup = decode(bytes, SettingsBackup.serializer())
    checkVersion(backup.formatVersion)
    return backup
  }

  fun encodeFull(backup: FullBackup): ByteArray =
    BackupZip.write(
      listOf(
        MANIFEST to
          json.encodeToString(BackupManifest.serializer(), backup.manifest).encodeToByteArray(),
        SETTINGS to encodeSettings(backup.settings),
        DATA to json.encodeToString(BackupData.serializer(), backup.data).encodeToByteArray(),
      ) + backup.files.map { (path, bytes) -> "$FILES$path" to bytes }
    )

  /** Only the manifest: what the import screen shows before anything is written. */
  fun readManifest(bytes: ByteArray): BackupManifest = decodeFull(bytes).manifest

  fun decodeFull(bytes: ByteArray): FullBackup {
    if (!isZip(bytes)) notABackup()
    val entries = BackupZip.read(bytes, MAX_UNPACKED_BYTES)
    val manifestBytes = entries[MANIFEST] ?: notABackup()
    if (kindOf(manifestBytes) != BackupManifest.KIND) notABackup()
    val manifest = decode(manifestBytes, BackupManifest.serializer())
    checkVersion(manifest.formatVersion)
    return FullBackup(
      manifest = manifest,
      settings =
        entries[SETTINGS]?.let { decode(it, SettingsBackup.serializer()) } ?: SettingsBackup(),
      data = decode(entries[DATA] ?: notABackup(), BackupData.serializer()),
      files =
        entries
          .filterKeys { it.startsWith(FILES) && it.length > FILES.length }
          .mapKeys { it.key.removePrefix(FILES) },
    )
  }

  private fun kindOf(bytes: ByteArray): String? =
    try {
      (json.parseToJsonElement(bytes.decodeToString()) as? JsonObject)?.get("kind")?.let {
        (it as? JsonPrimitive)?.contentOrNull
      }
    } catch (_: SerializationException) {
      null
    }

  private fun <T> decode(bytes: ByteArray, serializer: KSerializer<T>): T =
    try {
      json.decodeFromString(serializer, bytes.decodeToString())
    } catch (_: SerializationException) {
      notABackup()
    } catch (_: IllegalArgumentException) {
      notABackup()
    }

  private fun checkVersion(version: Int) {
    if (version > BackupFormatVersion.CURRENT) {
      throw BackupFormatException(
        "This backup is from a newer version of JobFinder. Update the app, then import it."
      )
    }
  }

  private fun notABackup(): Nothing =
    throw BackupFormatException("This file is not a JobFinder backup.")
}
