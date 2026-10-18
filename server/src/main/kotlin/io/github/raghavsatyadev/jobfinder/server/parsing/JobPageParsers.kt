package io.github.raghavsatyadev.jobfinder.server.parsing

import io.github.raghavsatyadev.jobfinder.server.parsing.sites.BuiltInDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.BuiltInListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.DiceDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.DiceListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.FlexJobsDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.FlexJobsListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.GlassdoorDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.GlassdoorListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.HiringCafeDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.HiringCafeListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.IndeedDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.IndeedListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.LinkedInDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.LinkedInListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RemoteIoDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RemoteIoListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RemoteOkDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RemoteOkListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RemotiveDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RemotiveListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.RobertHalfListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.TalentDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.TalentListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.UsaJobsDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.UsaJobsListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.WeWorkRemotelyDescriptionParser
import io.github.raghavsatyadev.jobfinder.server.parsing.sites.WeWorkRemotelyListParser
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider

/**
 * The parsers per board. To add a board, write its `JobListParser` / `JobDescriptionParser` in
 * `parsing/sites/` and add one line to each map; the dispatcher and the golden tests pick it up.
 *
 * [SiteProvider.CUSTOM] is not in the maps: it needs the LLM, so [JobPageParsers] takes it as a
 * constructor argument.
 */
object ParserRegistry {
  /**
   * Boards the old backend no longer scanned (`sites.deprecated`); their parsers are not ported.
   */
  val DEPRECATED: Set<SiteProvider> =
    setOf(
      SiteProvider.BESTJOBS,
      SiteProvider.ECHOJOBS,
      SiteProvider.NAUKRI,
      SiteProvider.ZIP_RECRUITER,
    )

  val listParsers: Map<SiteProvider, JobListParser> =
    mapOf(
      SiteProvider.LINKEDIN to LinkedInListParser,
      SiteProvider.DICE to DiceListParser,
      SiteProvider.REMOTEIO to RemoteIoListParser,
      SiteProvider.HIRING_CAFE to HiringCafeListParser,
      SiteProvider.GLASSDOOR to GlassdoorListParser,
      SiteProvider.INDEED to IndeedListParser,
      SiteProvider.REMOTEOK to RemoteOkListParser,
      SiteProvider.WEWORKREMOTELY to WeWorkRemotelyListParser,
      SiteProvider.FLEXJOBS to FlexJobsListParser,
      SiteProvider.REMOTIVE to RemotiveListParser,
      SiteProvider.BUILTIN to BuiltInListParser,
      SiteProvider.ROBERT_HALF to RobertHalfListParser,
      SiteProvider.USA_JOBS to UsaJobsListParser,
      SiteProvider.TALENT to TalentListParser,
    )

  val descriptionParsers: Map<SiteProvider, JobDescriptionParser> =
    mapOf(
      SiteProvider.LINKEDIN to LinkedInDescriptionParser,
      SiteProvider.GLASSDOOR to GlassdoorDescriptionParser,
      SiteProvider.INDEED to IndeedDescriptionParser,
      SiteProvider.REMOTEOK to RemoteOkDescriptionParser,
      SiteProvider.WEWORKREMOTELY to WeWorkRemotelyDescriptionParser,
      SiteProvider.DICE to DiceDescriptionParser,
      SiteProvider.FLEXJOBS to FlexJobsDescriptionParser,
      SiteProvider.REMOTIVE to RemotiveDescriptionParser,
      SiteProvider.REMOTEIO to RemoteIoDescriptionParser,
      SiteProvider.BUILTIN to BuiltInDescriptionParser,
      SiteProvider.USA_JOBS to UsaJobsDescriptionParser,
      SiteProvider.TALENT to TalentDescriptionParser,
      SiteProvider.HIRING_CAFE to HiringCafeDescriptionParser,
    )
}

/** The custom-board (LLM) parsers; suspend because they call Ollama. */
interface CustomJobsParser {
  suspend fun parseList(input: ListParseInput): JobListParseResult

  suspend fun parseDescription(html: String): JobDescriptionUpdates
}

/**
 * Picks the parser for a site, like the old `parseSiteJobsList` / `parseJobDescriptionUpdates`
 * switches.
 * - Deprecated boards are skipped: no jobs, not a failure (old `scan-urls` check on
 *   `site.deprecated`). Their descriptions are skipped too.
 * - A board without a ported parser returns a failed result with [JobListParseResult.error] (list)
 *   or no updates (description), instead of throwing.
 * - The custom board goes to [customParser]; without one it is treated like a missing parser.
 */
class JobPageParsers(
  private val listParsers: Map<SiteProvider, JobListParser> = ParserRegistry.listParsers,
  private val descriptionParsers: Map<SiteProvider, JobDescriptionParser> =
    ParserRegistry.descriptionParsers,
  private val customParser: CustomJobsParser? = null,
) {
  suspend fun parseList(
    site: Site,
    html: String,
    url: String = "",
    runtimeData: PageRuntimeData? = null,
  ): JobListParseResult {
    if (isDeprecated(site)) return JobListParseResult.SKIPPED
    val input = ListParseInput(siteId = site.id, html = html, url = url, runtimeData = runtimeData)
    if (site.provider == SiteProvider.CUSTOM && customParser != null) {
      return customParser.parseList(input)
    }
    val parser =
      listParsers[site.provider]
        ?: return JobListParseResult.NOT_FOUND.copy(
          error = "No list parser for ${site.provider.wire}"
        )
    return parser.parse(input)
  }

  suspend fun parseDescription(site: Site, html: String): JobDescriptionUpdates {
    if (isDeprecated(site)) return JobDescriptionUpdates.NONE
    if (site.provider == SiteProvider.CUSTOM && customParser != null) {
      return customParser.parseDescription(html)
    }
    return descriptionParsers[site.provider]?.parse(html) ?: JobDescriptionUpdates.NONE
  }

  private fun isDeprecated(site: Site) =
    site.deprecated || site.provider in ParserRegistry.DEPRECATED
}
