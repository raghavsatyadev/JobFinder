package io.github.raghavsatyadev.jobfinder.shared.backup

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographySystem
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** The platform's crypto: the JDK's on the computer and on Android. */
internal expect val backupCryptoProvider: CryptographyProvider

/** The password is wrong, or the locked file was changed or damaged (its check failed). */
class BackupPasswordException :
  BackupFormatException("The password is wrong, or the file is damaged.")

/** The file was locked without a password on another computer or phone: only that one opens it. */
class BackupOtherDeviceException :
  BackupFormatException(
    "This backup was saved without a password on another computer or phone. Import it there, or " +
      "export it there again with a password."
  )

/** A locked backup, opened: the backup file it holds and the API keys locked with it. */
class OpenedBackup(val content: ByteArray, val apiKeys: List<BackupApiKey>)

/** This device's own backup key: 32 random bytes kept in the system's key store, and its id. */
class DeviceBackupKey(val id: ByteArray, val key: ByteArray) {
  override fun toString() = "DeviceBackupKey"
}

/** This device's backup key, made on first use; `null` where the system can't keep it safe. */
fun interface DeviceBackupKeys {
  suspend fun get(): DeviceBackupKey?
}

/**
 * Locked backup files. No key ships with the app: a fresh random 256-bit file key locks the payload
 * with AES-256-GCM, and that file key is locked either by a key made from the user's password
 * (PBKDF2-HMAC-SHA256; the file opens anywhere) or by this device's own backup key from the
 * system's key store ([DeviceBackupKey]; the file opens only here). A wrong password or a changed
 * file fails the GCM check of the file key, so nothing is read.
 *
 * The file, all numbers big-endian:
 * ```
 * "F2ALOCK" (7 bytes) | format version (1) | KDF id (1) | KDF parameters
 * | locked file key length (1) | locked file key: nonce (12) + key (32) + tag (16)
 * | payload: nonce (12) + ciphertext + tag (16)
 * ```
 *
 * KDF 1 is PBKDF2-HMAC-SHA256: iterations (4 bytes), salt length (1), salt. KDF 2 is a device key:
 * key id length (1), key id. The file key is locked with everything before it as associated data;
 * the payload with the whole header. The payload is a zip: `content` (the plain backup file, a
 * settings `.json` or a full backup `.zip`) and, from a computer, `api-keys.json`. A new method
 * gets a new version or KDF id; files without the magic are plain backups from older apps.
 */
object BackupCrypto {
  const val MIN_PASSWORD_LENGTH = 10
  const val PBKDF2_ITERATIONS = 600_000

  private val MAGIC = "F2ALOCK".encodeToByteArray()
  private const val VERSION: Byte = 1
  private const val KDF_PBKDF2: Byte = 1
  private const val KDF_DEVICE: Byte = 2
  private const val SALT_BYTES = 16

  /** A file asking more work than this to open is refused: no app writes one. */
  private const val MAX_ITERATIONS = 10_000_000

  private const val CONTENT = "content"
  private const val API_KEYS = "api-keys.json"

  private val json = Json { ignoreUnknownKeys = true }
  private val keysSerializer = ListSerializer(BackupApiKey.serializer())

