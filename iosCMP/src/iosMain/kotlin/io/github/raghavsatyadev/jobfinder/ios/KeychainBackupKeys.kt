@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKey
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecRandomDefault
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The phone's backup key, made on first use and kept in the Keychain for this device only (it is
 * never synced or restored to another phone). Deleting the app can lose it, and with it the backups
 * saved without a password.
 */
class KeychainBackupKeys : DeviceBackupKeys {
  private val lock = Mutex()
  private var cached: DeviceBackupKey? = null

  override suspend fun get(): DeviceBackupKey? = lock.withLock {
    cached
      ?: runCatching {
        val bytes = read() ?: random(ID_BYTES + KEY_BYTES).also(::write)
        DeviceBackupKey(bytes.copyOfRange(0, ID_BYTES), bytes.copyOfRange(ID_BYTES, bytes.size))
      }
        .getOrNull()
        ?.also { cached = it }
  }

  private fun read(): ByteArray? = memScoped {
    val query = query()
    CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
    CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
    val result = alloc<CFTypeRefVar>()
    val status = SecItemCopyMatching(query, result.ptr)
    CFRelease(query)
    if (status != errSecSuccess) return null
    (CFBridgingRelease(result.value) as? NSData)?.toByteArray()?.takeIf {
      it.size == ID_BYTES + KEY_BYTES
    }
  }

  private fun write(bytes: ByteArray) {
    val query = query()
    val data = CFBridgingRetain(bytes.toNSData())
    CFDictionaryAddValue(query, kSecValueData, data)
    CFDictionaryAddValue(
      query,
      kSecAttrAccessible,
      kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    )
    val status = SecItemAdd(query, null)
    CFRelease(data)
    CFRelease(query)
    check(status == errSecSuccess) { "The backup key was not saved ($status)" }
  }

  private fun query(): CFMutableDictionaryRef? {
    val query =
      CFDictionaryCreateMutable(
        null,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
      )
    CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
    val service = CFBridgingRetain(SERVICE)
    val account = CFBridgingRetain(ACCOUNT)
    CFDictionaryAddValue(query, kSecAttrService, service)
    CFDictionaryAddValue(query, kSecAttrAccount, account)
    CFRelease(service)
    CFRelease(account)
    return query
  }

  private fun random(size: Int): ByteArray {
    val bytes = ByteArray(size)
    val status = bytes.usePinned {
      SecRandomCopyBytes(kSecRandomDefault, size.toULong(), it.addressOf(0))
    }
    check(status == errSecSuccess) { "No random bytes ($status)" }
    return bytes
  }

  private companion object {
    const val SERVICE = "io.github.raghavsatyadev.jobfinder"
    const val ACCOUNT = "backup-key"
    const val ID_BYTES = 16
    const val KEY_BYTES = 32
  }
}
