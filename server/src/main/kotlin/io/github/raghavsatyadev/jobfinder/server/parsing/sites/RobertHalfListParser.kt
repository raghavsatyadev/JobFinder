package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.HtmlToMarkdown
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.parentElement
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/**
 * Robert Half search results (old `parseRobertHalfJobs`). The cards are `<rhcl-job-card>` web
 * components whose attributes hold everything, including the whole description, so Robert Half has
 * no description parser.
 */
object RobertHalfListParser : JobListParser {
  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    if (document.qs("rhcl-block-copy[component-title=\"No matching jobs found\"]") != null) {
      return JobListParseResult.EMPTY
    }

    val jobsList =
      document.qs("rhcl-job-card")?.parentElement()?.parentElement()?.parentElement()
        ?: return JobListParseResult.NOT_FOUND
    val jobElements = jobsList.qsa("rhcl-job-card")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val externalId = el.trimmedAttr("job-id")?.ifEmpty { null } ?: return null
    val externalUrl = el.trimmedAttr("destination")?.ifEmpty { null } ?: return null
    val title = el.trimmedAttr("headline")?.ifEmpty { null } ?: return null

    val location = el.trimmedAttr("location")
    val salary = salary(el)

    val worksite = el.trimmedAttr("worksite")?.lowercase()
    val workMode =
      when {
        worksite == null -> null
        "remote" in worksite -> WorkMode.REMOTE
        "hybrid" in worksite -> WorkMode.HYBRID
        "onsite" in worksite -> WorkMode.ONSITE
        else -> null
      }

    val description = el.trimmedAttr("copy")?.ifEmpty { null }?.let { HtmlToMarkdown.convert(it) }
    val tags = listOf(el.trimmedAttr("type").orEmpty())

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      // Robert Half does not show the company name on the search results page
      companyName = "N/A",
      location = location,
      salary = salary,
      workMode = workMode,
      description = description,
      tags = tags,
    )
  }

  /** `USD 85k - 100k per year`, or `USD 40 - 50 per hour`; none when either end is missing/zero. */
  private fun salary(el: Element): String? {
    val min = el.trimmedAttr("salary-min")?.ifEmpty { null }
    val max = el.trimmedAttr("salary-max")?.ifEmpty { null }
    val currency = el.trimmedAttr("salary-currency")
    val period = el.trimmedAttr("salary-period")
    if (min == null || max == null || min == "0" || max == "0") return null

    val hourly = period?.lowercase()?.contains("hour") == true
    fun format(value: String): String {
      val amount = jsParseFloat(value)?.let { it / (if (hourly) 1 else 1000) }
      return jsNumber(amount) + if (hourly) "" else "k"
    }
    return "${currency ?: "undefined"} ${format(min)} - ${format(max)} ${period ?: "undefined"}"
      .jsTrim()
  }
}
