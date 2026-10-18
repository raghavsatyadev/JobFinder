package io.github.raghavsatyadev.jobfinder.server.service

import androidx.room3.withWriteTransaction
import io.github.raghavsatyadev.jobfinder.server.ai.PromptBook
import io.github.raghavsatyadev.jobfinder.server.db.AiUsageDailyEntity
import io.github.raghavsatyadev.jobfinder.server.db.AtsReviewEntity
import io.github.raghavsatyadev.jobfinder.server.db.CompanyRatingsEntity
import io.github.raghavsatyadev.jobfinder.server.db.CompanySalaryEntity
import io.github.raghavsatyadev.jobfinder.server.db.FormAnswerEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.LinkEntity
import io.github.raghavsatyadev.jobfinder.server.db.NoteEntity
import io.github.raghavsatyadev.jobfinder.server.db.ResumeEntity
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.SettingsDefaults
import io.github.raghavsatyadev.jobfinder.server.db.toAiSettings
import io.github.raghavsatyadev.jobfinder.server.db.withAi
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFormatException
import io.github.raghavsatyadev.jobfinder.shared.backup.FullBackup
import io.github.raghavsatyadev.jobfinder.shared.model.BackupAiUsage
import io.github.raghavsatyadev.jobfinder.shared.model.BackupAtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.BackupCompanyRatings
import io.github.raghavsatyadev.jobfinder.shared.model.BackupCompanySalary
import io.github.raghavsatyadev.jobfinder.shared.model.BackupData
import io.github.raghavsatyadev.jobfinder.shared.model.BackupFormAnswer
import io.github.raghavsatyadev.jobfinder.shared.model.BackupJob
import io.github.raghavsatyadev.jobfinder.shared.model.BackupLink
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import io.github.raghavsatyadev.jobfinder.shared.model.BackupNote
import io.github.raghavsatyadev.jobfinder.shared.model.BackupResume
import io.github.raghavsatyadev.jobfinder.shared.model.BackupSearch
import io.github.raghavsatyadev.jobfinder.shared.model.BackupSite
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.ImportSummary
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Settings and full backups of the desktop database (see [BackupFiles]). Never exports the pairing
 * token, site sign-ins or debug page dumps. A merge adds what is missing (searches by URL, jobs by
 * their id on the job board, with their notes and reviews); a replace first saves the current data
 * to [backupDir]. Every import tells open lists to reload with an empty [ServerEvent.NewJobs].
 */
