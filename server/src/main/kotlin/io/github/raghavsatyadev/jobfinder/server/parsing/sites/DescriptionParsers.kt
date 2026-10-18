package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.HtmlToMarkdown
import io.github.raghavsatyadev.jobfinder.server.parsing.JobDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.JobDescriptionUpdates
import io.github.raghavsatyadev.jobfinder.server.parsing.SelectorDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.innerHtml
import io.github.raghavsatyadev.jobfinder.server.parsing.qs

// Board description parsers that are only a list of selectors (old
// `SITE_PROVIDER_QUERY_SELECTORS`).

object GlassdoorDescriptionParser :
  SelectorDescriptionParser(
    listOf(
      "[data-brandviews*=\"jobview-description\"]",
      ".JobDetails_JobDescriptionUpdates__uW_fK",
      ".JobDetails_jobDescription__uW_fK",
    )
  )

object WeWorkRemotelyDescriptionParser :
  SelectorDescriptionParser(listOf(".lis-container__job__content__description"))

object DiceDescriptionParser :
  SelectorDescriptionParser(
    listOf("#jobDescription", ".job-description", "[class*=\"job-detail-description-module\"]")
  )

/** Paywalled: only the teaser is on the page. */
object FlexJobsDescriptionParser : SelectorDescriptionParser(listOf("#job-description"))

object RemoteIoDescriptionParser :
  SelectorDescriptionParser(listOf("#job-description", "[data-testid=\"text-job-description\"]"))

object BuiltInDescriptionParser :
  SelectorDescriptionParser(
    listOf(".job-post-item .container.py-lg .row > .col-12 > .position-relative")
  )

object UsaJobsDescriptionParser : SelectorDescriptionParser(listOf(".apply-joa-defaults"))

/** The class names are Talent's hashes; only the first paragraph matches, as before. */
object TalentDescriptionParser :
  SelectorDescriptionParser(
    listOf(".sc-e78c1cd5-10.sc-e78c1cd5-11.sc-207c7d5e-10.dwTTNY.gdYndp.jkXeTb > p")
  )

object HiringCafeDescriptionParser :
  SelectorDescriptionParser(listOf("article.prose", "article[class*=\"prose\"]"))

/**
 * RemoteOK: the `.company_profile` block is cut out of the page first (it can sit inside the
 * description container).
 */
object RemoteOkDescriptionParser : JobDescriptionParser {
  private val selectors = SelectorDescriptionParser(listOf(".description"))

  override fun parse(html: String): JobDescriptionUpdates {
    val document = Dom.parse(html)
    val container = selectors.findContainer(document) ?: return JobDescriptionUpdates.NONE
    document.qs(".company_profile")?.remove()
    return JobDescriptionUpdates(description = HtmlToMarkdown.convert(container.innerHtml()))
  }
}

/**
 * Remotive: its own page first (`job might be archived` when the container is empty after
 * conversion), then the Indeed, LinkedIn and Talent parsers, because Remotive often redirects to
 * those boards. The first non-empty description wins; only the description is kept.
 */
object RemotiveDescriptionParser : JobDescriptionParser {
  private val own = SelectorDescriptionParser(listOf("section div.tw-mt-8 > div.left > div"))

  override fun parse(html: String): JobDescriptionUpdates {
    val parsers =
      listOf(
        JobDescriptionParser { page ->
          val description =
            own.parse(page).description ?: return@JobDescriptionParser JobDescriptionUpdates.NONE
          JobDescriptionUpdates(description = description.ifEmpty { "job might be archived" })
        },
        IndeedDescriptionParser,
        LinkedInDescriptionParser,
        TalentDescriptionParser,
      )
    for (parser in parsers) {
      val description = parser.parse(html).description
      if (!description.isNullOrEmpty()) return JobDescriptionUpdates(description = description)
    }
    return JobDescriptionUpdates.NONE
  }
}
