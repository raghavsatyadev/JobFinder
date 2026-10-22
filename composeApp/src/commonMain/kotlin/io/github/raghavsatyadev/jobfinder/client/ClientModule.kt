package io.github.raghavsatyadev.jobfinder.client

import io.github.raghavsatyadev.jobfinder.cache.ImportFiles
import io.github.raghavsatyadev.jobfinder.cache.OfflineAiPromptsRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineAtsRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineBackupRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineGhostRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineInsightsRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineJobsRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineLinksRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineMatchingRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineNotesRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineSettingsRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineSitesRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineTipsRepository
import io.github.raghavsatyadev.jobfinder.cache.OfflineTranslationRepository
import io.github.raghavsatyadev.jobfinder.cache.PhoneCache
import io.github.raghavsatyadev.jobfinder.cache.PhoneCacheDatabase
import io.github.raghavsatyadev.jobfinder.cache.PhoneSync
import io.github.raghavsatyadev.jobfinder.cache.SyncedScanRepository
import io.github.raghavsatyadev.jobfinder.pairing.AddressKeeper
import io.github.raghavsatyadev.jobfinder.pairing.ApprovalPairing
import io.github.raghavsatyadev.jobfinder.pairing.DoctorChecks
import io.github.raghavsatyadev.jobfinder.pairing.LanIp
import io.github.raghavsatyadev.jobfinder.pairing.LanPhonePairing
import io.github.raghavsatyadev.jobfinder.pairing.PairedDesktop
import io.github.raghavsatyadev.jobfinder.pairing.PairingStorage
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteAiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteApi
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteAtsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteBackupRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteGhostRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteInsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteJobReportRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteJobsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteLinksRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteMatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteNotesRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteResumePdfRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteScanRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteSettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteSitesRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteTipsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteTranslationRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobReportRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TranslationRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.PhoneCopy
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 90_000L
private const val SOCKET_TIMEOUT_MS = 60_000L

/** The app-wide coroutine scope of a phone app. */
val APP_SCOPE = named("appScope")

/**
 * An HTTP client that reaches only the computer at [paired]. SSE streams set their own (endless)
 * request timeout.
 */
fun ClientPlatform.pairedHttpClient(paired: () -> LanIp?): HttpClient =
  httpClient(paired) {
    install(SSE)
    install(HttpTimeout) {
      connectTimeoutMillis = CONNECT_TIMEOUT_MS
      requestTimeoutMillis = REQUEST_TIMEOUT_MS
      socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }
  }

/** `true` when the computer in [desktop] answers an API call with its token. */
suspend fun ClientPlatform.answers(desktop: PairedDesktop): Boolean = pairedHttpClient {
  desktop.ip
}
  .use { http ->
    try {
      RemoteSitesRepository(RemoteApi(http) { desktop.endpoint }).listSites()
      true
    } catch (_: ApiFailure) {
      false
    }
  }

/**
 * A phone app's bindings on top of its [ClientPlatform]: every repository over the active
 * computer's API with the offline copy in front, the sync, the pairing, discovery and the
 * connection doctor.
 */
