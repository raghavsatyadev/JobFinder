package io.github.raghavsatyadev.jobfinder.cache

import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.FullBackup
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.BackupData
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import io.github.raghavsatyadev.jobfinder.shared.model.BackupSearch
import io.github.raghavsatyadev.jobfinder.shared.model.BackupSite
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyRating
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.ImportSummary
import io.github.raghavsatyadev.jobfinder.shared.model.InsightKeys
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.SalaryRange
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.toBackup
import io.github.raghavsatyadev.jobfinder.shared.remote.DesktopUnreachableException
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

/** Backup files imported on the phone, kept in the app's own storage until they are sent. */
interface ImportFiles {
  /** Saves [bytes]; returns the name to read it back with. */
  suspend fun write(bytes: ByteArray): String

  suspend fun read(name: String): ByteArray?

  suspend fun delete(name: String)
}

/**
 * Backups on the phone. Settings need the computer; a full export without it saves the phone's own
 * copy, marked partial. A full backup merged on the phone goes into the phone's copy first, so its
 * jobs show at once even off the Wi-Fi, and is sent to the computer by [PhoneSync] (at once when it
 * is in reach). Replacing everything needs the computer.
 */
class OfflineBackupRepository(
  private val remote: BackupRepository,
  private val cache: PhoneCache,
  private val files: ImportFiles,
  private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
  /** One sync now; `false` when the computer could not be reached. */
  private val sync: suspend () -> Boolean,
) : BackupRepository by remote {
  override suspend fun importSettings(
    backup: SettingsBackup,
    parts: Set<SettingsPart>,
  ): ImportSummary = remote.importSettings(backup, parts).also { cache.dropSettings() }

  override suspend fun exportFull(): ByteArray =
    try {
      remote.exportFull()
    } catch (e: DesktopUnreachableException) {
      phoneCopy() ?: throw e
    }

  /** The phone's copy of the computer's searches and jobs, or `null` when it keeps none. */
  private suspend fun phoneCopy(): ByteArray? {
    val computer = cache.computer() ?: return null
    val jobs = cache.computerJobs(computer).takeIf { it.isNotEmpty() } ?: return null
    val sites = cache.kept("sites", ListSerializer(Site.serializer())).orEmpty()
    val links = cache.kept("links", ListSerializer(Link.serializer())).orEmpty()
    val siteNames = sites.associate { it.id to it.name }
    val data =
      BackupData(
        sites = sites.map { BackupSite(it.id, it.name) },
        links = links.map { it.toBackup() },
        jobs = jobs.map { it.toBackup() },
      )
    val now = clock()
    val settings =
      SettingsBackup(
        exportedAt = now,
        searches =
          links.mapNotNull { link ->
            siteNames[link.siteId]?.let { BackupSearch(link.title, link.url, it) }
          },
        matching = cache.kept("matching", AdvancedMatchingConfig.serializer()),
        scanner = cache.kept("scanner", ScannerSettings.serializer()),
      )
    val manifest =
      BackupManifest(
        exportedAt = now,
        searches = links.size,
        jobs = jobs.size,
        partial = true,
      )
    return withContext(Dispatchers.Default) {
      BackupFiles.encodeFull(FullBackup(manifest, settings, data, emptyMap()))
    }
  }

  override suspend fun importFull(bytes: ByteArray, mode: ImportMode): ImportSummary {
    val computer = cache.computer()
    if (mode == ImportMode.REPLACE || computer == null) {
      return remote.importFull(bytes, mode).also { cache.dropSettings() }
    }
    // Refused here when it is no backup, before anything is kept.
    val backup = withContext(Dispatchers.Default) { BackupFiles.decodeFull(bytes) }
    val siteIds = siteIds(backup.data.sites.associate { it.id to it.name })
    val reviews = backup.data.atsReviews.filterNot { it.failed }.associateBy { it.jobId }
    val jobs =
      backup.data.jobs.map { job ->
        val review = reviews[job.id]
        job
          .toJob(siteId = siteIds[job.siteId] ?: job.siteId, linkId = null)
          .copy(ats = review?.let { AtsSummary(it.overall, it.atsMatch, it.shortlisted) })
      }
    // Jobs the phone already has from the computer, before the import adds the rest.
    val known = cache.computerJobIds(computer)
    val name = files.write(bytes)
    val added = cache.keepImport(computer, name, jobs)
    // The full reviews, so the job detail shows them before the computer has the jobs.
    for (job in added) {
      val review = reviews[-job.id]?.review?.let(::readReview) ?: continue
      cache.keep(
        "ats:${job.id}",
        AtsStatus.serializer(),
        AtsStatus(AtsState.READY, review.copy(jobId = job.id)),
      )
    }
    keepInsights(backup.data, added)
    // Jobs the phone had: the backup's review and company details where none is kept, so their
    // detail shows them offline too.
    val had = jobs.mapNotNull { job ->
      known[job.externalId]?.let { id -> job.copy(id = id) to job.id }
    }
    for ((job, backupId) in had) {
      val review = reviews[backupId]?.review?.let(::readReview) ?: continue
      cache.keepIfMissing(
        "ats:${job.id}",
        AtsStatus.serializer(),
        AtsStatus(AtsState.READY, review.copy(jobId = job.id)),
      )
    }
    keepInsights(backup.data, had.map { it.first }, onlyIfMissing = true)
    keepSettings(backup, siteIds)
    val sent = cache.online() && sync() && cache.imports(computer).none { it.file == name }
    return ImportSummary(
      searchesAdded = 0,
      jobsAdded = added.size,
      notesAdded = 0,
      waitingForComputer = !sent,
    )
  }

  /** Company ratings and salaries from the backup, for the job detail of [jobs]. */
  private suspend fun keepInsights(
    data: BackupData,
    jobs: List<Job>,
    onlyIfMissing: Boolean = false,
  ) {
    val ratings = data.companyRatings.filterNot { it.failed }.associateBy { it.companyKey }
    val salaries = data.companySalaries.associateBy { it.companyKey to it.titleKey }
    for (job in jobs) {
      val key = InsightKeys.company(job.companyName)
      val rated = ratings[key] ?: continue
      val insights =
        try {
          CompanyInsights(
            company = job.companyName,
            ratings =
              ReviewJson.decodeFromString(
                ListSerializer(CompanyRating.serializer()),
                rated.ratings,
              ),
            salary =
              salaries[key to InsightKeys.title(job.title)]?.salary?.let {
                ReviewJson.decodeFromString(SalaryRange.serializer(), it)
              },
            fetchedAt = rated.fetchedAt,
          )
        } catch (_: SerializationException) {
          continue
        } catch (_: IllegalArgumentException) {
          continue
        }
      val status = InsightsStatus(InsightsState.READY, insights)
      if (onlyIfMissing) {
        cache.keepIfMissing("insights:${job.id}", InsightsStatus.serializer(), status)
      } else {
        cache.keep("insights:${job.id}", InsightsStatus.serializer(), status)
      }
    }
  }

  /**
   * The backup's searches and settings, where the phone has none yet (it never synced): the screens
   * show them until the computer's own arrive.
   */
  private suspend fun keepSettings(backup: FullBackup, siteIds: Map<Long, Long>) {
    val links =
      backup.data.links.map {
        Link(
          it.id,
          it.url,
          it.title,
          siteIds[it.siteId] ?: it.siteId,
          it.createdAt,
          it.scrapeFailureCount,
          it.lastScrapedAt,
        )
      }
    if (links.isNotEmpty()) {
      cache.keepIfMissing(PhoneCache.LINKS, ListSerializer(Link.serializer()), links)
    }
    backup.settings.scanner?.let {
      cache.keepIfMissing(PhoneCache.SCANNER, ScannerSettings.serializer(), it)
    }
    backup.settings.matching?.let {
      cache.keepIfMissing(PhoneCache.MATCHING, AdvancedMatchingConfig.serializer(), it)
    }
    backup.settings.resume?.let { resume ->
      val info =
        ResumeInfo(
          fileName = resume.fileName,
          uploadedAt = resume.uploadedAt,
          pages = resume.pages,
          words = resume.words,
          flags =
            resume.flags.mapNotNull { flag ->
              ResumeFlag.entries.firstOrNull { it.name == flag }
            },
        )
      cache.keepIfMissing(PhoneCache.RESUME, ResumeInfo.serializer().nullable, info)
    }
  }

  /** A stored review; `null` when it can't be read (an older or damaged one). */
  private fun readReview(json: String): AtsReview? =
    try {
      ReviewJson.decodeFromString(AtsReview.serializer(), json)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  /** The backup's site ids to the computer's, by board name, from the kept site list. */
  private suspend fun siteIds(names: Map<Long, String>): Map<Long, Long> {
    val here =
      cache.kept("sites", ListSerializer(Site.serializer()))?.associate { it.name to it.id }
        ?: return emptyMap()
    return names.mapNotNull { (id, name) -> here[name]?.let { id to it } }.toMap()
  }
}

/** Reviews in a backup may come from a newer computer: unknown fields are skipped. */
private val ReviewJson = Json { ignoreUnknownKeys = true }
