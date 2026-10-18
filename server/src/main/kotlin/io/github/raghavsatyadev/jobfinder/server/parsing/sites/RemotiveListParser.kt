package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/** Remotive search results (old `parseRemotiveJobs`). Every job is remote. */
object RemotiveListParser : JobListParser {
  private val NOT_LETTERS = Regex("[^a-zA-Z${ParserHelpers.JS_SPACE_CLASS},]")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val jobsList = document.qs("#hits > ul") ?: return JobListParseResult.NOT_FOUND

    var jobElements = jobsList.qsa("li")
    if (jobElements.isEmpty()) jobElements = jobsList.qsa("div[x-data]")

    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val jobTitle = el.qs(".job-tile-title a") ?: return null
    val externalUrl = jobTitle.trimmedAttr("href")?.ifEmpty { null } ?: return null
    val externalId =
      externalUrl.substringBefore('?').split('/').last().ifEmpty { null } ?: return null

    val title = jobTitle.qs("span").trimmedText()?.ifEmpty { null } ?: return null
    val companyName =
      jobTitle.qs("span:nth-child(3)").trimmedText()?.ifEmpty { null } ?: return null

    // `getAttribute('src') || undefined`: not trimmed
    val companyLogo =
      el.qs(".company-page-logo-container")?.qs("img")?.attrOrNull("src").nullIfEmpty()

    val location =
      el
        .qsa(".job-tile-location")
        .map { it.trimmedText().orEmpty().replace(NOT_LETTERS, "") }
        .distinct()
        .filter { it.isNotEmpty() }
        .joinToString(", ")

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      workMode = WorkMode.REMOTE,
    )
  }
}
