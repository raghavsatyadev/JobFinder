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

/** Talent.com search results (old `parseTalentJobs`). */
object TalentListParser : JobListParser {
  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val jobsList = document.qs(".joblist") ?: return JobListParseResult.NOT_FOUND
    val jobElements = jobsList.qsa(".card__job")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val path = el.qs(".link-job-wrap").trimmedAttr("data-link")?.ifEmpty { null } ?: return null
    val externalUrl = "https://talent.com$path"
    val externalId = el.trimmedAttr("data-id")?.ifEmpty { null } ?: return null
    val title = el.qs(".card__job-title").trimmedText()?.ifEmpty { null } ?: return null
    val companyName =
      el.qs(".card__job-empname-label").trimmedText()?.ifEmpty { null } ?: return null
    val companyLogo = el.qs(".card__job-logo").trimmedAttr("src")
    val location = el.qs(".card__job-location").trimmedText().orEmpty()
    val workMode =
      if (el.qs(".card__job-badge-remote").trimmedText().isNullOrEmpty()) WorkMode.ONSITE
      else WorkMode.REMOTE

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      workMode = workMode,
    )
  }
}