class BackupService(
  private val db: ServerDatabase,
  private val settings: () -> SettingsRepository,
  private val matching: MatchingRepository,
  private val noteFiles: NoteFiles,
  private val events: ServerEvents,
  /** Where automatic backups and the copies made before a replace go. */
  private val backupDir: Path,
  private val appVersion: String = "",
  /** Puts back a resume from a backup and scores jobs again; `null` keeps it as is. */
  private val restoreResume: (suspend (ResumeEntity) -> Unit)? = null,
  /** The jobs as CSV ([JobsCsvExporter]); `null` exports only the header. */
  private val jobsCsv: (suspend () -> String)? = null,
  private val clock: () -> Long = System::currentTimeMillis,
) : BackupRepository {
  /** One import or export at a time. */
  private val lock = Mutex()
  private val customBoards = CustomBoards(db, clock)

  override suspend fun exportSettings(parts: Set<SettingsPart>): SettingsBackup = lock.withLock {
    settingsBackup(parts)
  }

  override suspend fun importSettings(
    backup: SettingsBackup,
    parts: Set<SettingsPart>,
  ): ImportSummary = lock.withLock {
    val chosen = backup.only(parts)
    val added = chosen.searches?.let { addSearches(it) } ?: 0
    val applied = applySettings(chosen)
    if (added > 0) events.emit(ServerEvent.NewJobs(emptyList()))
    ImportSummary(searchesAdded = added, settings = applied.toList())
  }

  override suspend fun exportFull(): ByteArray = lock.withLock { full() }

  override suspend fun exportJobsCsv(): ByteArray =
    (jobsCsv?.invoke() ?: JobsCsvExporter.HEADER.joinToString(",")).encodeToByteArray()

  /** A full backup without the application form answers, which never go to the phone. */
  suspend fun exportFullForPhone(): ByteArray = lock.withLock { full(formAnswers = false) }

  override suspend fun importFull(bytes: ByteArray, mode: ImportMode): ImportSummary =
    lock.withLock {
      val backup = withContext(Dispatchers.Default) { BackupFiles.decodeFull(bytes) }
      val summary =
        when (mode) {
          ImportMode.MERGE -> merge(backup)
          ImportMode.REPLACE -> {
            checkReplace(backup.manifest)
            replace(backup)
          }
        }
      events.emit(ServerEvent.NewJobs(emptyList()))
      log.info(
        "Imported a backup ({}): {} searches, {} jobs, {} notes added",
        mode,
        summary.searchesAdded,
        summary.jobsAdded,
        summary.notesAdded,
      )
      summary
    }

  /**
   * Saves a full backup to [backupDir] when the newest automatic one is older than [everyMs], and
   * keeps the newest [keep]. Returns the file written, or `null`.
   */
  suspend fun automaticBackup(everyMs: Long = DAY_MS, keep: Int = AUTO_KEEP): Path? =
    lock.withLock {
      val existing = automaticBackups()
      val newest = existing.firstOrNull()?.let { java.nio.file.Files.getLastModifiedTime(it) }
      if (newest != null && clock() - newest.toMillis() < everyMs) return@withLock null
      val file = write(AUTO_PREFIX, full())
      (listOf(file) + existing).drop(keep).forEach { java.nio.file.Files.deleteIfExists(it) }
      file
    }

  /** The automatic backups, newest first, with those saved under the app's old name. */
  fun automaticBackups(): List<Path> =
    if (!java.nio.file.Files.isDirectory(backupDir)) emptyList()
    else
      backupDir.listDirectoryEntries("{$AUTO_PREFIX,$OLD_AUTO_PREFIX}*.zip").sortedByDescending {
        java.nio.file.Files.getLastModifiedTime(it)
      }

  // Export

  private suspend fun settingsBackup(parts: Set<SettingsPart>): SettingsBackup {
    val sites = db.sitesDao().listAll().associate { it.id to it.name }
    return SettingsBackup(
        appVersion = appVersion,
        exportedAt = clock(),
        searches =
          db.linksDao().listAll().reversed().mapNotNull { link ->
            sites[link.siteId]?.let { BackupSearch(link.title, link.url, it) }
          },
        matching = matching.getConfig(),
        scanner = settings().getScannerSettings(),
        resume =
          db.resumeDao().get()?.let {
            BackupResume(it.fileName, it.text, it.pages, it.words, it.flags, it.uploadedAt)
          },
        server = settings().getServerSettings(),
        ai = db.settingsDao().get()?.toAiSettings(),
        prompts = PromptBook(db.aiPromptsDao()).edits(),
        detailLayout = settings().getDetailLayout(),
      )
      .only(parts)
  }

  private suspend fun full(formAnswers: Boolean = true): ByteArray {
    val notes = db.notesDao().listAll()
    val files = mutableMapOf<String, ByteArray>()
    val backupNotes = notes.map { note ->
      BackupNote(
        id = note.id,
        jobId = note.jobId,
        text = note.text,
        files =
          note.files.mapNotNull { stored ->
            // Old app's absolute paths are not in the notes folder: left out.
            val file = noteFiles.resolve(stored) ?: return@mapNotNull null
            stored.also { files[it] = withContext(Dispatchers.IO) { file.readBytes() } }
          },
        createdAt = note.createdAt,
      )
    }
    val data =
      BackupData(
        sites = db.sitesDao().listAll().map { BackupSite(it.id, it.name) },
        links = db.linksDao().listAll().reversed().map { it.toBackup() },
        jobs = db.jobsDao().listAll().map { it.toBackup() },
        notes = backupNotes,
        atsReviews = db.atsReviewsDao().listAll().map { it.toBackup() },
        companyRatings = db.insightsDao().allRatings().map { it.toBackup() },
        companySalaries = db.insightsDao().allSalaries().map { it.toBackup() },
        aiUsage = db.aiUsageDao().listAll().map { it.toBackup() },
        formAnswers =
          if (formAnswers) db.formAnswersDao().listAll().map { it.toBackup() } else emptyList(),
      )
    val manifest =
      BackupManifest(
        appVersion = appVersion,
        exportedAt = clock(),
        searches = data.links.size,
        jobs = data.jobs.size,
        notes = data.notes.size,
        atsReviews = data.atsReviews.size,
        companies = data.companyRatings.size,
        files = files.size,
      )
    val settings = settingsBackup(SettingsPart.entries.toSet())
    return withContext(Dispatchers.Default) {
      BackupFiles.encodeFull(FullBackup(manifest, settings, data, files))
    }
  }

  // Import

  /** Adds the searches whose URL is not saved yet; boards unknown here become custom boards. */
  private suspend fun addSearches(searches: List<BackupSearch>): Int {
    val sites = db.sitesDao().listAll().associate { it.name to it.id }
    val urls = db.linksDao().listAll().map { it.url }.toMutableSet()
    var added = 0
    val now = clock()
    for (search in searches) {
      if (search.url in urls) continue
      val siteId = sites[search.site] ?: customBoards.siteFor(search.url, search.site).id
      urls += search.url
      db
        .linksDao()
        .insert(
          LinkEntity(
            url = search.url,
            title = search.title,
            siteId = siteId,
            createdAt = now,
            lastScrapedAt = now,
          )
        )
      added++
    }
    return added
  }

  /** Applies every part [backup] holds except searches; returns the parts applied. */
  private suspend fun applySettings(backup: SettingsBackup): Set<SettingsPart> = buildSet {
    backup.searches?.let { add(SettingsPart.SEARCHES) }
    backup.matching?.let {
      matching.updateConfig(it)
      add(SettingsPart.MATCHING)
    }
    backup.scanner?.let {
      settings().updateScannerSettings(it)
      add(SettingsPart.SCANNER)
    }
    backup.resume?.let { resume ->
      val restore = restoreResume ?: return@let
      restore(
        ResumeEntity(
          fileName = resume.fileName,
          text = resume.text,
          pages = resume.pages,
          words = resume.words,
          flags = resume.flags,
          hash = "",
          uploadedAt = resume.uploadedAt,
        )
      )
      add(SettingsPart.RESUME)
    }
    backup.server?.let {
      settings().updateServerSettings(it)
      add(SettingsPart.SERVER)
    }
    backup.ai?.let { ai ->
      val dao = db.settingsDao()
      // A backup can't bring a reserved or empty model; the rest of it still applies.
      val row = runCatching { (dao.get() ?: SettingsDefaults.newRow()).withAi(ai) }.getOrNull()
      if (row != null) {
        dao.upsert(row)
        add(SettingsPart.AI)
      }
    }
    backup.prompts?.let { if (restorePrompts(it)) add(SettingsPart.PROMPTS) }
    backup.detailLayout?.let {
      settings().updateDetailLayout(it)
      add(SettingsPart.DETAIL_LAYOUT)
    }
  }

  private suspend fun restorePrompts(edits: Map<PromptId, String>): Boolean = runCatching {
    PromptBook(db.aiPromptsDao()).restore(edits)
  }
    .isSuccess

  private suspend fun merge(backup: FullBackup): ImportSummary {
    val data = backup.data
    val siteIds = siteMap(data)
    val linkUrls = db.linksDao().listAll().associate { it.url to it.id }
    var searchesAdded = 0
    var jobsAdded = 0
    var notesAdded = 0
    val addedNotes = mutableListOf<Pair<Long, BackupNote>>()
    db.withWriteTransaction {
      val links =
        data.links
          .mapNotNull { link ->
            val site = siteIds[link.siteId] ?: return@mapNotNull null
            link.id to
              (linkUrls[link.url]
                ?: db.linksDao().insert(link.toEntity(0, site)).also { searchesAdded++ })
          }
          .toMap()
      val jobs = mutableMapOf<Long, Long>()
      for (job in data.jobs) {
        val site = siteIds[job.siteId] ?: continue
        // Ignored when a job with the same external id is already there.
        val id = db.jobsDao().insert(job.toEntity(0, site, job.linkId?.let(links::get)))
        if (id > 0) {
          jobs[job.id] = id
          jobsAdded++
        }
      }
      for (note in data.notes) {
        val jobId = jobs[note.jobId] ?: continue
        val id =
          db
            .notesDao()
            .insert(NoteEntity(jobId = jobId, text = note.text, createdAt = note.createdAt))
        addedNotes += id to note
        notesAdded++
      }
      db
        .atsReviewsDao()
        .insertAll(data.atsReviews.mapNotNull { r -> jobs[r.jobId]?.let { r.toEntity(it) } })
      mergeInsights(data)
      mergeFormAnswers(data, jobs)
    }
    restoreFiles(addedNotes, backup.files)
    return ImportSummary(
      searchesAdded = searchesAdded,
      jobsAdded = jobsAdded,
      notesAdded = notesAdded,
    )
  }

  /** Company insights: a company or salary missing here, or read later in the backup. */
  private suspend fun mergeInsights(data: BackupData) {
    val dao = db.insightsDao()
    for (r in data.companyRatings) {
      val here = dao.ratings(r.companyKey)
      if (here == null || here.fetchedAt < r.fetchedAt) dao.upsertRatings(r.toEntity())
    }
    for (s in data.companySalaries) {
      val here = dao.salary(s.companyKey, s.titleKey)
      if (here == null || here.fetchedAt < s.fetchedAt) dao.upsertSalary(s.toEntity())
    }
  }

  /** Answers not here yet (same question, site, page and value), linked to the jobs added. */
  private suspend fun mergeFormAnswers(data: BackupData, jobs: Map<Long, Long>) {
    if (data.formAnswers.isEmpty()) return
    val dao = db.formAnswersDao()
    val here =
      dao.listAll().map { listOf(it.questionKey, it.site, it.url, it.value) }.toMutableSet()
    val added =
      data.formAnswers
        .filter { here.add(listOf(it.questionKey, it.site, it.url, it.value)) }
        .map { it.toEntity(it.jobId?.let(jobs::get)) }
    dao.insertAll(added)
  }

  private suspend fun replace(backup: FullBackup): ImportSummary {
    val savedBefore = write(BEFORE_PREFIX, full())
    val data = backup.data
    val siteIds = siteMap(data)
    // The resume first: a different one would drop the reviews put back below.
    val applied =
      applySettings(
        backup.settings.only(
          setOf(
            SettingsPart.MATCHING,
            SettingsPart.SCANNER,
            SettingsPart.RESUME,
            SettingsPart.AI,
            SettingsPart.PROMPTS,
            SettingsPart.DETAIL_LAYOUT,
          )
        )
      )
    val notes = mutableListOf<Pair<Long, BackupNote>>()
    var jobs = 0
    db.withWriteTransaction {
      db.notesDao().deleteAll()
      db.atsReviewsDao().deleteAll()
      db.jobsDao().deleteAll()
      db.linksDao().deleteAll()
      db.insightsDao().deleteRatings()
      db.insightsDao().deleteSalaries()
      db.aiUsageDao().deleteAll()
      val links = data.links.mapNotNull { l -> siteIds[l.siteId]?.let { l.toEntity(l.id, it) } }
      db.linksDao().insertAll(links)
      val linkIds = links.map { it.id }.toSet()
      val kept =
        data.jobs.mapNotNull { j ->
          siteIds[j.siteId]?.let { j.toEntity(j.id, it, j.linkId?.takeIf { l -> l in linkIds }) }
        }
      db.jobsDao().insertAll(kept)
      jobs = kept.size
      val jobIds = kept.map { it.id }.toSet()
      for (note in data.notes) {
        if (note.jobId !in jobIds) continue
        db
          .notesDao()
          .insert(
            NoteEntity(
              id = note.id,
              jobId = note.jobId,
              text = note.text,
              createdAt = note.createdAt,
            )
          )
        notes += note.id to note
      }
      db
        .atsReviewsDao()
        .insertAll(data.atsReviews.filter { it.jobId in jobIds }.map { it.toEntity(it.jobId) })
      data.companyRatings.forEach { db.insightsDao().upsertRatings(it.toEntity()) }
      data.companySalaries.forEach { db.insightsDao().upsertSalary(it.toEntity()) }
      db.aiUsageDao().insertAll(data.aiUsage.map { it.toEntity() })
      // A backup without answers (the phone's) keeps the ones here, unlinked from replaced jobs.
      if (data.formAnswers.isNotEmpty()) {
        db.formAnswersDao().deleteAll()
        db
          .formAnswersDao()
          .insertAll(data.formAnswers.map { it.toEntity(it.jobId?.takeIf { j -> j in jobIds }) })
      }
    }
    restoreFiles(notes, backup.files)
    return ImportSummary(
      searchesAdded = data.links.size,
      jobsAdded = jobs,
      notesAdded = notes.size,
      settings = applied.toList(),
      replaced = true,
      savedBefore = savedBefore.toString(),
    )
  }

  /** Copies each added note's attachments into the notes folder and points the note at them. */
  private suspend fun restoreFiles(
    notes: List<Pair<Long, BackupNote>>,
    files: Map<String, ByteArray>,
  ) {
    for ((id, note) in notes) {
      val stored =
        note.files.mapNotNull { path ->
          val bytes = files[path] ?: return@mapNotNull null
          try {
            withContext(Dispatchers.IO) {
              noteFiles.store(id, path.substringAfterLast('/'), bytes.inputStream())
            }
          } catch (e: ApiException) {
            log.warn("A note attachment was not restored: {}", e.message)
            null
          }
        }
      if (stored.isNotEmpty()) {
        db.notesDao().get(id)?.let { db.notesDao().update(it.copy(files = stored)) }
      }
    }
  }

  /**
   * The backup's site ids to this database's, by board name. A board unknown here is a custom board
   * from the other computer: it is made again from one of its searches' addresses.
   */
  private suspend fun siteMap(data: BackupData): Map<Long, Long> {
    val here = db.sitesDao().listAll().associate { it.name to it.id }
    return data.sites
      .mapNotNull { s ->
        val id =
          here[s.name]
            ?: data.links
              .firstOrNull { it.siteId == s.id }
              ?.let { customBoards.siteFor(it.url, s.name).id }
        id?.let { s.id to it }
      }
      .toMap()
  }

  private suspend fun write(prefix: String, bytes: ByteArray): Path =
    withContext(Dispatchers.IO) {
      backupDir.createDirectories()
      val stamp = LocalDateTime.now().format(STAMP)
      var file = backupDir.resolve("$prefix$stamp.zip")
      var n = 2
      while (java.nio.file.Files.exists(file)) file = backupDir.resolve("$prefix$stamp-${n++}.zip")
      file.also { it.writeBytes(bytes) }
    }

  companion object {
    private val log = LoggerFactory.getLogger("jobfinder.backup")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    const val AUTO_PREFIX = "jobfinder-auto-"
    private const val OLD_AUTO_PREFIX = "first2apply-auto-"
    const val BEFORE_PREFIX = "jobfinder-before-restore-"
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val AUTO_KEEP = 7

    /** A partial backup (the phone's copy) can be added, never replace everything. */
    fun checkReplace(manifest: BackupManifest) {
      if (manifest.partial) {
        throw BackupFormatException(
          "This backup is only the phone's copy. Add it instead of replacing everything."
        )
      }
    }

    /** A bad backup file as the API's bad request. */
    fun badFile(e: BackupFormatException): Nothing =
      throw ApiException(
        ApiErrorCode.BAD_REQUEST,
        e.message ?: "This file is not a JobFinder backup.",
      )
  }
}

