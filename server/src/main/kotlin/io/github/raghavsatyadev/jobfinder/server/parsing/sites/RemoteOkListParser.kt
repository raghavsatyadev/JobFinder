package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrimOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/** RemoteOK search results (old `parseRemoteOkJobs`). Every job is remote. */
object RemoteOkListParser : JobListParser {
  private val REMOTE = ciRegex("remote")
  private val PROBABLY = ciRegex("probably")
  private val OR = ciRegex("${JS_S}or${JS_S}")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val jobsList = document.qs("#jobsboard") ?: return JobListParseResult.NOT_FOUND
    val jobElements = jobsList.qsa("tr.job")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val externalId = el.trimmedAttr("data-slug")?.ifEmpty { null } ?: return null
    val externalUrl = "https://remoteok.io/remote-jobs/$externalId"

    val companyEl = el.qs("td.company") ?: return null
    val title =
      companyEl.qs("a[itemprop='url'] > h2[itemprop='title']").trimmedText()?.ifEmpty { null }
        ?: return null
    val companyName =
      companyEl
        .qs("span[itemprop='hiringOrganization'] > h3[itemprop='name']")
        .trimmedText()
        ?.ifEmpty { null } ?: return null

    val companyLogo =
      el.qs("td.image.has-logo")?.qs("a > img")?.attrOrNull("data-src").nullIfEmpty()

    val locationTexts =
      companyEl
        .qsa("div.location")
        .map {
          it
            .trimmedText()
            .orEmpty()
            .replaceFirstMatch(REMOTE)
            .replaceFirstMatch(PROBABLY)
            .replaceFirstMatch(OR)
            .let { s -> s.jsTrimOrNull().orEmpty() }
        }
        .filter { it.isNotEmpty() }
    val location =
      locationTexts
        .filter { '$' !in it }
        .joinToString("/") { ParserHelpers.parseLocation(it).orEmpty() }

    val salary =
      ParserHelpers.parseSalary(locationTexts.firstOrNull { '$' in it }?.let { it.jsTrimOrNull() })

    val tags =
      el.qs("td.tags")?.qsa("a").orEmpty().map { it.qs("a > div > h3").trimmedText().orEmpty() }

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      salary = salary,
      workMode = WorkMode.REMOTE,
      tags = tags,
    )
  }
}
