package io.github.raghavsatyadev.jobfinder

import android.content.Context
import android.net.nsd.NsdManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.room3.Room
import io.github.raghavsatyadev.jobfinder.cache.ImportFiles
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
import io.github.raghavsatyadev.jobfinder.cache.PhoneCache
import io.github.raghavsatyadev.jobfinder.cache.PhoneCacheDatabase
import io.github.raghavsatyadev.jobfinder.cache.PhoneSync
import io.github.raghavsatyadev.jobfinder.cache.SyncedScanRepository
import io.github.raghavsatyadev.jobfinder.pairing.AddressKeeper
import io.github.raghavsatyadev.jobfinder.pairing.AndroidConnectionDoctor
import io.github.raghavsatyadev.jobfinder.pairing.ApprovalPairing
import io.github.raghavsatyadev.jobfinder.pairing.CurrentActivity
import io.github.raghavsatyadev.jobfinder.pairing.DoctorChecks
import io.github.raghavsatyadev.jobfinder.pairing.LanDiscovery
import io.github.raghavsatyadev.jobfinder.pairing.LanIp
import io.github.raghavsatyadev.jobfinder.pairing.LanPhonePairing
import io.github.raghavsatyadev.jobfinder.pairing.LocalNetworkAccess
import io.github.raghavsatyadev.jobfinder.pairing.NsdDiscovery
import io.github.raghavsatyadev.jobfinder.pairing.PairedDesktop
import io.github.raghavsatyadev.jobfinder.pairing.PairedHostDns
import io.github.raghavsatyadev.jobfinder.pairing.PairingStorage
import io.github.raghavsatyadev.jobfinder.pairing.PairingStore
import io.github.raghavsatyadev.jobfinder.pairing.QrCodeScanner
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteAiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteApi
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteAtsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteBackupRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteGhostRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteInsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteJobsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteLinksRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteMatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteNotesRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteScanRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteSettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteSitesRepository
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteTipsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.ConnectionDoctor
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import io.github.raghavsatyadev.jobfinder.ui.platform.PhoneCopy
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 90_000L
private const val SOCKET_TIMEOUT_MS = 60_000L
private val APP_SCOPE = named("appScope")

/** Backups imported on the phone, in the app's files, until the computer has them. */
private const val IMPORTS_DIR = "imports"

/**
 * An HTTP client that reaches only the computer at [paired], through [PairedHostDns]. SSE streams
 * set their own (endless) request timeout.
 */
fun pairedHttpClient(paired: () -> LanIp?): HttpClient =
  HttpClient(OkHttp) {
    engine { config { dns(PairedHostDns(paired)) } }
    install(SSE)
    install(HttpTimeout) {
      connectTimeoutMillis = CONNECT_TIMEOUT_MS
      requestTimeoutMillis = REQUEST_TIMEOUT_MS
      socketTimeoutMillis = SOCKET_TIMEOUT_MS
    }
  }

/** `true` when the computer in [desktop] answers an API call with its token. */
suspend fun answers(desktop: PairedDesktop): Boolean = pairedHttpClient {
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

/** This phone's name as the computer's approval dialog shows it. */
fun deviceName(context: Context): String =
  Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf {
    it.isNotBlank()
  } ?: "${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * Every repository over the active computer's API, the pairing, discovery, the connection doctor
 * and the QR scanner.
 */
fun remoteModule(): Module = module {
  single(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
  single<PairingStorage> { PairingStore(get()) }
  single { CurrentActivity() }
  single { QrCodeScanner(get()) }
  single<FilePicker> { AndroidFilePicker(get()) }
  single { LocalNetworkAccess(get()) }
  single<LanDiscovery> { NsdDiscovery(get<Context>().getSystemService(NsdManager::class.java)) }
  single {
    val random = SecureRandom()
    ApprovalPairing({ ip -> pairedHttpClient { ip } }, { deviceName(get()) }, random::nextInt)
  }
  single { pairedHttpClient { get<PairingStorage>().desktop.value?.ip } } onClose { it?.close() }
  single { RemoteApi(get()) { get<PairingStorage>().desktop.value?.endpoint } }
  // The phone's copy of the computer's jobs: the screens read through it, so jobs stay on show
  // off the Wi-Fi, and PhoneSync keeps it in step.
  single {
    val context = get<Context>()
    PhoneCacheDatabase.create(
      Room.databaseBuilder<PhoneCacheDatabase>(
        context,
        context.getDatabasePath(PhoneCacheDatabase.NAME).absolutePath,
      )
    )
  } onClose { it?.close() }
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
      log = { message, e -> Log.w("PhoneSync", message, e) },
      backups = RemoteBackupRepository(get()),
      importFiles = get(),
      settings = RemoteSettingsRepository(get()),
      matching = RemoteMatchingRepository(get()),
      ats = RemoteAtsRepository(get()),
    )
  }
  single<ImportFiles> { FileImportFiles(get<Context>().filesDir.resolve(IMPORTS_DIR)) }
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
  single<AiPromptsRepository> { RemoteAiPromptsRepository(get()) }
  single<AtsRepository> { OfflineAtsRepository(RemoteAtsRepository(get()), get()) }
  single<TipsRepository> { OfflineTipsRepository(RemoteTipsRepository(get()), get()) }
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
    val scanner = get<QrCodeScanner>()
    LanPhonePairing(
      store = get(),
      scanCode = scanner::scan,
      connected = get<RemoteScanRepository>().connected,
      scope = get(APP_SCOPE),
      verify = ::answers,
      approvals = get(),
      discovery = get(),
      beforeDiscovery = get<LocalNetworkAccess>()::awaitAnswer,
    )
  }
  single {
    AddressKeeper(
      get(),
      get<RemoteScanRepository>().connected,
      get(),
      allowed = get<LocalNetworkAccess>()::granted,
    )
  }
  single<ConnectionDoctor> {
    val approvals = get<ApprovalPairing>()
    AndroidConnectionDoctor(
      context = get(),
      checks =
        DoctorChecks(
          store = get(),
          link = { AndroidConnectionDoctor.localLink(get()) },
          discovery = get(),
          probe = { ip, port -> AndroidConnectionDoctor.probe(ip, port) },
          health = approvals::health,
          token = { desktop ->
            AndroidConnectionDoctor.tokenResult {
              pairedHttpClient { desktop.ip }
                .use { RemoteSitesRepository(RemoteApi(it) { desktop.endpoint }).listSites() }
            }
          },
        ),
      activities = get(),
    )
  }
  single { NewJobsNotifier(get(), get(), get(APP_SCOPE)) }
}
