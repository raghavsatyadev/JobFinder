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
import io.github.raghavsatyadev.jobfinder.server.parsing.parentElement
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import org.jsoup.nodes.Element

/** hiring.cafe search results (old `parsers/hiringCafe.ts`). */
object HiringCafeListParser : JobListParser {
  private val JOB_PATH = Regex("^/(?:viewjob|job)/([^/]+)$")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)

    // hiring.cafe has used both `/viewjob/<id>` and `/job/<id>` for job cards.
    val allJobLinks = document.qsa("a[href^=\"/viewjob/\"], a[href^=\"/job/\"]")
    if (allJobLinks.isEmpty()) {
      // An app shell without cards is an empty result; no shell at all is a failure.
      val hasAppShell = document.qs("a[href*=\"/company/\"], a[href*=\"/org/\"]") != null
      return JobListParseResult(jobs = emptyList(), listFound = hasAppShell, elementsCount = 0)
    }

    // The same job can show up in more than one section.
    val jobLinks = allJobLinks.distinctBy { it.attrOrNull("href").orEmpty() }
    val jobs = jobLinks.mapNotNull { parseJob(it, input.siteId) }
    return JobListParseResult(jobs = jobs, listFound = true, elementsCount = jobLinks.size)
  }

  private fun parseJob(link: Element, siteId: Long): ParsedJob? {
    val href = link.attrOrNull("href").jsTrimOrNull() ?: return null
    val externalId = extractExternalId(href) ?: return null
    val externalUrl = "https://hiring.cafe$href"

    val card = findJobCard(link) ?: return null
    // The title is the only span with a `text-start` class.
    val title = card.qs("span[class*=\"text-start\"]")?.domText().jsTrimOrNull() ?: return null
    // The logo's alt text is the most reliable company name.
    val logo = card.qs("picture > img") ?: return null
    val companyName = logo.attrOrNull("alt").jsTrimOrNull() ?: return null
    val companyLogo = logo.attrOrNull("src").jsTrimOrNull()
    val location = card.qs("[class*=\"bg-gray-50\"] span")?.domText().jsTrimOrNull()

    val tagElements = card.qsa("[class*=\"gap-1\"] > span")
    val tagTexts = tagElements.map { it.domText().jsTrim() }.filter { it.isNotEmpty() }
    fun Element.classes() = attrOrNull("class").orEmpty()

    // Older cards marked the salary green; newer ones render it as plain text.
    val salary =
      tagElements.firstOrNull { "green" in it.classes() }?.domText().jsTrimOrNull()
        ?: tagTexts.firstOrNull { ParserHelpers.matchesSalaryPattern(it) }

    // Older cards marked the work mode cyan; newer ones render Remote/Hybrid/Onsite as a plain tag.
    val workplaceTag =
      tagElements.firstOrNull { "cyan" in it.classes() }?.domText().jsTrimOrNull()?.lowercase()
        ?: tagTexts.firstOrNull(::isWorkplaceTag)?.lowercase()

    // Keep only skill/requirement tags.
    var tags =
      tagElements
        .filter {
          val classes = it.classes()
          val text = it.domText().jsTrim()
          "green" !in classes &&
            "cyan" !in classes &&
            !ParserHelpers.matchesSalaryPattern(text) &&
            !isWorkplaceTag(text)
        }
        .map { it.domText().jsTrim() }
        .filter { it.isNotEmpty() }
    if (tags.isEmpty()) tags = parseSkillTags(card)

    val workMode =
      when {
        workplaceTag == null -> null
        workplaceTag == "remote" -> WorkMode.REMOTE
        "hybrid" in workplaceTag -> WorkMode.HYBRID
        else -> WorkMode.ONSITE
      }

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

  private fun extractExternalId(href: String): String? {
    val path = href.substringBefore('?').removeSuffix("/")
    return JOB_PATH.find(path)?.groupValues?.get(1)?.jsTrimOrNull()
  }

  /** The nearest ancestor (or the link) with `bg-white` that holds a title and a logo. */
  private fun findJobCard(link: Element): Element? =
    generateSequence(link) { it.parentElement() }
      .firstOrNull {
        "bg-white" in it.attrOrNull("class").orEmpty() &&
          it.qs("span[class*=\"text-start\"]") != null &&
          it.qs("picture > img") != null
      }

  private fun isWorkplaceTag(text: String): Boolean {
    val normalized = text.jsTrim().lowercase()
    return normalized == "remote" || normalized == "onsite" || "hybrid" in normalized
  }

  private fun parseSkillTags(card: Element): List<String> {
    val skillText =
      card
        .qsa("span[class*=\"line-clamp-2\"]")
        .map { it.domText().jsTrim() }
        .firstOrNull { "," in it }
    return skillText?.split(',')?.map { it.jsTrim() }?.filter { it.isNotEmpty() }.orEmpty()
  }
}