fun clientModule(): Module = module {
  single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
  single {
    val platform = get<ClientPlatform>()
    ApprovalPairing(
      { ip -> platform.pairedHttpClient { ip } },
      { platform.deviceName },
      platform::secureInt,
    )
  }
  single {
    get<ClientPlatform>().pairedHttpClient { get<PairingStorage>().desktop.value?.ip }
  } onClose
    {
      it?.close()
    }
  single { RemoteApi(get()) { get<PairingStorage>().desktop.value?.endpoint } }
  // The phone's copy of the computer's jobs: the screens read through it, so jobs stay on show
  // off the Wi-Fi, and PhoneSync keeps it in step.
  single { PhoneCacheDatabase.create(get<ClientPlatform>().cacheDatabase()) } onClose
    {
      it?.close()
    }
  single {
    val connected = get<RemoteScanRepository>().connected
    PhoneCache(
      dao = get<PhoneCacheDatabase>().dao(),
      computer = { get<PairingStorage>().desktop.value?.key },
      online = { connected.value },
      scope = get(APP_SCOPE),
    )
  }
  single<PhoneCopy> { get<PhoneCache>() }
  single {
    val remote = get<RemoteScanRepository>()
    PhoneSync(
      jobs = RemoteJobsRepository(get()),
      links = RemoteLinksRepository(get()),
      sites = RemoteSitesRepository(get()),
      cache = get(),
      connected = remote.connected,
      events = remote.events,
      computers = get<PairingStorage>().computers.map { list -> list.map { it.key }.toSet() },
      scope = get(APP_SCOPE),
      log = { message, e -> get<ClientPlatform>().log(message, e) },
      backups = RemoteBackupRepository(get()),
      importFiles = get(),
      settings = RemoteSettingsRepository(get()),
      matching = RemoteMatchingRepository(get()),
      ats = RemoteAtsRepository(get()),
      prompts = RemoteAiPromptsRepository(get()),
    )
  }
  single<ImportFiles> { FileImportFiles(get<ClientPlatform>().importsDir) }
  single<BackupRepository> {
    OfflineBackupRepository(RemoteBackupRepository(get()), get(), get()) {
      get<PhoneSync>().syncNow()
    }
  }
  single<JobsRepository> {
    val sync = get<PhoneSync>()
    OfflineJobsRepository(RemoteJobsRepository(get()), get(), sync::requestSync, sync::syncNow)
  }
  single<LinksRepository> { OfflineLinksRepository(RemoteLinksRepository(get()), get()) }
  single<SitesRepository> { OfflineSitesRepository(RemoteSitesRepository(get()), get()) }
  single<NotesRepository> { OfflineNotesRepository(RemoteNotesRepository(get()), get()) }
  single<MatchingRepository> { OfflineMatchingRepository(RemoteMatchingRepository(get()), get()) }
  single<SettingsRepository> { OfflineSettingsRepository(RemoteSettingsRepository(get()), get()) }
  single<AiPromptsRepository> {
    OfflineAiPromptsRepository(RemoteAiPromptsRepository(get()), get())
  }
  single<AtsRepository> { OfflineAtsRepository(RemoteAtsRepository(get()), get()) }
  single<TipsRepository> { OfflineTipsRepository(RemoteTipsRepository(get()), get()) }
  single<TranslationRepository> {
    OfflineTranslationRepository(RemoteTranslationRepository(get()), get())
  }
  single<ResumeImproveRepository> { RemoteResumeImproveRepository(get()) }
  single<ResumePdfRepository> { RemoteResumePdfRepository(get()) }
  single<GhostRepository> { OfflineGhostRepository(RemoteGhostRepository(get()), get()) }
  single<InsightsRepository> {
    OfflineInsightsRepository(RemoteInsightsRepository(get()), get())
  }
  single {
    RemoteScanRepository(
      api = get(),
      links = RemoteLinksRepository(get()),
      scope = get(APP_SCOPE),
      endpoints = get<PairingStorage>().desktop.map { it?.endpoint },
    )
  }
  single<ScanRepository> { SyncedScanRepository(get<RemoteScanRepository>(), get(), get()) }
  single<PhonePairing> {
    val platform = get<ClientPlatform>()
    LanPhonePairing(
      store = get(),
      scanCode = platform::scanCode,
      connected = get<RemoteScanRepository>().connected,
      scope = get(APP_SCOPE),
      verify = { platform.answers(it) },
      approvals = get(),
      discovery = get(),
      beforeDiscovery = platform::awaitLocalNetwork,
    )
  }
  single {
    AddressKeeper(
      get(),
      get<RemoteScanRepository>().connected,
      get(),
      allowed = get<ClientPlatform>()::localNetworkGranted,
    )
  }
  single<ConnectionDoctor> {
    val platform = get<ClientPlatform>()
    val approvals = get<ApprovalPairing>()
    ClientConnectionDoctor(
      checks =
        DoctorChecks(
          store = get(),
          link = platform::localLink,
          discovery = get(),
          probe = platform::probe,
          health = approvals::health,
          token = { desktop ->
            ClientConnectionDoctor.tokenResult {
              platform
                .pairedHttpClient { desktop.ip }
                .use { RemoteSitesRepository(RemoteApi(it) { desktop.endpoint }).listSites() }
            }
          },
        ),
      platform = platform,
    )
  }
  single<JobReportRepository> { RemoteJobReportRepository(get()) }
}
