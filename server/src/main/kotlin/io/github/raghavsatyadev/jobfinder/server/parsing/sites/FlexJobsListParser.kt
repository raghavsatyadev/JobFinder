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

/**
 * FlexJobs search results (old `parseFlexjobsJobs`). The company is not on the list page, so it is
 * `N/A`; the list page does carry a short description.
 */
object FlexJobsListParser : JobListParser {
  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val jobsList = document.qs("#job-table-wrapper") ?: return JobListParseResult.NOT_FOUND
    val jobElements = jobsList.qsa(":scope > div")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(outer: Element, siteId: Long): ParsedJob? {
    val el = outer.qs(":scope > div") ?: return null
    val externalId = el.trimmedAttr("id")?.ifEmpty { null } ?: return null

    val linkEl = el.qs("a#job-name-$externalId") ?: return null
    val href = linkEl.trimmedAttr("href") ?: return null
    val externalUrl = "https://www.flexjobs.com$href"
    val title = linkEl.trimmedText()?.ifEmpty { null } ?: return null

    val location = el.qs("li.allowed-location-flag").trimmedText()

    var workMode = WorkMode.REMOTE
    el.qs("li#remoteoption-0-$externalId")?.trimmedText()?.lowercase()?.let { text ->
      if ("hybrid" in text || "option" in text) workMode = WorkMode.HYBRID
      else if ("no remote" in text) workMode = WorkMode.ONSITE
    }

    // `salartRange` is FlexJobs' own typo
    val salary = el.qs("li#salartRange-0-$externalId").trimmedText()
    val description = el.qs("p#description-$externalId").trimmedText()

    val tags =
      el
        .qsa("ul > li.tag-name:not(#remoteoption-0-$externalId):not(#salartRange-0-$externalId)")
        .map { it.trimmedText().orEmpty() }
        .filter { it.isNotEmpty() }

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = "N/A",
      location = location,
      salary = salary,
      workMode = workMode,
      tags = tags,
      description = description,
    )
  }
}
