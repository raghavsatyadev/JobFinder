package io.github.raghavsatyadev.jobfinder

import io.github.raghavsatyadev.jobfinder.ui.fake.FakeBackend
import io.github.raghavsatyadev.jobfinder.ui.filters.aiFiltersUiModule
import io.github.raghavsatyadev.jobfinder.ui.jobs.jobsUiModule
import io.github.raghavsatyadev.jobfinder.ui.links.linksUiModule
import io.github.raghavsatyadev.jobfinder.ui.pairing.pairingUiModule
import io.github.raghavsatyadev.jobfinder.ui.settings.settingsUiModule
import io.github.raghavsatyadev.jobfinder.ui.setup.setupUiModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * The ViewModels of every screen. The repositories they use come from the platform: the server's
 * in-process implementations on desktop, Ktor clients on Android, [fakeRepositoriesModule] in
 * previews, tests and the Android debug build.
 */
val appModule: Module = module {
  includes(
    jobsUiModule,
    linksUiModule,
    aiFiltersUiModule,
    settingsUiModule,
    pairingUiModule,
    setupUiModule,
  )
}

/** Binds every repository interface to one [FakeBackend]. */
fun fakeRepositoriesModule(backend: FakeBackend = FakeBackend()): Module = module {
  single { backend }
  single { backend.jobsRepository }
  single { backend.linksRepository }
  single { backend.sitesRepository }
  single { backend.notesRepository }
  single { backend.matchingRepository }
  single { backend.settingsRepository }
  single { backend.scanRepository }
  single { backend.atsRepository }
  single { backend.insightsRepository }
}

/** Starts Koin once per process. Call it before any UI is created. */
fun initKoin(config: KoinAppDeclaration? = null, extraModules: List<Module> = emptyList()) {
  startKoin {
    config?.invoke(this)
    modules(listOf(appModule) + extraModules)
  }
}
