package io.github.raghavsatyadev.jobfinder.desktop.server

import io.github.raghavsatyadev.jobfinder.desktop.models.DesktopLocalModels
import io.github.raghavsatyadev.jobfinder.desktop.models.GgufDownload
import io.github.raghavsatyadev.jobfinder.desktop.models.HttpOllamaModels
import io.github.raghavsatyadev.jobfinder.desktop.models.HuggingFace
import io.github.raghavsatyadev.jobfinder.desktop.models.ModelCheckStore
import io.github.raghavsatyadev.jobfinder.desktop.models.PcProfiler
import io.github.raghavsatyadev.jobfinder.desktop.secrets.DesktopDeviceBackupKeys
import io.github.raghavsatyadev.jobfinder.desktop.secrets.DpapiSecretStore
import io.github.raghavsatyadev.jobfinder.desktop.setup.PowerShell
import io.github.raghavsatyadev.jobfinder.server.ai.AccuracyCheck
import io.github.raghavsatyadev.jobfinder.server.ai.AgyClient
import io.github.raghavsatyadev.jobfinder.server.ai.AiKeyPool
import io.github.raghavsatyadev.jobfinder.server.ai.AiService
import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeClient
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeServer
import io.github.raghavsatyadev.jobfinder.server.ai.PromptBook
import io.github.raghavsatyadev.jobfinder.server.ai.PromptTrials
import io.github.raghavsatyadev.jobfinder.server.ai.SecretStore
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabaseFactory
import io.github.raghavsatyadev.jobfinder.server.forms.FormAnswersService
import io.github.raghavsatyadev.jobfinder.server.http.ApiDependencies
import io.github.raghavsatyadev.jobfinder.server.http.ApiServerHost
import io.github.raghavsatyadev.jobfinder.server.http.PairedPhones
import io.github.raghavsatyadev.jobfinder.server.http.PairingRequests
import io.github.raghavsatyadev.jobfinder.server.http.RestoreRequests
import io.github.raghavsatyadev.jobfinder.server.http.ServerIdentity
import io.github.raghavsatyadev.jobfinder.server.nativehost.FormMessages
import io.github.raghavsatyadev.jobfinder.server.nativehost.JobUrlLookup
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostLink
import io.github.raghavsatyadev.jobfinder.server.parsing.JobPageParsers
import io.github.raghavsatyadev.jobfinder.server.parsing.custom.OllamaCustomJobsParser
import io.github.raghavsatyadev.jobfinder.server.parsing.custom.RoomAiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.repository.LocalJobsRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalLinksRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalMatchingRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalNotesRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSitesRepository
import io.github.raghavsatyadev.jobfinder.server.scan.BrowserPool
import io.github.raghavsatyadev.jobfinder.server.scan.JobScanner
import io.github.raghavsatyadev.jobfinder.server.scan.PageScraper
import io.github.raghavsatyadev.jobfinder.server.scan.Scanner
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.scan.UnavailableScanner
import io.github.raghavsatyadev.jobfinder.server.service.AdvancedMatchingFilter
import io.github.raghavsatyadev.jobfinder.server.service.AiWorkPause
import io.github.raghavsatyadev.jobfinder.server.service.BackupService
import io.github.raghavsatyadev.jobfinder.server.service.CustomBoards
import io.github.raghavsatyadev.jobfinder.server.service.JobIngestService
import io.github.raghavsatyadev.jobfinder.server.service.JobRetention
import io.github.raghavsatyadev.jobfinder.server.service.JobsCsvExporter
import io.github.raghavsatyadev.jobfinder.server.service.LinkService
import io.github.raghavsatyadev.jobfinder.server.service.MergedJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.NoteFiles
import io.github.raghavsatyadev.jobfinder.server.service.OllamaJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.ats.AtsService
import io.github.raghavsatyadev.jobfinder.server.service.ats.OllamaAtsModel
import io.github.raghavsatyadev.jobfinder.server.service.ats.ResumeImproveService
import io.github.raghavsatyadev.jobfinder.server.service.ghost.CareersMatcher
import io.github.raghavsatyadev.jobfinder.server.service.ghost.CareersReader
import io.github.raghavsatyadev.jobfinder.server.service.ghost.GhostService
import io.github.raghavsatyadev.jobfinder.server.service.insights.InsightsService
import io.github.raghavsatyadev.jobfinder.server.service.insights.ScraperInsightPages
import io.github.raghavsatyadev.jobfinder.server.service.tips.TipsFacts
import io.github.raghavsatyadev.jobfinder.server.service.tips.TipsService
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.backup.DeviceBackupKeys
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose

