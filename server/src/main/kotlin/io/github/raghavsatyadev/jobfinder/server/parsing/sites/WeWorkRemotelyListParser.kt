package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/** We Work Remotely search results (old `parseWeWorkRemotelyJobs`). Every job is remote. */
object WeWorkRemotelyListParser : JobListParser {
  private val ONLY = ciRegex("only")
  private val WORLDWIDE = ciRegex("anywhere in the world")
  private val BACKGROUND = ciRegex("background-image:url\\(")
  private val CLOSE_PAREN = Regex("\\)")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    if (document.qs(".no_results") != null || document.qs(".advanced_no_results") != null) {
      return JobListParseResult.EMPTY
    }

    val jobsLists = document.qsa("section.jobs")
    if (jobsLists.isEmpty()) return JobListParseResult.NOT_FOUND

    val jobElements = jobsLists.flatMap { it.qsa("ul > li") }
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }.distinctBy { it.externalId }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val linkToJob = el.qs(":scope > a") ?: return null
    val href = linkToJob.trimmedAttr("href") ?: return null
    val externalId = href.split('/').filter { it.isNotEmpty() }.lastOrNull() ?: return null
    val externalUrl = "https://weworkremotely.com$href"

    val title =
      linkToJob.qs(".new-listing__header__title").trimmedText()?.ifEmpty { null } ?: return null
    val companyName =
      linkToJob.qs(".new-listing__company-name").trimmedText()?.ifEmpty { null } ?: return null

    // background-image:url(https://we-work-remotely.imgix.net/logos/0082/0572/logo.gif?ixlib=...)
    val companyLogo =
      el
        .qs("div.tooltip > a > div")
        ?.attrOrNull("style")
        ?.replaceFirstMatch(BACKGROUND)
        ?.replaceFirstMatch(CLOSE_PAREN)
        ?.jsTrim()
        .nullIfEmpty()

    val location =
      linkToJob.qs(".new-listing__company-headquarters").trimmedText()?.let { text ->
        text.split('/').joinToString("/") {
          it.jsTrim().replaceFirstMatch(ONLY).replaceFirstMatch(WORLDWIDE, "worldwide").jsTrim()
        }
      }

    val tags = el.qsa(".new-listing__categories__category").map { it.trimmedText().orEmpty() }

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      workMode = WorkMode.REMOTE,
      tags = tags,
    )
  }
}