private fun LinkEntity.toBackup() =
  BackupLink(id, url, title, siteId, createdAt, scrapeFailureCount, lastScrapedAt)

private fun BackupLink.toEntity(id: Long, siteId: Long) =
  LinkEntity(id, url, title, siteId, createdAt, scrapeFailureCount, lastScrapedAt)

private fun JobEntity.toBackup() =
  BackupJob(
    id,
    externalId,
    externalUrl,
    siteId,
    linkId,
    title,
    companyName,
    companyLogo,
    location,
    salary,
    jobType,
    tags,
    description,
    status,
    labels,
    excludeReason,
    createdAt,
    updatedAt,
  )

private fun BackupJob.toEntity(id: Long, siteId: Long, linkId: Long?) =
  JobEntity(
    id,
    externalId,
    externalUrl,
    siteId,
    linkId,
    title,
    companyName,
    companyLogo,
    location,
    salary,
    jobType,
    tags,
    description,
    status,
    labels,
    excludeReason,
    createdAt,
    updatedAt,
  )

private fun AtsReviewEntity.toBackup() =
  BackupAtsReview(jobId, resumeHash, overall, atsMatch, shortlisted, failed, review, createdAt)

private fun BackupAtsReview.toEntity(jobId: Long) =
  AtsReviewEntity(jobId, resumeHash, overall, atsMatch, shortlisted, failed, review, createdAt)

