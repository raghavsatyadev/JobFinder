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

/** A locked backup, opened: the backup file it holds and the API keys locked with it. */
class OpenedBackup(val content: ByteArray, val apiKeys: List<BackupApiKey>)

/**
 * Password-locked backup files. No key ships with the app: a fresh random 256-bit file key locks
 * the payload with AES-256-GCM, and a key made from the user's password (PBKDF2-HMAC-SHA256) locks
 * the file key. A wrong password fails the GCM check of the file key, so nothing is read.
 *
 * The file, all numbers big-endian:
 * ```
 * "F2ALOCK" (7 bytes) | format version (1) | KDF id (1) | KDF parameters | salt length (1) | salt
 * | locked file key length (1) | locked file key: nonce (12) + key (32) + tag (16)
 * | payload: nonce (12) + ciphertext + tag (16)
 * ```
 *
 * KDF 1 is PBKDF2-HMAC-SHA256 with its iterations (4 bytes) as parameters. The file key is locked
 * with everything before it as associated data; the payload with the whole header. The payload is a
 * zip: `content` (the plain backup file, a settings `.json` or a full backup `.zip`) and, from a
 * computer, `api-keys.json`. A new method gets a new version or KDF id; files without the magic are
 * plain backups.
 */
object BackupCrypto {
  const val MIN_PASSWORD_LENGTH = 10
  const val PBKDF2_ITERATIONS = 600_000

  private val MAGIC = "F2ALOCK".encodeToByteArray()
  private const val VERSION: Byte = 1
  private const val KDF_PBKDF2: Byte = 1
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

  /** Locks [content] (a plain backup file) and [apiKeys] with [password]. */
  suspend fun lock(content: ByteArray, apiKeys: List<BackupApiKey>, password: String): ByteArray {
    require(password.length >= MIN_PASSWORD_LENGTH) { "The password is too short" }
    val aes = backupCryptoProvider.get(AES.GCM)
    val salt = CryptographySystem.getDefaultRandom().nextBytes(SALT_BYTES)
    val kdfHeader = buildList {
      addAll(MAGIC.toList())
      add(VERSION)
      add(KDF_PBKDF2)
      addAll(intBytes(PBKDF2_ITERATIONS).toList())
      add(SALT_BYTES.toByte())
      addAll(salt.toList())
    }
      .toByteArray()
    val fileKey = aes.keyGenerator(AES.Key.Size.B256).generateKey()
    val wrapped =
      passwordKey(password, salt, PBKDF2_ITERATIONS)
        .cipher()
        .encrypt(fileKey.encodeToByteArray(AES.Key.Format.RAW), kdfHeader)
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

  /**
   * Opens a locked backup. Throws [BackupPasswordException] for a wrong password or a changed file,
   * and [BackupFormatException] when it is no locked backup this app can read.
   */
  suspend fun open(bytes: ByteArray, password: String): OpenedBackup {
    if (!isLocked(bytes)) notLocked()
    var at = MAGIC.size
    fun next(count: Int): ByteArray {
      if (count < 0 || at + count > bytes.size) notLocked()
      return bytes.copyOfRange(at, at + count).also { at += count }
    }
    if (next(1)[0] > VERSION) newer()
    if (next(1)[0] != KDF_PBKDF2) newer()
    val iterations = readInt(next(4))
    if (iterations !in 1..MAX_ITERATIONS) notLocked()
    val salt = next(next(1)[0].toInt() and 0xff)
    val kdfHeader = bytes.copyOfRange(0, at)
    val wrapped = next(next(1)[0].toInt() and 0xff)
    val header = bytes.copyOfRange(0, at)
    val ciphertext = bytes.copyOfRange(at, bytes.size)
    val aes = backupCryptoProvider.get(AES.GCM)
    val fileKey = checked {
      passwordKey(password, salt, iterations).cipher().decrypt(wrapped, kdfHeader)
    }
    val payload = checked {
      aes
        .keyDecoder()
        .decodeFromByteArray(AES.Key.Format.RAW, fileKey)
        .cipher()
        .decrypt(ciphertext, header)
    }
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

  private suspend fun passwordKey(password: String, salt: ByteArray, iterations: Int): AES.GCM.Key {
    val secret =
      backupCryptoProvider
        .get(PBKDF2)
        .secretDerivation(SHA256, iterations, 256.bits, salt)
        .deriveSecretToByteArray(password.encodeToByteArray())
    return backupCryptoProvider
      .get(AES.GCM)
      .keyDecoder()
      .decodeFromByteArray(AES.Key.Format.RAW, secret)
  }

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
