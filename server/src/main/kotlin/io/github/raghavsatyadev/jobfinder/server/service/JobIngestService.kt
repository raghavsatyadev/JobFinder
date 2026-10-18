package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.HtmlDumpEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.JsonColumnConverters
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.parsing.JobPageParsers
import io.github.raghavsatyadev.jobfinder.server.parsing.PageRuntimeData
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.custom.CustomScanPolicy
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import org.slf4j.LoggerFactory

/** Result of [JobIngestService.ingestList]. */
data class ListIngestResult(
  /** Jobs inserted by this scan, in `processing` until their description is read. */
  val newJobIds: List<Long>,
  val parseFailed: Boolean,
  /** The page was not parsed (deprecated board, or a custom board scanned in the last 24 h). */
  val skipped: Boolean = false,
)

/** Result of [JobIngestService.ingestDescription]. */
data class DescriptionIngestResult(val job: Job, val parseFailed: Boolean)

/**
 * Stores what a scan read from a page: the old `scan-urls` (job lists) and `scan-job-description`
 * edge functions, minus subscriptions and the per-user scoping.
 */
class JobIngestService(
  private val db: ServerDatabase,
  private val parsers: JobPageParsers,
  private val matching: AdvancedMatchingFilter,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val log = LoggerFactory.getLogger(JobIngestService::class.java)

  /**
   * Parses the search page [html] of link [linkId] and inserts the jobs that are not stored yet.
   * Updates the link's last scrape time. A failed parse counts against the link once, on the
   * [isLastRetry] (the old app counted every read of the page and again in the scanner), and keeps
   * the page in `html_dumps` unless the link already failed [ERROR_MODE_FAILURES] times.
   */
  suspend fun ingestList(
    linkId: Long,
    html: String,
    runtimeData: PageRuntimeData? = null,
    isLastRetry: Boolean = true,
  ): ListIngestResult {
    val link = db.linksDao().get(linkId)
    if (link == null) {
      log.error("Link not found: {}", linkId)
      return ListIngestResult(emptyList(), parseFailed = false, skipped = true)
    }
    val site = db.sitesDao().get(link.siteId)?.toModel() ?: notFound("Site ${link.siteId}")
    val now = clock()
    if (
      site.provider == SiteProvider.CUSTOM &&
        CustomScanPolicy.shouldSkip(link.lastScrapedAt, link.createdAt, now)
    ) {
      log.info(
        "Skip custom board scan for link {}, last scraped at {}",
        link.id,
        link.lastScrapedAt,
      )
      return ListIngestResult(emptyList(), parseFailed = false, skipped = true)
    }
    val result = parsers.parseList(site, html, link.url, runtimeData)
    if (result.skipped) return ListIngestResult(emptyList(), parseFailed = false, skipped = true)
    log.info("[{}] found {} jobs on link {}", site.provider.wire, result.jobs.size, link.id)

    if (result.parseFailed) {
      if (!isLastRetry) return ListIngestResult(emptyList(), parseFailed = true)
      db.linksDao().incrementScrapeFailures(link.id)
      if (link.scrapeFailureCount < ERROR_MODE_FAILURES) {
        log.error("[{}] no jobs found on link {}, the parser may be broken", site.provider, link.id)
        saveDump(link.url, html, runtimeData)
      }
    } else {
      db.linksDao().markScraped(link.id, now)
    }
    val inserted = insertJobs(result.jobs, link.id, now)
    return ListIngestResult(inserted, result.parseFailed)
  }

  /**
   * Inserts [jobs] in `processing` unless a job with the same external id exists. Returns the ids
   * of the inserted jobs.
   */
  suspend fun insertJobs(jobs: List<ParsedJob>, linkId: Long?, now: Long = clock()): List<Long> =
    jobs.mapNotNull { job ->
      db
        .jobsDao()
        .insert(
          JobEntity(
            externalId = job.externalId,
            externalUrl = job.externalUrl,
            siteId = job.siteId,
            linkId = linkId,
            title = job.title,
            companyName = job.companyName,
            companyLogo = job.companyLogo,
            location = job.location,
            salary = job.salary,
            jobType = job.workMode?.wire,
            tags = job.tags,
            description = job.description,
            status = JobStatus.PROCESSING.wire,
            createdAt = now,
            updatedAt = now,
          )
        )
        .takeIf { it > 0 }
    }

  /**
   * Reads the description from the job page [html] of job [jobId], then runs advanced matching. The
   * job ends in `new` or `excluded_by_advanced_matching`, unless the user moved it meanwhile. When
   * no description is found and this is not the [isLastRetry], nothing is stored: the job stays in
   * `processing` and the caller reads the page again.
   */
  suspend fun ingestDescription(
    jobId: Long,
    html: String,
    isLastRetry: Boolean = true,
  ): DescriptionIngestResult {
    val job = db.jobsDao().get(jobId)?.toModel() ?: notFound("Job $jobId")
    val site = db.sitesDao().get(job.siteId)?.toModel() ?: notFound("Site ${job.siteId}")
    val updates = parsers.parseDescription(site, html)
    var updated =
      job.copy(
        description = updates.description ?: job.description,
        salary = if (job.salary.isNullOrEmpty()) updates.salary else job.salary,
        tags = (job.tags + updates.tags.orEmpty()).distinct(),
      )
    if (updated.description == null && !isLastRetry) {
      return DescriptionIngestResult(job, parseFailed = true)
    }
    if (updates.description == null) {
      log.error(
        "[{}] no description read from job {}, the parser may be broken",
        site.provider,
        jobId,
      )
      saveDump(job.externalUrl, html, null)
    }
    val decision = matching.evaluate(updated)
    updated = updated.copy(status = decision.status, excludeReason = decision.excludeReason)
    db
      .jobsDao()
      .updateAfterDescriptionScan(
        id = jobId,
        description = updated.description,
        salary = updated.salary,
        tagsJson = JsonColumnConverters.encodeStringList(updated.tags),
        status = updated.status.wire,
        excludeReason = updated.excludeReason,
        matchedWith = decision.checkedWith,
        updatedAt = clock(),
      )
    log.info("[{}] {} {}", site.provider.wire, updated.status.wire, job.title)
    val stored = db.jobsDao().get(jobId)?.toModel() ?: updated
    return DescriptionIngestResult(stored, parseFailed = updated.description == null)
  }

  private suspend fun saveDump(url: String, html: String, runtimeData: PageRuntimeData?) {
    db
      .htmlDumpsDao()
      .insert(
        HtmlDumpEntity(
          url = url,
          html = html,
          webpageRuntimeData = runtimeData?.linkedInComoRehydration,
          createdAt = clock(),
        )
      )
  }

  companion object {
    /** The old `isLinkInErrorMode`: past this many failures no more dumps are saved. */
    const val ERROR_MODE_FAILURES = 5
  }
}
