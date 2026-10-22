package io.github.raghavsatyadev.jobfinder.desktop.secrets

import io.github.raghavsatyadev.jobfinder.server.ai.SecretStore
import java.util.Base64
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Secrets in this macOS user's login Keychain, one generic password each under [service], through
 * Apple's `security` tool. Another user or another Mac can't read them, and they are never part of
 * a backup.
 *
 * Values are written through `security -i` on standard input, so a key never shows in the process
 * list, and stored as Base64, so no character needs quoting.
 */
class KeychainSecretStore(private val service: String = SERVICE) : SecretStore {
  private val log = LoggerFactory.getLogger(KeychainSecretStore::class.java)

  override val available = true

  override fun get(name: String): String? {
    val result = run(listOf("find-generic-password", "-s", service, "-a", account(name), "-w"))
    if (result == null || result.first != 0) return null
    return runCatching { String(Base64.getDecoder().decode(result.second.trim()), Charsets.UTF_8) }
      .getOrNull()
  }

  override fun put(name: String, value: String) {
    val encoded = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    val command =
      "add-generic-password -U -s \"$service\" -a \"${account(name)}\" -l \"$LABEL\" -w \"$encoded\"\n"
    val result = run(listOf("-i"), input = command)
    check(result != null && result.first == 0) { "The Keychain did not save the secret" }
  }

  override fun remove(name: String) {
    run(listOf("delete-generic-password", "-s", service, "-a", account(name)))
  }

  private fun account(name: String): String {
    require(name.matches(SAFE_NAME)) { "Bad secret name" }
    return name
  }

  /** Exit code and standard output of `security` [args], or `null` when it did not run. */
  private fun run(args: List<String>, input: String? = null): Pair<Int, String>? =
    try {
      val process =
        ProcessBuilder(listOf(SECURITY) + args)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start()
      process.outputStream.use { out -> input?.let { out.write(it.toByteArray(Charsets.UTF_8)) } }
      val output = process.inputStream.bufferedReader().readText()
      if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        null
      } else {
        process.exitValue() to output
      }
    } catch (e: Exception) {
      log.warn("security {} failed", args.firstOrNull(), e)
      null
    }

  private companion object {
    const val SECURITY = "/usr/bin/security"
    const val SERVICE = "io.github.raghavsatyadev.jobfinder"
    const val LABEL = "JobFinder"
    const val TIMEOUT_S = 15L
    val SAFE_NAME = Regex("[a-z0-9-]+")
  }
}
