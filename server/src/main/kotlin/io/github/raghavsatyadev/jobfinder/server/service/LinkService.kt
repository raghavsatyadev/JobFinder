package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.HtmlDumpEntity
import io.github.raghavsatyadev.jobfinder.server.db.LinkEntity
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.parsing.JobPageParsers
import io.github.raghavsatyadev.jobfinder.server.parsing.JobSiteException
import io.github.raghavsatyadev.jobfinder.server.parsing.JobSites
import io.github.raghavsatyadev.jobfinder.server.parsing.PageRuntimeData
import io.github.raghavsatyadev.jobfinder.server.scan.HtmlFetcher
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import kotlin.coroutines.cancellation.CancellationException
import org.slf4j.LoggerFactory

/** A search page already loaded by the caller (the desktop add-link browser). */
data class LoadedPage(val html: String, val runtimeData: PageRuntimeData? = null)

/**
 * The old `create-link` edge function: clean the URL, save the link and store the jobs on its page.
 * Without a [LoadedPage] the page is loaded with [fetcher] (in a hidden browser on the desktop).
 *
 * Unlike the old function there are no per-user link limits (everything is local now), and a page
 * without a recognisable job list is refused unless `force` is set; the old function had that check
 * switched off while its LinkedIn parser was broken.
 */
class LinkService(
  private val db: ServerDatabase,
  private val parsers: JobPageParsers,
  private val ingest: JobIngestService,
  private val fetcher: HtmlFetcher?,
  private val clock: () -> Long = System::currentTimeMillis,
  private val customBoards: CustomBoards = CustomBoards(db, clock),
) {
  private val log = LoggerFactory.getLogger(LinkService::class.java)

  suspend fun createLink(
    title: String,
    url: String,
    force: Boolean = false,
    page: LoadedPage? = null,
    /** The name of a board the app does not know; `null` takes it from the address. */
    siteName: String? = null,
  ): CreateLinkResponse {
    val cleanTitle = title.trim()
    if (cleanTitle.isEmpty())
      throw ApiException(ApiErrorCode.BAD_REQUEST, "Give the search a title.")
    // The scan browser opens this URL, so never a local file or another scheme.
    val scheme = url.trim().substringBefore("://", missingDelimiterValue = "").lowercase()
    if (scheme != "http" && scheme != "https") {
      throw ApiException(ApiErrorCode.BAD_REQUEST, "Use an http or https address.")
    }
    val sites = db.sitesDao().listAll().map { it.toModel() }
    val cleaned =
      try {
        JobSites.cleanJobUrl(sites, url.trim())
      } catch (e: JobSiteException) {
        throw ApiException(ApiErrorCode.BAD_REQUEST, e.message ?: "This URL is not supported.")
      }
    // A board without a parser gets its own site, so its jobs can be filtered by board.
    val clean =
      if (cleaned.site.provider != SiteProvider.CUSTOM) cleaned
      else cleaned.copy(site = customBoards.siteFor(cleaned.cleanUrl, siteName).toModel())
    if (clean.site.deprecated) {
      throw ApiException(
        ApiErrorCode.BAD_REQUEST,
        "${clean.site.name} is no longer supported.",
      )
    }
    val now = clock()
    val linkId =
      db
        .linksDao()
        .insert(
          LinkEntity(
            url = clean.cleanUrl,
            title = cleanTitle,
            siteId = clean.site.id,
            createdAt = now,
            lastScrapedAt = now,
          )
        )
    log.info("Created link {} for {}", linkId, clean.site.name)
    try {
      val loaded = page ?: fetch(clean.cleanUrl)
      if (loaded == null) {
        return CreateLinkResponse(link(linkId), emptyList(), parseFailed = false)
      }
      val result = parsers.parseList(clean.site, loaded.html, clean.cleanUrl, loaded.runtimeData)
      if (result.parseFailed && !force) {
        db
          .htmlDumpsDao()
          .insert(
            HtmlDumpEntity(
              url = clean.cleanUrl,
              html = loaded.html,
              webpageRuntimeData = loaded.runtimeData?.linkedInComoRehydration,
              createdAt = clock(),
            )
          )
        throw ApiException(
          ApiErrorCode.CONFLICT,
          "No jobs were found on this ${clean.site.name} page. This usually means it shows a " +
            "single job instead of a list of jobs. If it is a list, the site layout may have " +
            "changed; you can save it anyway.",
        )
      }
      val newJobIds = ingest.insertJobs(result.jobs, linkId, now)
      log.info("Link {}: {} jobs on the page, {} new", linkId, result.jobs.size, newJobIds.size)
      return CreateLinkResponse(link(linkId), newJobIds, result.parseFailed)
    } catch (e: Throwable) {
      // Like the old function: a link whose first scan failed is removed again.
      db.linksDao().delete(linkId)
      throw e
    }
  }

  /** Links whose last [ServerEvent.BROKEN_LINK_THRESHOLD] or more scans failed. */
  suspend fun brokenLinks(): List<Link> =
    db.linksDao().listFailing(ServerEvent.BROKEN_LINK_THRESHOLD).map { it.toModel() }

  private suspend fun fetch(url: String): LoadedPage? {
    val fetcher = fetcher ?: return null
    val result =
      try {
        fetcher.fetchHtml(url)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.error("Could not load {}", url, e)
        throw ApiException(ApiErrorCode.UNAVAILABLE, "The page could not be loaded. Try again.")
      }
    return LoadedPage(result.html)
  }

  private suspend fun link(id: Long): Link = checkNotNull(db.linksDao().get(id)).toModel()
}
