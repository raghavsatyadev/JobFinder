package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import org.jsoup.nodes.Element

/** Built In search results (old `parseBuiltinJobs`). No work mode: the old code left it unset. */
object BuiltInListParser : JobListParser {
  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val jobsList = document.qs("#jobs-list") ?: return JobListParseResult.NOT_FOUND
    val jobElements = jobsList.qsa("[data-id=\"job-card\"]")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val jobInfo = el.qs("h2 > a") ?: return null
    val href = jobInfo.trimmedAttr("href") ?: return null
    val externalUrl = "https://builtin.com$href"
    val externalId = el.trimmedAttr("id")?.ifEmpty { null } ?: return null
    val title = jobInfo.trimmedText()?.ifEmpty { null } ?: return null
    val companyName =
      el.qs("[data-id=\"company-title\"]").trimmedText()?.ifEmpty { null } ?: return null
    val companyLogo = el.qs("img[data-id=\"company-img\"]").trimmedAttr("src")

    val jobLocation =
      el.qs(
        "div:first-child > div:nth-child(2) > div:first-child > div:first-child > div:nth-child(2)"
      )
    val location = jobLocation?.qs("div:nth-child(2)").trimmedText()

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
    )
  }
}
