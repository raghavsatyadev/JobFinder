package io.github.raghavsatyadev.jobfinder.server.repository

import io.github.raghavsatyadev.jobfinder.server.db.AdvancedMatchingDao
import io.github.raghavsatyadev.jobfinder.server.db.AdvancedMatchingEntity
import io.github.raghavsatyadev.jobfinder.server.db.LinksDao
import io.github.raghavsatyadev.jobfinder.server.db.NoteEntity
import io.github.raghavsatyadev.jobfinder.server.db.NotesDao
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDao
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDefaults
import io.github.raghavsatyadev.jobfinder.server.db.SettingsEntity
import io.github.raghavsatyadev.jobfinder.server.db.SitesDao
import io.github.raghavsatyadev.jobfinder.server.db.toAiSettings
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.db.toScannerSettings
import io.github.raghavsatyadev.jobfinder.server.db.toServerSettings
import io.github.raghavsatyadev.jobfinder.server.db.withAi
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.JobLanguages
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [LinksRepository] over the local Room database. */
class LocalLinksRepository(private val dao: LinksDao) : LinksRepository {
  override suspend fun listLinks(): List<Link> = dao.listAll().map { it.toModel() }

  override suspend fun getLink(id: Long): Link? = dao.get(id)?.toModel()

  /** Like the old `updateLink`: title and URL are stored as given. */
  override suspend fun updateLink(id: Long, title: String, url: String): Link? {
    val current = dao.get(id) ?: return null
    dao.update(current.copy(title = title, url = url))
    return getLink(id)
  }

  override suspend fun deleteLink(id: Long) {
    dao.delete(id)
  }
}

/** [SitesRepository] over the seeded `sites` table. */
class LocalSitesRepository(private val dao: SitesDao) : SitesRepository {
  override suspend fun listSites(): List<Site> = dao.listAll().map { it.toModel() }
}

/** [NotesRepository] over the local Room database. */
class LocalNotesRepository(
  private val dao: NotesDao,
  private val clock: () -> Long = System::currentTimeMillis,
) : NotesRepository {
  override suspend fun listNotes(jobId: Long): List<Note> =
    dao.listForJob(jobId).map { it.toModel() }

  override suspend fun createNote(jobId: Long, text: String, files: List<String>): Note {
    val id = dao.insert(NoteEntity(jobId = jobId, text = text, files = files, createdAt = clock()))
    return checkNotNull(dao.get(id)).toModel()
  }

  override suspend fun updateNote(id: Long, text: String): Note? {
    val current = dao.get(id) ?: return null
    dao.update(current.copy(text = text))
    return dao.get(id)?.toModel()
  }

  override suspend fun addFileToNote(id: Long, file: String): Note? {
    val current = dao.get(id) ?: return null
    dao.update(current.copy(files = current.files + file))
    return dao.get(id)?.toModel()
  }

  override suspend fun deleteNote(id: Long) {
    dao.delete(id)
  }

  suspend fun getNote(id: Long): Note? = dao.get(id)?.toModel()
}

/** [MatchingRepository] over the one-row `advanced_matching` table. */
class LocalMatchingRepository(private val dao: AdvancedMatchingDao) : MatchingRepository {
  override suspend fun getConfig(): AdvancedMatchingConfig =
    dao.get()?.toModel() ?: AdvancedMatchingConfig()

  override suspend fun updateConfig(config: AdvancedMatchingConfig): AdvancedMatchingConfig {
    require(config.prompt.length <= AdvancedMatchingConfig.MAX_PROMPT_LENGTH) {
      "prompt is longer than ${AdvancedMatchingConfig.MAX_PROMPT_LENGTH} characters"
    }
    dao.upsert(
      AdvancedMatchingEntity(
        prompt = config.prompt,
        blacklistedCompanies =
          config.blacklistedCompanies.map { it.trim() }.filter { it.isNotEmpty() },
      )
    )
    return getConfig()
  }
}

