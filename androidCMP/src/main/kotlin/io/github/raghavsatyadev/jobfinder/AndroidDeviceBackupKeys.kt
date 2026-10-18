package io.github.raghavsatyadev.jobfinder

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKey
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The phone's backup key, made on first use, kept in [file] locked by an Android Keystore key that
 * never leaves the phone. Uninstalling the app loses it, and with it the backups saved without a
 * password.
 */
class AndroidDeviceBackupKeys(private val file: File) : DeviceBackupKeys {
  private val lock = Mutex()
  private var cached: DeviceBackupKey? = null

  override suspend fun get(): DeviceBackupKey? = lock.withLock {
    cached
      ?: withContext(Dispatchers.IO) {
          runCatching {
            val bytes =
              read()
                ?: ByteArray(ID_BYTES + KEY_BYTES).also {
                  SecureRandom().nextBytes(it)
                  write(it)
                }
            DeviceBackupKey(bytes.copyOfRange(0, ID_BYTES), bytes.copyOfRange(ID_BYTES, bytes.size))
          }
            .getOrNull()
        }
        ?.also { cached = it }
  }

  private fun read(): ByteArray? {
    if (!file.exists()) return null
    val sealed = file.readBytes()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      keystoreKey(),
      GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES),
    )
    return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
  }

  private fun write(bytes: ByteArray) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
    val sealed = cipher.iv + cipher.doFinal(bytes)
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, "${file.name}.tmp")
    temp.writeBytes(sealed)
    check(temp.renameTo(file)) { "The backup key was not saved" }
  }

  private fun keystoreKey(): SecretKey {
    val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (store.getKey(ALIAS, null) as? SecretKey)?.let {
      return it
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
          ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val ALIAS = "jobfinder-backup-key"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
    const val ID_BYTES = 16
    const val KEY_BYTES = 32
  }
}
