@file:OptIn(ExperimentalAtomicApi::class)

package io.github.raghavsatyadev.jobfinder.cache

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.JobQueryParams
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.remote.DesktopUnreachableException
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

/**
 * Keeps the phone's copy ([PhoneCache]) in step with the computer. Each time the event stream
 * opens, and whenever the computer's jobs change (a scan, an import): sends the changes made
 * offline and the backups imported on the phone, then copies every job in the four tabs, the sites
 * and the searches, then the descriptions of new and applied jobs the phone has not read yet.
 * [jobs], [links] and [sites] talk to the computer directly.
 */
class PhoneSync(
  private val jobs: JobsRepository,
  private val links: LinksRepository,
  private val sites: SitesRepository,
  private val cache: PhoneCache,
  private val connected: StateFlow<Boolean>,
  private val events: Flow<ServerEvent>,
  /** The saved computers' keys: the copy of one the user forgets is dropped. */
  private val computers: Flow<Set<String>>,
  private val scope: CoroutineScope,
  private val log: (String, Throwable?) -> Unit = { _, _ -> },
  /** Sends the backups imported on the phone; `null` where there are none (tests). */
  private val backups: BackupRepository? = null,
  private val importFiles: ImportFiles? = null,
  /** The settings the screens show, copied too; `null` where there are none (tests). */
  private val settings: SettingsRepository? = null,
  private val matching: MatchingRepository? = null,
  private val ats: AtsRepository? = null,
  private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
  /** When the last sync started, for [requestSync]. */
  private val lastSync = AtomicLong(0)

  private val lock = Mutex()
  private val _synced = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 1)

  /** A [ServerEvent.NewJobs] with no ids after each sync that changed the copy. */
  val synced: Flow<ServerEvent> = _synced.asSharedFlow()

  fun start() {
    scope.launch {
      var before: Set<String>? = null
      computers.collect { now ->
        before?.minus(now)?.forEach { cache.forget(it) }
        before = now
      }
    }
    scope.launch { connected.collectLatest { if (it) syncNow() } }
    scope.launch {
      // From the computer only (not [synced]): no ids means its jobs changed in bulk (an import).
      events.filterIsInstance<ServerEvent.NewJobs>().collect { syncNow() }
    }
  }

  /**
   * A sync in the background while the computer is in reach, unless one started in the last
   * [FRESH_MS]: screens showing the copy call it on each read.
   */
  fun requestSync() {
    if (!connected.value) return
    val now = clock()
    val last = lastSync.load()
    if (now - last < FRESH_MS || !lastSync.compareAndSet(last, now)) return
    scope.launch { syncNow() }
  }

  /** One sync; returns `false` when the computer could not be reached or answered with an error. */
  suspend fun syncNow(): Boolean = lock.withLock {
    val computer = cache.computer() ?: return@withLock false
    lastSync.store(clock())
    try {
      cache.busyWith { copy(computer) }
      readDescriptions(computer)
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: DesktopUnreachableException) {
      false
    } catch (e: ApiFailure) {
      log("Phone sync failed", e)
      false
    }
  }

  private suspend fun copy(computer: String) {
    sendPending(computer)
    sendImports(computer)
    cache.keep(PhoneCache.SITES, ListSerializer(Site.serializer()), sites.listSites())
    cache.keep(PhoneCache.LINKS, ListSerializer(Link.serializer()), links.listLinks())
    settings?.let {
      cache.keep(PhoneCache.SCANNER, ScannerSettings.serializer(), it.getScannerSettings())
    }
    matching?.let {
      cache.keep(PhoneCache.MATCHING, AdvancedMatchingConfig.serializer(), it.getConfig())
    }
    ats?.let { cache.keep(PhoneCache.RESUME, ResumeInfo.serializer().nullable, it.getResume()) }
    for (status in TABS) cache.replaceStatus(computer, status, all(status))
    cache.markSynced(computer)
    _synced.tryEmit(ServerEvent.NewJobs(emptyList()))
  }

  /** Sends the changes made offline, oldest first; `true` when any was sent. */
  private suspend fun sendPending(computer: String): Boolean {
    var sent = false
    for (change in cache.pending(computer)) {
      try {
        when (change.kind) {
          PendingChange.STATUS ->
            JobStatus.entries
              .firstOrNull { it.wire == change.value }
              ?.let { jobs.updateStatus(change.jobId, it) }
          PendingChange.LABELS ->
            jobs.updateLabels(
              change.jobId,
              ApiJson.decodeFromString(ListSerializer(JobLabel.serializer()), change.value),
            )
        }
      } catch (e: ApiFailure) {
        // The job is gone on the computer: nothing left to change.
        if (e is DesktopUnreachableException || e.code != ApiErrorCode.NOT_FOUND) throw e
      }
      cache.done(change)
      sent = true
    }
    return sent
  }

  /** Merges each backup imported on the phone into the computer, oldest first. */
  private suspend fun sendImports(computer: String) {
    val backups = backups ?: return
    val files = importFiles ?: return
    for (pending in cache.imports(computer)) {
      val bytes = files.read(pending.file)
      if (bytes != null) {
        try {
          backups.importFull(bytes, ImportMode.MERGE)
        } catch (e: ApiFailure) {
          // A file the computer refuses would be refused again: dropped, the rest goes on.
          if (e is DesktopUnreachableException || e.code != ApiErrorCode.BAD_REQUEST) throw e
          log("An imported backup was refused by the computer", e)
        }
      }
      cache.importSent(pending)
      files.delete(pending.file)
    }
  }

  private suspend fun all(status: JobStatus): List<Job> {
    val all = mutableListOf<Job>()
    var after: String? = null
    do {
      val page = jobs.listJobs(status, JobFilter(), JobQueryParams.MAX_LIMIT, after)
      all += page.jobs
      after = page.nextPageToken
    } while (after != null)
    return all
  }

  private suspend fun readDescriptions(computer: String) {
    for (id in cache.withoutDescription(computer, READ_AHEAD, DESCRIPTIONS)) {
      if (!connected.value) return
      jobs.getJob(id)?.let { cache.storeJobs(computer, listOf(it)) }
    }
  }

  private companion object {
    val TABS =
      listOf(
        JobStatus.NEW,
        JobStatus.APPLIED,
        JobStatus.ARCHIVED,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
      )

    /** Descriptions read ahead for offline reading. */
    val READ_AHEAD = listOf(JobStatus.NEW, JobStatus.APPLIED)
    const val DESCRIPTIONS = 300

    /** A sync this recent is fresh enough for [requestSync]. */
    const val FRESH_MS = 60_000L
  }
}
