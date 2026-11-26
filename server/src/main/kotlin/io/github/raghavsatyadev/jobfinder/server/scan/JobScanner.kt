package io.github.raghavsatyadev.jobfinder.server.scan

import io.github.raghavsatyadev.jobfinder.server.db.LinkEntity
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.parsing.custom.CustomScanPolicy
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.DescriptionRead
import io.github.raghavsatyadev.jobfinder.server.service.JobIngestService
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory

/**
 * The scan pipeline (the old Electron `JobScanner`): load every link's search page in a hidden
 * browser and store the new jobs, then read the description of every job still in `processing` and
 * run advanced matching on it. Jobs stay in `processing` until that is done, so a scan that stops
 * halfway (the app quit, the PC slept) picks them up again with [resumeProcessing].
 *
 * One scan at a time: [startScanAll] and [scanLink] refuse to start while one runs.
 */
class JobScanner(
  private val db: ServerDatabase,
  private val ingest: JobIngestService,
  private val pool: BrowserPool,
  private val events: ServerEvents,
  private val pacing: Pacing = Pacing(),
  /** Reads a page in the user's own browser when the hidden one cannot. */
  fallback: PageFallback? = null,
  private val clock: () -> Long = System::currentTimeMillis,
  private val cooldown: RefreshCooldown = RefreshCooldown(),
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : Scanner, AutoCloseable {
  private val log = LoggerFactory.getLogger(JobScanner::class.java)
  private val running = AtomicBoolean(false)
  private val open = AtomicBoolean(true)
  private val scraper = PageScraper(pool, pacing, fallback) { open.get() }
  private val _state = MutableStateFlow(ScanState(refreshAllowedAt = cooldown.allowedAt))

  override val state: StateFlow<ScanState> = _state.asStateFlow()

  override fun startScanAll(): Boolean {
    if (!open.get() || !running.compareAndSet(false, true)) {
      log.info("Not starting a scan: one is running")
      return false
    }
    launchScanAll()
    return true
  }

  override fun startRefresh(): Boolean {
    if (!open.get()) return false
    if (!cooldown.allows(clock())) {
      log.info("Not starting a refresh: allowed again at {}", cooldown.allowedAt)
      return false
    }
    if (!running.compareAndSet(false, true)) {
      log.info("Not starting a refresh: a scan is running")
      return false
    }
    cooldown.start(clock())
    _state.update { it.copy(refreshAllowedAt = cooldown.allowedAt) }
    publishState()
    launchScanAll()
    return true
  }

  private fun launchScanAll() {
    scope.launch {
      try {
        exclusive { scan(db.linksDao().listAll()) }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.error("Scan failed", e)
      }
    }
  }

  override suspend fun scanLink(linkId: Long): List<Long> {
    val link =
      db.linksDao().get(linkId)
        ?: throw ApiException(ApiErrorCode.NOT_FOUND, "Search $linkId was not found.")
    if (!running.compareAndSet(false, true)) throw busy()
    return exclusive { scan(listOf(link)) }.inserted
  }

  override suspend fun scanJob(jobId: Long): Job {
    val job =
      db.jobsDao().get(jobId)?.toModel()
        ?: throw ApiException(ApiErrorCode.NOT_FOUND, "Job $jobId was not found.")
    val incognito = db.sitesDao().get(job.siteId)?.incognitoSupport == true
    return scraper.scrape(job.externalUrl, DESCRIPTION_SCROLLS, incognito) { page ->
      if (GonePosting.matches(page) && removeGone(job)) {
        throw ApiException(
          ApiErrorCode.NOT_FOUND,
          "The posting was removed, so the job was deleted.",
        )
      }
      val result = ingest.ingestDescription(job.id, page.html, isLastRetry = page.isLast)
      if (result.parseFailed && !page.isLast) throw ParseFailedException(job.externalUrl)
      result.job
    }
  }

  /**
   * Reads the descriptions of jobs left in `processing` by a scan that did not finish. Call once at
   * startup; does nothing while a scan runs. Returns `false` when it did not start.
   */
  fun resumeProcessing(): Boolean {
    if (!open.get() || !running.compareAndSet(false, true)) return false
    scope.launch {
      var started = false
      try {
        val jobs = processingJobs()
        if (jobs.isEmpty()) return@launch
        started = true
        exclusive {
          log.info("Resuming {} jobs left in processing", jobs.size)
          begin(links = 0, jobs = jobs.size)
          outcome(inserted = emptyList(), described = describe(jobs))
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.error("Resuming processing jobs failed", e)
      } finally {
        // exclusive() frees the flag itself once it ran.
        if (!started) running.set(false)
      }
    }
    return true
  }

  /** Stops retries and pacing waits, and cancels a running scan. */
  override fun close() {
    open.set(false)
    scope.cancel()
  }

  /** What a finished scan found. */
  private class Outcome(
    val inserted: List<Long>,
    val newJobIds: List<Long>,
    val brokenLinkIds: List<Long>,
  )

  /**
   * Runs [work] while holding the one-scan-at-a-time flag (the caller set it), then frees the flag
   * before announcing the end, so a client that sees `scanning = false` can start the next scan.
   */
  private suspend fun exclusive(work: suspend () -> Outcome): Outcome {
    var outcome: Outcome? = null
    try {
      return work().also { outcome = it }
    } finally {
      running.set(false)
      finish(outcome)
    }
  }

  /** Runs one scan of [links]. */
  private suspend fun scan(links: List<LinkEntity>): Outcome {
    val started = clock()
    log.info("Scanning {} links", links.size)
    begin(links = links.size, jobs = 0)
    val inserted = coroutineScope {
      pacing.order(links).map { link -> async { scanList(link) } }.awaitAll().flatten()
    }
    val pending = processingJobs()
    log.info("{} jobs need their description", pending.size)
    _state.update { it.copy(jobsTotal = pending.size) }
    publishState()
    val result = outcome(inserted, describe(pending))
    log.info(
      "Scan done in {} s, {} new jobs",
      (clock() - started) / MS_PER_SECOND,
      result.newJobIds.size,
    )
    return result
  }

  private suspend fun outcome(inserted: List<Long>, described: List<Job>) =
    Outcome(
      inserted = inserted,
      newJobIds = described.filter { it.status == JobStatus.NEW }.map { it.id },
      brokenLinkIds = db.linksDao().listFailing(ServerEvent.BROKEN_LINK_THRESHOLD).map { it.id },
    )

  /** Stores the jobs on [link]'s page. A failed scan counts against the link once. */
  private suspend fun scanList(link: LinkEntity): List<Long> {
    return try {
      if (skipsLoading(link)) return emptyList()
      scraper.scrape(link.url, LIST_SCROLLS) { page ->
        if (!open.get()) return@scrape emptyList()
        val result = ingest.ingestList(link.id, page.html, isLastRetry = page.isLast)
        if (result.parseFailed) throw ParseFailedException(link.url)
        delay(pacing.betweenLinks())
        result.newJobIds
      }
    } catch (e: ParseFailedException) {
      // The last read was already counted by the ingest service.
      log.warn("No jobs found on link {} ({})", link.id, link.title)
      emptyList()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error("Could not scan link {} ({}): {}", link.id, link.title, e.message)
      db.linksDao().incrementScrapeFailures(link.id)
      emptyList()
    } finally {
      _state.update { it.copy(linksDone = it.linksDone + 1) }
      publishState()
    }
  }

  /**
   * Reads the descriptions of [jobs]: each site's pages one at a time with the usual pause, the
   * sites side by side, incognito-capable sites in the incognito browser. Each job's AI check runs
   * on in the background (at most [CHECKS_AT_ONCE]) while the next page is read, so the checks
   * spread over all the AI models at once. Each job is saved as soon as it is checked, so a scan
   * that stops redoes at most the jobs it was on. A job the user moved meanwhile is skipped.
   * Returns the jobs as they are afterwards; a job whose page could not be loaded comes back
   * unchanged, and a job whose posting was gone is left out (it was deleted).
   */
  private suspend fun describe(jobs: List<Job>): List<Job> {
    if (jobs.isEmpty()) return emptyList()
    val incognitoSites =
      db.sitesDao().listAll().filter { it.incognitoSupport }.map { it.id }.toSet()
    val described = ConcurrentLinkedQueue<Job>()
    val checks = Semaphore(CHECKS_AT_ONCE)
    coroutineScope {
      for ((siteId, siteJobs) in jobs.groupBy { it.siteId }) {
        launch {
          for (job in siteJobs) {
            if (!open.get()) break
            if (db.jobsDao().get(job.id)?.status != JobStatus.PROCESSING.wire) {
              jobDone()
              continue
            }
            when (val page = readOne(job, siteId in incognitoSites)) {
              is Page.Done -> {
                page.job?.let(described::add)
                jobDone()
              }
              is Page.Read -> {
                checks.acquire()
                launch {
                  try {
                    described += check(job, page.read)
                  } finally {
                    checks.release()
                    jobDone()
                  }
                }
              }
            }
            delay(pacing.betweenJobs())
          }
        }
      }
    }
    return described.toList()
  }

  /** What reading one job's page gave. */
  private sealed interface Page {
    class Read(val read: DescriptionRead) : Page

    /** Nothing to check: the job unchanged, or `null` when the posting was gone and deleted. */
    class Done(val job: Job?) : Page
  }

  private suspend fun readOne(job: Job, incognito: Boolean): Page =
    try {
      scraper.scrape(job.externalUrl, DESCRIPTION_SCROLLS, incognito) { page ->
        if (!open.get()) return@scrape Page.Done(job)
        if (GonePosting.matches(page) && removeGone(job)) return@scrape Page.Done(null)
        val read = ingest.readDescription(job.id, page.html, isLastRetry = page.isLast)
        if (read.parseFailed) throw ParseFailedException(job.externalUrl)
        Page.Read(read)
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error(
        "Could not read the description of job {} ({}): {}",
        job.id,
        job.title,
        e.message,
        e,
      )
      Page.Done(job)
    }

  /** [job] after the AI checked its [read] page; unchanged when the check could not be saved. */
  private suspend fun check(job: Job, read: DescriptionRead): Job =
    try {
      ingest.finishDescription(read).job
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error("Could not check job {} ({}): {}", job.id, job.title, e.message, e)
      job
    }

  private fun jobDone() {
    _state.update { it.copy(jobsDone = it.jobsDone + 1) }
    publishState()
  }

  /**
   * Deletes [job] when its page says the posting is gone and the user has not touched it (new,
   * processing or filtered out, no label). Returns whether it was deleted.
   */
  private suspend fun removeGone(job: Job): Boolean {
    val current = db.jobsDao().get(job.id) ?: return true
    if (current.status !in UNTOUCHED_STATUSES || current.labels.isNotEmpty()) return false
    db.jobsDao().delete(job.id)
    log.info("Job {} ({}) was removed from {}; deleted it", job.id, job.title, job.externalUrl)
    return true
  }

  /**
   * Boards the ingest service would not parse anyway (deprecated ones, custom boards scanned in the
   * last 24 h) are not loaded at all.
   */
  private suspend fun skipsLoading(link: LinkEntity): Boolean {
    val site = db.sitesDao().get(link.siteId)?.toModel() ?: return false
    val skip =
      site.deprecated ||
        (site.provider == SiteProvider.CUSTOM &&
          CustomScanPolicy.shouldSkip(link.lastScrapedAt, link.createdAt, clock()))
    if (skip) log.info("Skipping link {} ({}) this time", link.id, link.title)
    return skip
  }

  private suspend fun processingJobs(): List<Job> =
    db.jobsDao().listProcessing().map { it.toModel() }

  private fun begin(links: Int, jobs: Int) {
    _state.update {
      ScanState(
        scanning = true,
        startedAt = clock(),
        linksTotal = links,
        jobsTotal = jobs,
        lastFinishedAt = it.lastFinishedAt,
        lastNewJobs = it.lastNewJobs,
        refreshAllowedAt = it.refreshAllowedAt,
      )
    }
    publishState()
  }

  /**
   * The old post-scan hook: announce new jobs, then the links that keep failing. [outcome] is
   * `null` when the scan failed or was cancelled; it then only ends the scanning state.
   */
  private fun finish(outcome: Outcome?) {
    _state.update {
      it.copy(
        scanning = false,
        lastFinishedAt = clock(),
        lastNewJobs = outcome?.newJobIds?.size ?: 0,
      )
    }
    publishState()
    if (outcome == null) return
    if (outcome.newJobIds.isNotEmpty()) events.emit(ServerEvent.NewJobs(outcome.newJobIds))
    events.emit(ServerEvent.BrokenLinks(outcome.brokenLinkIds))
  }

  private fun publishState() = events.emit(ServerEvent.ScanStateChanged(_state.value))

  private fun busy() =
    ApiException(ApiErrorCode.CONFLICT, "A scan is running. Try again when it finishes.")

  /** The page was read but held no jobs or description; the scraper reads it once more. */
  private class ParseFailedException(url: String) : RuntimeException("Nothing parsed on $url")

  companion object {
    /** Scrolls of a search page, to load more of an infinite list. */
    const val LIST_SCROLLS = 5

    /** Scrolls of a job page. */
    const val DESCRIPTION_SCROLLS = 1

    /** Jobs whose AI check runs while the next pages are read. */
    private const val CHECKS_AT_ONCE = 6

    private const val MS_PER_SECOND = 1_000

    /** Statuses of jobs the user has not acted on; only those are deleted when gone. */
    private val UNTOUCHED_STATUSES =
      setOf(
        JobStatus.NEW.wire,
        JobStatus.PROCESSING.wire,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING.wire,
      )
  }
}
