package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.server.ai.AddKeyResult
import io.github.raghavsatyadev.jobfinder.server.ai.AgyClient
import io.github.raghavsatyadev.jobfinder.server.ai.AiKeyPool
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeServer
import io.github.raghavsatyadev.jobfinder.server.ai.SecretStore
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.BackupApiKey
import io.github.raghavsatyadev.jobfinder.ui.platform.AddKeyOutcome
import io.github.raghavsatyadev.jobfinder.ui.platform.AgyStatus
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeysState
import io.github.raghavsatyadev.jobfinder.ui.platform.OpenCodeStatus
import kotlinx.coroutines.CancellationException

/** [AiKeys] on the in-process [AiKeyPool]; the keys stay in its protected store. */
class DesktopAiKeys(
  private val pool: AiKeyPool,
  private val secrets: SecretStore,
  private val openCode: OpenCodeServer? = null,
  private val agy: AgyClient? = null,
) : AiKeys {
  override suspend fun load(): AiKeysState =
    AiKeysState(
      pool.list(),
      pool.usageToday(),
      pool.routing(),
      secrets.available,
      System.currentTimeMillis(),
      openCodeStatus(),
      agyStatus(),
    )

  private suspend fun openCodeStatus(): OpenCodeStatus {
    val server = openCode ?: return OpenCodeStatus()
    if (server.command() == null) return OpenCodeStatus()
    return OpenCodeStatus(installed = true, version = server.version())
  }

  private suspend fun agyStatus(): AgyStatus {
    val client = agy ?: return AgyStatus()
    if (client.command() == null) return AgyStatus()
    return AgyStatus(installed = true, version = client.version())
  }

  override suspend fun add(secret: String, expiresAt: Long?): AddKeyOutcome =
    try {
      when (val result = pool.add(secret, expiresAt = expiresAt)) {
        is AddKeyResult.Added -> AddKeyOutcome.Added(result.key)
        AddKeyResult.UnknownProvider -> AddKeyOutcome.Unknown
        AddKeyResult.Refused -> AddKeyOutcome.Refused
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      AddKeyOutcome.Failed(e.message.orEmpty())
    }

  override suspend fun setExpiry(id: String, expiresAt: Long?) = pool.setExpiry(id, expiresAt)

  override suspend fun remove(id: String) = pool.remove(id)

  override suspend fun update(key: AiKey) = pool.update(key)

  override suspend fun models(id: String): List<String> = pool.models(id)

  override suspend fun setRouting(routing: AiRouting) = pool.setRouting(routing)

  override suspend fun backupKeys(): List<BackupApiKey> = pool.backupKeys()

  override suspend fun restoreKeys(keys: List<BackupApiKey>): Int = pool.restoreKeys(keys)
}
