package io.github.raghavsatyadev.jobfinder.ui.platform

import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiKeyUsage
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey

/** The online keys, today's use of each and which model does which work. Never the keys. */
data class AiKeysState(
  val keys: List<AiKey> = emptyList(),
  val usage: List<AiKeyUsage> = emptyList(),
  val routing: AiRouting = AiRouting(),
  /** `false` where keys can't be kept safely. */
  val available: Boolean = true,
  /** The time of the load, for the days left on each key. */
  val now: Long = 0,
  /** Whether the OpenCode CLI is installed on this computer. */
  val openCode: OpenCodeStatus = OpenCodeStatus(),
  /** Whether the Antigravity CLI (`agy`) is installed on this computer. */
  val agy: AgyStatus = AgyStatus(),
)

/** The OpenCode CLI on this computer: [version] as it reports it, e.g. "v2.0.31". */
data class OpenCodeStatus(val installed: Boolean = false, val version: String? = null)

/** The Antigravity CLI (`agy`) on this computer: [version] as it reports it, e.g. "1.4.0". */
data class AgyStatus(val installed: Boolean = false, val version: String? = null)

/** What adding a key found. */
sealed interface AddKeyOutcome {
  data class Added(val key: AiKey) : AddKeyOutcome

  /** The key's look doesn't say which site it is from. */
  data object Unknown : AddKeyOutcome

  /** The site refused the key. */
  data object Refused : AddKeyOutcome

  data class Failed(val reason: String) : AddKeyOutcome
}

/**
 * Free online keys that take AI work off this computer. Only the desktop binds it: keys never leave
 * the computer.
 */
interface AiKeys {
  suspend fun load(): AiKeysState

  /** [expiresAt] is the expiry the user chose on the site; `null` when none. */
  suspend fun add(secret: String, expiresAt: Long?): AddKeyOutcome

  /** Records when [id] expires; `null` clears it. */
  suspend fun setExpiry(id: String, expiresAt: Long?)

  suspend fun remove(id: String)

  suspend fun setRouting(routing: AiRouting)

  /** Every key with its secret, only for a password-locked export. */
  suspend fun backupKeys(): List<BackupApiKey>

  /** Puts back the keys from a locked backup; returns how many were added. */
  suspend fun restoreKeys(keys: List<BackupApiKey>): Int
}
