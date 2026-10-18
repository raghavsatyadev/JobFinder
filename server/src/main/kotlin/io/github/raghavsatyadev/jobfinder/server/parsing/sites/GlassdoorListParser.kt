package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.parentElement
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import org.jsoup.nodes.Element

/** Glassdoor search results (old `parseGlassDoorJobs`). The class names are Glassdoor's hashes. */
object GlassdoorListParser : JobListParser {
  private val EMPLOYER_EST = ciRegex("employer est")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    if (document.qs(".ErrorPage_errorPageTitle__XtznY") != null) return JobListParseResult.EMPTY

    val jobsList = document.qs(".JobsList_jobsList__lqjTr") ?: return JobListParseResult.NOT_FOUND
    // make sure we are not hitting the similar jobs section
    if (jobsList.parentElement()?.parentElement()?.attrOrNull("data-test") == "related-jobs-list") {
      return JobListParseResult.EMPTY
    }

    val jobElements = jobsList.qsa(":scope > li")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val externalId = el.trimmedAttr("data-jobid")?.ifEmpty { null } ?: return null

    val titleEl = el.qs("#job-title-$externalId")
    val externalUrl = titleEl.trimmedAttr("href")?.ifEmpty { null } ?: return null
    val title = titleEl.trimmedText()?.ifEmpty { null } ?: return null

    val companyName =
      el.qs(".jobCard .EmployerProfile_profileContainer__63w3R").trimmedText()?.ifEmpty { null }
        ?: return null
    val companyLogo = el.qs(".jobCard .EmployerLogo_logoContainer__7hw16 > img").trimmedAttr("src")

    val location = el.qs("#job-location-$externalId").trimmedText()

    val salary =
      ParserHelpers.parseSalary(
        el.qs("#job-salary-$externalId")?.trimmedText()?.replaceFirstMatch(EMPLOYER_EST)?.jsTrim()
      )

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      salary = salary,
    )
  }
}