/**
 * [SettingsRepository] over the one-row `settings` table, plus the pairing token, which only the
 * desktop app reads. The row is created with defaults and a fresh token on first use.
 */
class LocalSettingsRepository(
  private val dao: SettingsDao,
  private val onDetailLayoutChanged: () -> Unit = {},
) : SettingsRepository {
  private val mutex = Mutex()
  private val scannerChanges =
    MutableSharedFlow<ScannerSettings>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  /**
   * The scanner settings now, then after every change, whoever made it (the desktop UI or a paired
   * phone). The scheduler, sleep blocker and browser pool follow it.
   */
  val scannerSettings: Flow<ScannerSettings> =
    scannerChanges.onSubscription { emit(getScannerSettings()) }.distinctUntilChanged()

  override suspend fun getScannerSettings(): ScannerSettings = row().toScannerSettings()

  override suspend fun updateScannerSettings(settings: ScannerSettings): ScannerSettings {
    require(settings.scraperConcurrency in ScannerSettings.SCRAPER_CONCURRENCY_RANGE) {
      "scraperConcurrency must be in ${ScannerSettings.SCRAPER_CONCURRENCY_RANGE}"
    }
    return update {
      it.copy(
        cronRule =
          ScannerSettings.limitFrequency(settings.cronRule?.trim()?.takeIf(String::isNotEmpty)),
        preventSleep = settings.preventSleep,
        useSound = settings.useSound,
        inAppBrowserEnabled = settings.inAppBrowserEnabled,
        scraperConcurrency = settings.scraperConcurrency,
        excludedKeepDays =
          settings.excludedKeepDays?.coerceIn(ScannerSettings.EXCLUDED_KEEP_DAYS_RANGE),
        aiPaused = settings.aiPaused,
        translateTo = settings.translateTo?.takeIf { it in JobLanguages.ALL },
      )
    }
      .toScannerSettings()
      .also { scannerChanges.emit(it) }
  }

  override suspend fun getServerSettings(): ServerSettings = row().toServerSettings()

  override suspend fun updateServerSettings(settings: ServerSettings): ServerSettings {
    require(settings.port in PORT_RANGE) { "port must be in $PORT_RANGE" }
    return update {
      it.copy(
        serverPort = settings.port,
        networkInterface = settings.networkInterface?.trim()?.takeIf(String::isNotEmpty),
      )
    }
      .toServerSettings()
  }

  override suspend fun getDetailLayout(): DetailLayout =
    row().detailLayout?.let {
      runCatching { ApiJson.decodeFromString(DetailLayout.serializer(), it) }.getOrNull()
    } ?: DetailLayout.DEFAULT

  override suspend fun updateDetailLayout(layout: DetailLayout): DetailLayout {
    val clean = layout.normalized()
    update { it.copy(detailLayout = ApiJson.encodeToString(DetailLayout.serializer(), clean)) }
    onDetailLayoutChanged()
    return clean
  }

  /** The AI choice, without the key. */
  suspend fun getAiSettings(): AiSettings = row().toAiSettings()

  suspend fun updateAiSettings(settings: AiSettings): AiSettings = update {
    it.withAi(settings)
  }
    .toAiSettings()

  /** The bearer token paired clients must send. */
  suspend fun pairingToken(): String = row().pairingToken

  /** Replaces the pairing token; every paired client must pair again. Returns the new token. */
  suspend fun rotatePairingToken(): String = update {
    it.copy(pairingToken = SettingsDefaults.newPairingToken())
  }
    .pairingToken

  private suspend fun row(): SettingsEntity =
    dao.get() ?: mutex.withLock { dao.get() ?: SettingsDefaults.newRow().also { dao.upsert(it) } }

  private suspend fun update(change: (SettingsEntity) -> SettingsEntity): SettingsEntity =
    mutex.withLock {
      val updated = change(dao.get() ?: SettingsDefaults.newRow())
      dao.upsert(updated)
      updated
    }

  companion object {
    /** Unprivileged ports only. */
    val PORT_RANGE = 1024..65535
  }
}