/**
 * The embedded server in the desktop Koin graph. The UI gets the shared repository interfaces bound
 * to the in-process implementations (no HTTP round trip); [ApiServerHost] serves the same objects
 * to paired phones.
 *
 * @param dataDir `%LOCALAPPDATA%\jobfinder-data`.
 */
fun serverModule(dataDir: File) = module {
  single<ServerDatabase> { ServerDatabaseFactory.create(dataDir.resolve(DB_FILE).toPath()) } onClose
    {
      it?.close()
    }
  single {
    LocalJobsRepository(
      get<ServerDatabase>().jobsDao(),
      atsSummary = { get<AtsService>().summary(it) },
    )
  } bind JobsRepository::class
  single { LocalLinksRepository(get<ServerDatabase>().linksDao()) } bind LinksRepository::class
  single { LocalSitesRepository(get<ServerDatabase>().sitesDao()) } bind SitesRepository::class
  single { LocalNotesRepository(get<ServerDatabase>().notesDao()) } bind NotesRepository::class
  single { LocalMatchingRepository(get<ServerDatabase>().advancedMatchingDao()) } bind
    MatchingRepository::class
  // The UI's SettingsRepository is bound in desktopUiModule (it also restarts the API server).
  single {
    val events = get<ServerEvents>()
    LocalSettingsRepository(get<ServerDatabase>().settingsDao()) {
      events.emit(ServerEvent.DetailLayoutChanged)
    }
  }

  single { ServerEvents() }
  // The scan pipeline needs the browser pool, which only the running app binds (desktopModule).
  single<Scanner> { getOrNull<JobScanner>() ?: UnavailableScanner() }
  // API keys sit in DPAPI files next to the database, never in it.
  single<SecretStore> { DpapiSecretStore.forThisComputer(dataDir.resolve(SECRETS_DIR).toPath()) }
  // Locks the backups saved without a password; kept like the API keys.
  single<DeviceBackupKeys> {
    DesktopDeviceBackupKeys(
      get(),
      dataDir.resolve(SECRETS_DIR).resolve(DesktopDeviceBackupKeys.NAME).toPath(),
    )
  }
  single { AiService(get(), get()) }
  single<LocalModels> {
    val http = LlmClient.defaultHttpClient()
    DesktopLocalModels(
      ollama = HttpOllamaModels(http),
      huggingFace = HuggingFace(http),
      download = GgufDownload(http, dataDir.resolve(MODELS_DIR)),
      profiler = PcProfiler(PowerShell())::profile,
      accuracy = AccuracyCheck(get(), http),
      checks = ModelCheckStore(dataDir.resolve(MODEL_CHECKS_FILE)),
    )
  }
  // The online keys the user added and which model does which work.
  single {
    val db = get<ServerDatabase>()
    AiKeyPool(db.aiKeysDao(), db.settingsDao(), get(), LlmClient.defaultHttpClient())
  }
  // One client for every LLM call: its semaphore lets one request at a time reach the main model,
  // so
  // the custom parser and matching never make Ollama swap models mid-scan; online keys run in
  // parallel. It reads the AI choice from Settings before every call.
  single {
    val ai = get<AiService>()
    LlmClient(LlmClient.defaultHttpClient(), Semaphore(1), get<AiKeyPool>(), get(), get()) {
      ai.config()
    }
  }
  // The user's signed-in Antigravity CLI (agy) as a model source: one child per call.
  single { AgyClient() }
  // The user's OpenCode CLI as a free model source: started at its first call, stopped on exit.
  single {
    OpenCodeServer(dataDir.resolve(OPENCODE_DIR).toPath(), LlmClient.defaultHttpClient())
  } onClose
    {
      it?.close()
    }
  single { OpenCodeClient(get(), LlmClient.defaultHttpClient()) }
  single<AiUsageRecorder> { RoomAiUsageRecorder(get<ServerDatabase>().aiUsageDao()) }
  // The AI instructions, as edited in Settings; read before every call.
  single {
    val db = get<ServerDatabase>()
    PromptTrials(db.jobsDao(), db.resumeDao(), db.advancedMatchingDao(), get())
  }
  single {
    val scope = this
    PromptBook(get<ServerDatabase>().aiPromptsDao()) { id, text ->
      scope.get<PromptTrials>().run(id, text)
    }
  } bind AiPromptsRepository::class
  single {
    JobPageParsers(
      customParser =
        OllamaCustomJobsParser(
          get(),
          get(),
          preferences = { get<MatchingRepository>().getConfig().prompt.ifBlank { null } },
          listInstructions = { get<PromptBook>().instructions(PromptId.PARSE_LIST) },
          descriptionInstructions = {
            get<PromptBook>().instructions(PromptId.PARSE_DESCRIPTION)
          },
        )
    )
  }
  single {
    // The ATS review rides in the filter's request when both go to the same online model.
    AdvancedMatchingFilter(
      get(),
      MergedJobCheck(
        get(),
        OllamaJobCheck(get(), get()) { get<PromptBook>().instructions(PromptId.MATCHING) },
        riders = { listOf(get<AtsService>()) },
        usage = get(),
      ),
    )
  }
  single { JobIngestService(get(), get(), get()) }
  single { CustomBoards(get()) }
  single {
    JobRetention(
      get<ServerDatabase>().jobsDao(),
      scannerSettings = { get<LocalSettingsRepository>().getScannerSettings() },
    )
  }
  single {
    LinkService(
      get(),
      get(),
      get(),
      getOrNull<BrowserPool>()?.let { PageScraper(it) },
      customBoards = get(),
    )
  }
  single(ATS_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) } onClose
    {
      it?.cancel()
    }
  single { AiWorkPause(get<LocalSettingsRepository>().scannerSettings, get(ATS_SCOPE)) }
  // Scores jobs against the resume in the background, on the same model client as matching.
  single {
    val db = get<ServerDatabase>()
    AtsService(
        db.resumeDao(),
        db.atsReviewsDao(),
        db.jobsDao(),
        OllamaAtsModel(get(), get()) { get<PromptBook>().instructions(PromptId.ATS_REVIEW) },
        get(),
        pause = get(),
      )
      .also { it.start(get(ATS_SCOPE)) }
  } bind AtsRepository::class
  // Company ratings and salary, read in the scan browsers one page at a time.
  single {
    val db = get<ServerDatabase>()
    InsightsService(
        db.insightsDao(),
        db.jobsDao(),
        getOrNull<BrowserPool>()?.let { ScraperInsightPages(PageScraper(it)) },
        get(),
        aiPause = get(),
      )
      .also { it.start(get(ATS_SCOPE)) }
  } bind InsightsRepository::class
  // Whether the company's own careers site lists each job, from the hosts' public job lists.
  single {
    val db = get<ServerDatabase>()
    GhostService(
        db.ghostDao(),
        db.jobsDao(),
        CareersReader(LlmClient.defaultHttpClient()),
        CareersMatcher(get(), get()) { get<PromptBook>().instructions(PromptId.GHOST_MATCH) },
        get(),
        aiPause = get(),
      )
      .also { it.start(get(ATS_SCOPE)) }
  } bind GhostRepository::class
  // Negotiation tips, written by the AI when the user asks.
  single {
    TipsService(
        get<ServerDatabase>().tipsDao(),
        get<ServerDatabase>().jobsDao(),
        get(),
        facts = { jobId ->
          TipsFacts(
            review = runCatching { get<AtsRepository>().requestReview(jobId).review }.getOrNull(),
            insights =
              runCatching { get<InsightsRepository>().requestInsights(jobId).insights }.getOrNull(),
            ghost =
              runCatching { get<GhostRepository>().requestGhostCheck(jobId).check }.getOrNull(),
          )
        },
        events = get(),
        usage = get(),
        instructions = { get<PromptBook>().instructions(PromptId.NEGOTIATION_TIPS) },
      )
      .also { it.start(get(ATS_SCOPE)) }
  } bind TipsRepository::class
  // Resume fix plans for one job, written by the AI when the user asks; the resume goes where
  // ATS work is routed.
  single {
    ResumeImproveService(
        get<ServerDatabase>().resumeDao(),
        get<ServerDatabase>().jobsDao(),
        get(),
        review = { get<AtsService>().storedReview(it) },
        events = get(),
        usage = get(),
        instructions = { get<PromptBook>().instructions(PromptId.RESUME_IMPROVE) },
      )
      .also { it.start(get(ATS_SCOPE)) }
  } bind ResumeImproveRepository::class
  single { JobsCsvExporter(get(), get()) }
  single { NoteFiles(dataDir.resolve(NOTES_DIR).toPath()) }
  single {
    BackupService(
      db = get(),
      // The UI's settings restart the API server when the port changes; tests have none.
      settings = { getOrNull<SettingsRepository>() ?: get<LocalSettingsRepository>() },
      matching = get(),
      noteFiles = get(),
      events = get(),
      backupDir = dataDir.resolve(BACKUP_DIR).toPath(),
      appVersion = System.getProperty(APP_VERSION_PROPERTY).orEmpty(),
      restoreResume = { get<AtsService>().restoreResume(it) },
      jobsCsv = { get<JobsCsvExporter>().export() },
      deviceKeys = get(),
    )
  } bind BackupRepository::class
  single { ServerIdentity.load(dataDir.resolve(SERVER_ID_FILE).toPath()) }
  single { PairingRequests(token = { get<LocalSettingsRepository>().pairingToken() }) }
  single { PairedPhones(dataDir.resolve(PHONE_PAIRED_FILE).toPath()) }
  single { RestoreRequests() }
  single {
    ApiDependencies(
      jobs = get(),
      links = get(),
      sites = get(),
      notes = get(),
      matching = get(),
      settings = get(),
      linkService = get(),
      scanner = get(),
      events = get(),
      csv = get(),
      noteFiles = get(),
      identity = get(),
      pairingRequests = get(),
      phones = get(),
      ats = get(),
      insights = get(),
      prompts = get(),
      ghost = get(),
      tips = get(),
      improve = get(),
      backup = get(),
      restoreRequests = get(),
    )
  }
  single { ApiServerHost.forApi(get()) }
  single { JobUrlLookup(get<ServerDatabase>().jobsDao()) { get<AtsService>().summary(it) } }
  // Application form answers from the browser extension: desktop only, never served to the phone.
  single {
    val db = get<ServerDatabase>()
    FormAnswersService(db.formAnswersDao(), db.jobsDao(), db.resumeDao(), get(), get())
  } bind FormAnswersRepository::class
  // The browser extension's native host connects here; EmbeddedServer starts and stops it.
  single {
    NativeHostLink(
      connectionFile = dataDir.resolve(NativeHostLink.CONNECTION_FILE).toPath(),
      appVersion = System.getProperty(APP_VERSION_PROPERTY).orEmpty(),
      jobs = get(),
      forms = FormMessages(get()),
    )
  }
}

/** Background work of the ATS scorer and the company insights reader. */
private val ATS_SCOPE = named("atsScope")
private const val DB_FILE = "jobfinder.db"
private const val NOTES_DIR = "notes"
private const val SECRETS_DIR = "secrets"
/** The OpenCode child's own (empty) config folder, so the user's global config stays out. */
private const val OPENCODE_DIR = "opencode"
/** Partial downloads of models added from a link; empty once Ollama has them. */
private const val MODELS_DIR = "models"
private const val MODEL_CHECKS_FILE = "model-checks.json"
private const val BACKUP_DIR = "backups"
/** Set by the installer's launcher; empty when run from the build. */
private const val APP_VERSION_PROPERTY = "jpackage.app-version"
private const val SERVER_ID_FILE = "server-id"
private const val PHONE_PAIRED_FILE = "phone-paired"
