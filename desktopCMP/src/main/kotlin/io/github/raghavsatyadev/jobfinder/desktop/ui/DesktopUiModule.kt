package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.desktop.scan.DesktopScanRepository
import io.github.raghavsatyadev.jobfinder.desktop.server.RefreshingSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.http.ApiServerHost
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoice
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * What the shared UI needs from the desktop beyond the server's repositories: the in-process
 * [ScanRepository], [DesktopFeatures], and a [SettingsRepository] that moves the API server to a
 * new port at once. Needs `serverModule` and the `CefRuntime` binding.
 */
val desktopUiModule = module {
  single { PickedPages() }
  single { LoginBrowserState() }
  single<FilePicker> { DesktopFilePicker() }
  single<AiChoice> { DesktopAiChoice(get(), get()) }
  single<AiKeys> { DesktopAiKeys(get(), get(), getOrNull(), getOrNull()) }
  single<AgentAccess> { DesktopAgentAccess(get()) }
  single<ScanRepository> { DesktopScanRepository(get(), get(), get(), get()) }
  single<SettingsRepository> {
    val scope = this
    RefreshingSettingsRepository(get<LocalSettingsRepository>()) {
      scope.get<ApiServerHost>().refresh()
    }
  }
  single<DesktopFeatures> {
    DesktopAppFeatures(
      get(),
      get(),
      get(),
      get(),
      get(),
      signIn = getOrNull(),
      phones = getOrNull(),
      extensionSignIns = getOrNull(),
      setupMemory = getOrNull(),
    )
  } onClose
    {
      (it as? AutoCloseable)?.close()
    }
}
