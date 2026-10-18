package io.github.raghavsatyadev.jobfinder.desktop.secrets

import io.github.raghavsatyadev.jobfinder.server.ai.SecretStore
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKey
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * This computer's backup key, made on first use. On Windows it sits in the DPAPI [secrets] store;
 * where that store is not available, in [fallback], a file only this user can read. Losing it (a
 * new Windows install) makes the backups saved without a password unreadable.
 */
class DesktopDeviceBackupKeys(private val secrets: SecretStore, private val fallback: Path) :
  DeviceBackupKeys {
  private val lock = Mutex()
  private var cached: DeviceBackupKey? = null

  override suspend fun get(): DeviceBackupKey = lock.withLock {
    cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
  }

  private fun load(): DeviceBackupKey {
    val bytes =
      read()
        ?: ByteArray(ID_BYTES + KEY_BYTES).also {
          SecureRandom().nextBytes(it)
          write(it)
        }
    return DeviceBackupKey(bytes.copyOfRange(0, ID_BYTES), bytes.copyOfRange(ID_BYTES, bytes.size))
  }

  private fun read(): ByteArray? {
    val text =
      if (secrets.available) secrets.get(NAME)
      else fallback.takeIf { Files.exists(it) }?.let { Files.readString(it) }
    return text
      ?.let { runCatching { Base64.decode(it.trim()) }.getOrNull() }
      ?.takeIf { it.size == ID_BYTES + KEY_BYTES }
  }

  private fun write(bytes: ByteArray) {
    val text = Base64.encode(bytes)
    if (secrets.available) return secrets.put(NAME, text)
    Files.createDirectories(fallback.parent)
    val temp = Files.createTempFile(fallback.parent, NAME, ".tmp")
    runCatching {
      Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"))
    }
    Files.writeString(temp, text)
    Files.move(temp, fallback, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  companion object {
    const val NAME = "backup-key"
    private const val ID_BYTES = 16
    private const val KEY_BYTES = 32
  }
}
