package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.domText
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrimOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import org.jsoup.nodes.Element

/** Dice search results (old `parsers/dice.ts`). */
object DiceListParser : JobListParser {
  /** The relative date Dice appends to the location line: `Today`, `2d ago`, `3 days ago`. */
  private val POSTED_AT =
    Regex(
      "^(today|yesterday|just posted|\\d+\\s*[a-z]{1,8}\\.?(\\s*ago)?)$",
      RegexOption.IGNORE_CASE,
    )

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    val noResults =
      document.qs(".no-jobs-message") ?: document.qs("div[data-testid='job-search-no-results']")
    if (noResults != null) return JobListParseResult.EMPTY

    // Dice renders two lists labelled `Job search results`: the results and a `Similar Jobs` grid
    // at the bottom, so scope to the results container first.
    val jobsList =
      document.qs("div[data-testid=\"job-search-split-view-card-list-scroll\"] [role=\"list\"]")
        ?: document.qs("[role=\"list\"][aria-label=\"Job search results\"]")
        ?: document.qs("[role=\"list\"], [aria-label=\"Job search results\"]")
        ?: return JobListParseResult.NOT_FOUND

    val jobElements = jobsList.qsa("div[data-testid=\"job-card\"]")
    val jobs = jobElements.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobElements.size)
  }

  private fun parseJob(el: Element, siteId: Long): ParsedJob? {
    val externalId = el.attrOrNull("data-id").jsTrimOrNull() ?: return null
    val jobGuid = el.attrOrNull("data-job-guid").jsTrimOrNull() ?: return null
    val externalUrl = "https://www.dice.com/job-detail/$jobGuid".jsTrim()

    val title =
      el.qs("a[data-testid=\"job-search-job-detail-link\"]")?.domText().jsTrimOrNull()
        ?: return null
    val companyName =
      el.qs("p[data-testid=\"job-card-company-name\"]")?.domText().jsTrimOrNull() ?: return null
    val companyLogo =
      el.qs("a[aria-label=\"Company Logo\"]")?.qs("img")?.attrOrNull("src").jsTrimOrNull()

    // The meta line holds the location and the posting date, `Boston, Massachusetts • 2d ago`. It
    // is the only paragraph left once the company name and the tag chips (they have an id) are out.
    val metaText =
      el
        .qsa("p")
        .filter {
          it.attrOrNull("id") == null && it.attrOrNull("data-testid") != "job-card-company-name"
        }
        .map { it.domText().jsTrim() }
        .firstOrNull { it.isNotEmpty() }

    val metaParts =
      metaText?.split('•').orEmpty().map { it.jsTrim() }.filter { it.isNotEmpty() }.toMutableList()
    val postedAt =
      if (POSTED_AT.matches(metaParts.lastOrNull().orEmpty()))
        metaParts.removeAt(metaParts.lastIndex)
      else null
    val location = metaParts.joinToString(" • ").ifEmpty { null }
    val workMode = ParserHelpers.workModeFromText(location)

    val tags = mutableListOf<String>()
    if (postedAt != null) tags.add(postedAt)
    // Tag chips: `Sponsored`, `Easy Apply`, `Full-time`, `USD 80,000.00 - 190,000.00 per year`.
    tags +=
      el.qsa("div[aria-labelledby] > p").map { it.domText().jsTrim() }.filter { it.isNotEmpty() }

    // Dice labels the pay chip; older markup needs the tags sniffed.
    val salary =
      el.qs("div[aria-labelledby=\"salary-label\"] > p")?.domText().jsTrimOrNull()
        ?: tags.firstOrNull { ParserHelpers.isSalaryText(it) }
    if (salary != null) tags.remove(salary)

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      salary = salary,
      workMode = workMode,
      tags = tags,
    )
  }
}
