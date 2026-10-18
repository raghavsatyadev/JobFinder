package io.github.raghavsatyadev.jobfinder.desktop.server

import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository

/**
 * The desktop UI's [SettingsRepository]: [local], plus [onServerSettingsChanged] after a port or
 * interface change, so the API server moves to the new port now instead of at its next address
 * check.
 */
class RefreshingSettingsRepository(
  private val local: SettingsRepository,
  private val onServerSettingsChanged: suspend () -> Unit,
) : SettingsRepository by local {
  override suspend fun updateServerSettings(settings: ServerSettings): ServerSettings {
    val before = local.getServerSettings()
    val saved = local.updateServerSettings(settings)
    if (saved != before) onServerSettingsChanged()
    return saved
  }
}