  /** `true` when [bytes] start like a locked backup. */
  fun isLocked(bytes: ByteArray): Boolean =
    bytes.size > MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }

  /** `true` when locked [bytes] were locked with a device's own key, not a password. */
  fun isDeviceLocked(bytes: ByteArray): Boolean =
    isLocked(bytes) && bytes.size > MAGIC.size + 1 && bytes[MAGIC.size + 1] == KDF_DEVICE

  /** Locks [content] (a plain backup file) and [apiKeys] with [password]. */
  suspend fun lock(content: ByteArray, apiKeys: List<BackupApiKey>, password: String): ByteArray {
    require(password.length >= MIN_PASSWORD_LENGTH) { "The password is too short" }
    val salt = CryptographySystem.getDefaultRandom().nextBytes(SALT_BYTES)
    val kdfHeader =
      header(KDF_PBKDF2, intBytes(PBKDF2_ITERATIONS) + byteArrayOf(SALT_BYTES.toByte()) + salt)
    return seal(kdfHeader, passwordKey(password, salt, PBKDF2_ITERATIONS), content, apiKeys)
  }

  /** Locks [content] and [apiKeys] with this device's own [key]: only this device opens it. */
  suspend fun lock(
    content: ByteArray,
    apiKeys: List<BackupApiKey>,
    key: DeviceBackupKey,
  ): ByteArray {
    val kdfHeader = header(KDF_DEVICE, byteArrayOf(key.id.size.toByte()) + key.id)
    return seal(kdfHeader, aesKey(key.key), content, apiKeys)
  }

  /**
   * Opens a password-locked backup. Throws [BackupPasswordException] for a wrong password or a
   * changed file, and [BackupFormatException] when it is no such backup this app can read.
   */
  suspend fun open(bytes: ByteArray, password: String): OpenedBackup =
    unseal(bytes) { kdf, params ->
      if (kdf != KDF_PBKDF2) notLocked()
      val iterations = readInt(params.next(4))
      if (iterations !in 1..MAX_ITERATIONS) notLocked()
      val salt = params.next(params.next(1)[0].toInt() and 0xff)
      passwordKey(password, salt, iterations)
    }

  /**
   * Opens a backup locked with a device's own key. Throws [BackupOtherDeviceException] when another
   * device locked it, and [BackupPasswordException] when the file was changed.
   */
  suspend fun open(bytes: ByteArray, key: DeviceBackupKey): OpenedBackup =
    unseal(bytes) { kdf, params ->
      if (kdf != KDF_DEVICE) notLocked()
      val id = params.next(params.next(1)[0].toInt() and 0xff)
      if (!id.contentEquals(key.id)) throw BackupOtherDeviceException()
      aesKey(key.key)
    }

  private fun header(kdf: Byte, params: ByteArray): ByteArray =
    MAGIC + byteArrayOf(VERSION, kdf) + params

  private suspend fun seal(
    kdfHeader: ByteArray,
    wrapKey: AES.GCM.Key,
    content: ByteArray,
    apiKeys: List<BackupApiKey>,
  ): ByteArray {
    val fileKey = backupCryptoProvider.get(AES.GCM).keyGenerator(AES.Key.Size.B256).generateKey()
    val wrapped = wrapKey.cipher().encrypt(fileKey.encodeToByteArray(AES.Key.Format.RAW), kdfHeader)
    val header = kdfHeader + byteArrayOf(wrapped.size.toByte()) + wrapped
    val payload =
      BackupZip.write(
        buildList {
          add(CONTENT to content)
          if (apiKeys.isNotEmpty()) {
            add(API_KEYS to json.encodeToString(keysSerializer, apiKeys).encodeToByteArray())
          }
        }
      )
    return header + fileKey.cipher().encrypt(payload, header)
  }

  /** Reads a locked file's bytes in order. */
  private class Reader(private val bytes: ByteArray, var at: Int) {
    fun next(count: Int): ByteArray {
      if (count < 0 || at + count > bytes.size) notLocked()
      return bytes.copyOfRange(at, at + count).also { at += count }
    }
  }

  /** Reads the header, gets the key that locked the file key from [wrapKey], and opens the file. */
  private suspend fun unseal(
    bytes: ByteArray,
    wrapKey: suspend (kdf: Byte, params: Reader) -> AES.GCM.Key,
  ): OpenedBackup {
    if (!isLocked(bytes)) notLocked()
    val reader = Reader(bytes, MAGIC.size)
    if (reader.next(1)[0] > VERSION) newer()
    val kdf = reader.next(1)[0]
    if (kdf != KDF_PBKDF2 && kdf != KDF_DEVICE) newer()
    val key = wrapKey(kdf, reader)
    val kdfHeader = bytes.copyOfRange(0, reader.at)
    val wrapped = reader.next(reader.next(1)[0].toInt() and 0xff)
    val header = bytes.copyOfRange(0, reader.at)
    val ciphertext = bytes.copyOfRange(reader.at, bytes.size)
    val fileKey = checked { key.cipher().decrypt(wrapped, kdfHeader) }
    val payload = checked { aesKey(fileKey).cipher().decrypt(ciphertext, header) }
    val entries = BackupZip.read(payload, BackupFiles.MAX_UNPACKED_BYTES)
    val keys =
      entries[API_KEYS]?.let {
        try {
          json.decodeFromString(keysSerializer, it.decodeToString())
        } catch (_: SerializationException) {
          emptyList()
        } catch (_: IllegalArgumentException) {
          emptyList()
        }
      } ?: emptyList()
    return OpenedBackup(entries[CONTENT] ?: notLocked(), keys)
  }

  private suspend fun aesKey(raw: ByteArray): AES.GCM.Key =
    backupCryptoProvider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, raw)

  private suspend fun passwordKey(password: String, salt: ByteArray, iterations: Int): AES.GCM.Key =
    aesKey(
      backupCryptoProvider
        .get(PBKDF2)
        .secretDerivation(SHA256, iterations, 256.bits, salt)
        .deriveSecretToByteArray(password.encodeToByteArray())
    )

  /** A failed GCM check, whatever the platform throws for it, as a wrong password. */
  private inline fun <T> checked(block: () -> T): T =
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      throw BackupPasswordException()
    }

  private fun intBytes(value: Int) =
    byteArrayOf(
      (value ushr 24).toByte(),
      (value ushr 16).toByte(),
      (value ushr 8).toByte(),
      value.toByte(),
    )

  private fun readInt(b: ByteArray): Int =
    ((b[0].toInt() and 0xff) shl 24) or
      ((b[1].toInt() and 0xff) shl 16) or
      ((b[2].toInt() and 0xff) shl 8) or
      (b[3].toInt() and 0xff)

  private fun notLocked(): Nothing =
    throw BackupFormatException("This file is not a JobFinder backup.")

  private fun newer(): Nothing =
    throw BackupFormatException(
      "This backup is from a newer version of JobFinder. Update the app, then import it."
    )
}
