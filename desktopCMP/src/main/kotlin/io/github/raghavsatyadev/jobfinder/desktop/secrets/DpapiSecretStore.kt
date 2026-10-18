package io.github.raghavsatyadev.jobfinder.desktop.secrets

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Crypt32Util
import com.sun.jna.platform.win32.WinCrypt
import io.github.raghavsatyadev.jobfinder.server.ai.NoSecretStore
import io.github.raghavsatyadev.jobfinder.server.ai.SecretStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Secrets encrypted with Windows DPAPI for this Windows user, one file each in [dir]. Another user,
 * another computer or a copied file can't read them. The folder is never part of a backup.
 */
class DpapiSecretStore(private val dir: Path) : SecretStore {
  override val available = true

  override fun get(name: String): String? {
    val file = file(name)
    if (!Files.exists(file)) return null
    val sealed = Files.readAllBytes(file)
    unseal(sealed, ENTROPY)?.let {
      return it
    }
    // Saved before the rename to JobFinder: seal it again under the new name.
    return unseal(sealed, OLD_ENTROPY)?.also { put(name, it) }
  }

  /** `null` when the file was made by another user, on another computer or with other entropy. */
  private fun unseal(sealed: ByteArray, entropy: ByteArray): String? =
    try {
      String(Crypt32Util.cryptUnprotectData(sealed, entropy, FLAGS, null), Charsets.UTF_8)
    } catch (_: Exception) {
      null
    }

  override fun put(name: String, value: String) {
    Files.createDirectories(dir)
    val sealed =
      Crypt32Util.cryptProtectData(value.toByteArray(Charsets.UTF_8), ENTROPY, FLAGS, "", null)
    val temp = Files.createTempFile(dir, name, ".tmp")
    Files.write(temp, sealed)
    Files.move(
      temp,
      file(name),
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  }

  override fun remove(name: String) {
    Files.deleteIfExists(file(name))
  }

  private fun file(name: String): Path {
    require(name.matches(SAFE_NAME)) { "Bad secret name" }
    return dir.resolve("$name.dpapi")
  }

  companion object {
    private val SAFE_NAME = Regex("[a-z0-9-]+")
    private val ENTROPY = "jobfinder".toByteArray(Charsets.UTF_8)
    private val OLD_ENTROPY = "first2apply".toByteArray(Charsets.UTF_8)
    private const val FLAGS = WinCrypt.CRYPTPROTECT_UI_FORBIDDEN

    /** DPAPI on Windows; elsewhere no keys yet, so only the local model works. */
    fun forThisComputer(dir: Path): SecretStore =
      if (Platform.isWindows()) DpapiSecretStore(dir) else NoSecretStore
  }
}
