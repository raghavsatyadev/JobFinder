package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Indeed search results (old `parseIndeedJobs`). */
object IndeedListParser : JobListParser {
  private val REMOTE = ciRegex("remote")
  private val HYBRID = ciRegex("hybrid")
  private val IN = ciRegex("${JS_S}in${JS_S}")
  private val A_YEAR = Regex("a year")
  private val FRACTION = ciRegex("\\.[0-9]+")
  private val COMMA = ciRegex(",")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    // `.css-1z0pyms.e1wnkr790` is from the individual company page
    if (
      document.qs(".jobsearch-NoResult-messageContainer") != null ||
        document.qs(".css-1z0pyms.e1wnkr790") != null
    ) {
      return JobListParseResult.EMPTY
    }

    val jobsList =
      document.qs("#mosaic-jobResults ul")
        ?: document.qs("#mosaic-provider-jobcards ul")
        ?: return JobListParseResult.NOT_FOUND

    val jobElements = jobsList.qsa(":scope > li")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId, document) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long, document: Document): ParsedJob? {
    val jobLinkEl = el.qs(".jobTitle > a") ?: return null
    val externalId = jobLinkEl.trimmedAttr("id")?.ifEmpty { null } ?: return null
    jobLinkEl.trimmedAttr("href")?.ifEmpty { null } ?: return null
    val jk = jobLinkEl.trimmedAttr("data-jk")?.ifEmpty { null } ?: return null
    val externalUrl = "https://www.indeed.com/viewjob?jk=$jk"

    val title = jobLinkEl.qs("span").trimmedText()?.ifEmpty { null } ?: return null

    val companyEl = el.qs(".company_location")
    val companyName =
      companyEl?.qs(":scope > div span[data-testid=company-name]").trimmedText()?.ifEmpty { null }
        ?: document.qs("h1[data-testid=PageHeader-title-jobs]").trimmedText()?.ifEmpty { null }
        ?: return null

    val rawLocation = companyEl?.qs(":scope > div > div[data-testid=text-location]").trimmedText()
    val lower = rawLocation?.lowercase()
    var workMode = WorkMode.ONSITE
    if (lower?.contains("remote") == true) workMode = WorkMode.REMOTE
    if (lower?.contains("hybrid") == true) workMode = WorkMode.HYBRID
    val location =
      rawLocation
        ?.replaceFirstMatch(REMOTE)
        ?.replaceFirstMatch(HYBRID)
        ?.replaceFirstMatch(IN)
        ?.jsTrim()

    val salary = el.qs(".salary-snippet-container").trimmedText()?.let { formatSalary(it) }

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

  /** `$90,000 - $120,000 a year` becomes `$90k - $120k`; hourly pay keeps its numbers + `/hr`. */
  private fun formatSalary(text: String): String {
    val salary = text.replaceFirstMatch(A_YEAR).jsTrim()
    if ("hour" in salary) return ParserHelpers.parseSalary(salary) + "/hr"
    return salary.replaceFirstMatch(A_YEAR).jsTrim().split('-').joinToString(" - ") { part ->
      val digits = part.jsTrim().drop(1).replaceFirstMatch(FRACTION).replaceFirstMatch(COMMA)
      // Math.round(parseInt(x) / 1000); an unparsable amount printed `NaN`.
      val thousands = jsParseInt(digits)?.let { Math.floorDiv(it * 2 + 1000, 2000L) }
      "$${thousands ?: "NaN"}k"
    }
  }
}
