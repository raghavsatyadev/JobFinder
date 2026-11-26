package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDefaults
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting

/**
 * The saved AI routing (which AI does which work, in order); `null` when never set or unreadable.
 */
internal suspend fun ServerDatabase.backupRouting(): AiRouting? =
  settingsDao().get()?.aiRouting?.let {
    runCatching { ApiJson.decodeFromString(AiRouting.serializer(), it) }.getOrNull()
  }

/** Saves [routing] from a backup over this computer's. */
internal suspend fun ServerDatabase.restoreRouting(routing: AiRouting) {
  val dao = settingsDao()
  val row = dao.get() ?: SettingsDefaults.newRow()
  dao.upsert(row.copy(aiRouting = ApiJson.encodeToString(AiRouting.serializer(), routing)))
}
