package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/** USAJobs search results (old `parseUSAJobsJobs`). The company is the agency. */
object UsaJobsListParser : JobListParser {
  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val jobsList = document.qs("#search-results") ?: return JobListParseResult.NOT_FOUND
    val jobElements = jobsList.qsa(".page-section")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val titleElement = el.qs("h2 a[data-document-id]")
    val externalId = titleElement.trimmedAttr("data-document-id")?.ifEmpty { null } ?: return null
    val path = titleElement.trimmedAttr("href")?.ifEmpty { null } ?: return null
    val externalUrl = "https://www.usajobs.gov$path"
    val title = titleElement.trimmedText()?.ifEmpty { null } ?: return null

    val infoColumn = el.qs(".grid > div:first-child")
    val companyName =
      infoColumn?.qs("div:first-child strong").trimmedText()?.ifEmpty { null } ?: return null
    val location = infoColumn?.qs("div:nth-child(2)").trimmedText()
    val salary = el.qs(".grid > div:nth-child(2) span.badge-secondary").trimmedText()

    val workMode =
      if (location?.lowercase()?.contains("remote") == true) WorkMode.REMOTE else WorkMode.ONSITE

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      location = location,
      salary = salary,
      workMode = workMode,
    )
  }
}
