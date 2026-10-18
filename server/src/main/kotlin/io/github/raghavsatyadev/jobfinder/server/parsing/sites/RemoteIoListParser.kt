package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.domText
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrimOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/**
 * Remote.io search results (old `parsers/remoteio.ts`). Every job is remote.
 *
 * Cards are read from `:scope > div > div` as before. The old fixture (and its test) has the cards
 * one level up, `list > div > a`, where the old selector finds 1 element and no job; only then this
 * port retries with `:scope > div`. Pages the old parser handled give the same result.
 */
object RemoteIoListParser : JobListParser {
  private const val ORIGIN = "https://remote.io"

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    val noResults = document.qs(".shadow-singlePost")
    if (
      noResults != null && noResults.domText().jsTrim().lowercase().startsWith("no results found")
    ) {
      return JobListParseResult.EMPTY
    }

    val jobsList =
      document.qs("div[id=\"root\"] > div:nth-child(4) > div > section > div:nth-child(3)")
        ?: return JobListParseResult.NOT_FOUND

    var jobElements = jobsList.qsa(":scope > div > div")
    var jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    if (jobs.isEmpty()) {
      val cards = jobsList.qsa(":scope > div")
      val cardJobs = cards.mapNotNull { parseJob(it, input.siteId) }
      if (cardJobs.isNotEmpty()) {
        jobElements = cards
        jobs = cardJobs
      }
    }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val jobInfo = el.qs("a") ?: return null

    val href = jobInfo.attrOrNull("href")
    val externalUrl =
      when {
        href == null || href.isEmpty() -> return null
        href.startsWith("http") -> href.jsTrim()
        else -> "$ORIGIN${href.jsTrim()}"
      }
    val externalId = jobInfo.attrOrNull("data-testid").jsTrimOrNull() ?: return null

    val title =
      jobInfo.qs(":scope > div > div:first-child > div:nth-child(2) > h3")?.domText().jsTrimOrNull()
        ?: return null
    val companyName =
      jobInfo.qs(":scope > div > div:first-child > div:nth-child(2) > p")?.domText().jsTrimOrNull()
        ?: return null
    val location =
      jobInfo
        .qs(":scope > div > div:first-child > div:nth-child(2) > div > button")
        ?.domText()
        ?.jsTrim()
    val logoPath =
      jobInfo
        .qs(":scope > div > div:first-child > div:first-child > img")
        ?.attrOrNull("src")
        .jsTrimOrNull()
    val companyLogo = logoPath?.let { "$ORIGIN$it" }

    val tags =
      el
        .qsa(":scope > a > div > div:nth-child(4) > button")
        .map { it.domText().jsTrim() }
        .filter { it.isNotEmpty() }
        .toMutableList()
    el.qs(":scope > a > div > span[data-testid^=\"text-time-\"]")?.let {
      tags.add(it.domText().jsTrim())
    }

    val salary = el.qs(":scope > a > div span[data-testid^=\"text-salary-\"]")?.domText()?.jsTrim()

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