private fun CompanyRatingsEntity.toBackup() =
  BackupCompanyRatings(companyKey, ratings, ambitionBoxSlug, failed, fetchedAt)

private fun BackupCompanyRatings.toEntity() =
  CompanyRatingsEntity(companyKey, ratings, ambitionBoxSlug, failed, fetchedAt)

private fun CompanySalaryEntity.toBackup() =
  BackupCompanySalary(companyKey, titleKey, salary, fetchedAt)

private fun BackupCompanySalary.toEntity() =
  CompanySalaryEntity(companyKey, titleKey, salary, fetchedAt)

private fun AiUsageDailyEntity.toBackup() =
  BackupAiUsage(usageDate, inputTokens, outputTokens, updatedAt)

private fun BackupAiUsage.toEntity() =
  AiUsageDailyEntity(date, inputTokens, outputTokens, updatedAt)

private fun FormAnswerEntity.toBackup() =
  BackupFormAnswer(
    jobId,
    site,
    url,
    label,
    name,
    elementId,
    placeholder,
    ariaLabel,
    inputType,
    questionKey,
    value,
    createdAt,
    updatedAt,
  )

private fun BackupFormAnswer.toEntity(jobId: Long?) =
  FormAnswerEntity(
    jobId = jobId,
    site = site,
    url = url,
    label = label,
    name = name,
    elementId = elementId,
    placeholder = placeholder,
    ariaLabel = ariaLabel,
    inputType = inputType,
    questionKey = questionKey,
    value = value,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )
