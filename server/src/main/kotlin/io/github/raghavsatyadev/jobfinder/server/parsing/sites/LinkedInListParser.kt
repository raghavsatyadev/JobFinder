package io.github.raghavsatyadev.jobfinder.server.parsing.sites

import io.github.raghavsatyadev.jobfinder.server.parsing.Dom
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParseResult
import io.github.raghavsatyadev.jobfinder.server.parsing.JobListParser
import io.github.raghavsatyadev.jobfinder.server.parsing.ListParseInput
import io.github.raghavsatyadev.jobfinder.server.parsing.ParsedJob
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers.stripWorkModeMarkers
import io.github.raghavsatyadev.jobfinder.server.parsing.ParserHelpers.workModeFromText
import io.github.raghavsatyadev.jobfinder.server.parsing.WebUrl
import io.github.raghavsatyadev.jobfinder.server.parsing.attrOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.domText
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrim
import io.github.raghavsatyadev.jobfinder.server.parsing.jsTrimOrNull
import io.github.raghavsatyadev.jobfinder.server.parsing.qs
import io.github.raghavsatyadev.jobfinder.server.parsing.qsa
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.slf4j.LoggerFactory

/**
 * LinkedIn search results (old `parsers/linkedin.ts`). LinkedIn has shipped six layouts; the parser
 * tries them in order and uses the first one that finds job cards:
 * 1. guest (logged-out) search page, `.jobs-search__results-list`;
 * 2. logged-in classic search, `li[data-occludable-job-id]` / `.scaffold-layout__list`;
 * 3. jobs home feed modules, `div[data-view-name="job-card"]`;
 * 4. AI search results, cards carry `data-view-tracking-scope` JSON with the job URN;
 * 5. AI search results v2, `componentkey="job-card-component-ref-<jobId>"`;
 * 6. AI search results v3, UUID `componentkey`s mapped to job ids through the rehydration script
 *    (from the DOM, or from [ListParseInput.runtimeData]) or the `f2a-react-context` attribute.
 *
 * Where the old code would throw on unexpected markup (bad JSON in a tracking attribute, a v3 card
 * without its details block, a relative v3 URL), this port skips that card instead.
 */
object LinkedInListParser : JobListParser {
  private val log = LoggerFactory.getLogger(LinkedInListParser::class.java)
  private const val ORIGIN = "https://www.linkedin.com"
  private val SALARY_MARKERS = listOf("/yr", "/year", "/yearly", "/hr", "/hour", "/hourly")

  override fun parse(input: ListParseInput): JobListParseResult {
    val document = Dom.parse(input.html)
    val siteId = input.siteId

    val noResults =
      document.qs(".no-results")
        ?: document.qs(".jobs-search-no-results-banner")
        ?: document.qs(".jobs-semantic-search__no-results")
        ?: document.qs("svg#empty-room-small")
    if (noResults != null) return JobListParseResult.EMPTY

    val layout = findLayout(document, input)
    val jobs =
      layout.elements
        .mapNotNull { el ->
          runCatching { layout.parse(el, siteId) }
            .onFailure { log.debug("LinkedIn v{} card skipped: {}", layout.version, it.toString()) }
            .getOrNull()
        }
        .map(::moveSalaryOutOfTags)

    log.info(
      "Parsed LinkedIn jobs with parser version {}. Found {} valid jobs out of {} elements.",
      layout.version,
      jobs.size,
      layout.elements.size,
    )
    return JobListParseResult(
      jobs = jobs,
      listFound = layout.listFound,
      elementsCount = layout.elements.size,
    )
  }

  private class Layout(
    val version: Int,
    val elements: List<Element>,
    val listFound: Boolean,
    val parse: (Element, Long) -> ParsedJob?,
  )

  private fun findLayout(document: Document, input: ListParseInput): Layout {
    // v1: guest page.
    var version = 1
    var elements = document.qs(".jobs-search__results-list")?.qsa("li").orEmpty()
    if (elements.isNotEmpty()) return Layout(version, elements, true, ::parseV1)

    // v2: logged in.
    val v2List =
      document.qs("ul li[data-occludable-job-id]")?.closest("ul")
        ?: document.qs(".scaffold-layout__list ul")
    if (v2List != null) {
      version = 2
      elements = v2List.qsa("li")
      if (elements.isNotEmpty()) return Layout(version, elements, true, ::parseV2)
    }

    // v3: jobs home feed.
    val v3List =
      document.qs("div[data-view-name=\"jobs-home-infinite-jymbii-jobs-feed-module\"]")
        ?: document.qs("div[data-view-name=\"jobs-home-top-jymbii-jobs-feed-module\"]")
        ?: document.qs("div[data-view-name=\"feed-full-update\"]")
    if (v3List != null) {
      version = 3
      elements = v3List.qsa("div[data-view-name=\"job-card\"]")
      if (elements.isNotEmpty()) return Layout(version, elements, true, ::parseV3)
    }

    val aiList = document.qs("div[componentkey=\"SearchResultsMainContent\"]")
    if (aiList != null) {
      // v4: AI search.
      elements = aiList.qsa("div[data-view-tracking-scope]")
      if (elements.isNotEmpty()) return Layout(4, elements, true, ::parseV4)

      // v5: AI search v2.
      elements = aiList.qsa("div[componentkey^=\"job-card-component-ref\"] > div[componentkey]")
      if (elements.isNotEmpty()) return Layout(5, elements, true, ::parseV5)

      // v6: AI search v3, UUID component keys.
      val jobIdMap = LinkedHashMap<String, String>()
      val rehydrateScript = document.qs("script#rehydrate-data")?.domText().orEmpty()
      val fromRuntime = input.runtimeData?.linkedInComoRehydration
      val rehydration =
        when {
          rehydrateScript.isNotEmpty() -> rehydrateScript
          !fromRuntime.isNullOrEmpty() -> fromRuntime
          else -> null
        }
      rehydration?.let { jobIdMap.putAll(LinkedInRehydration.buildJobIdMap(it)) }

      elements = aiList.qsa("div[role=\"button\"][componentkey] > div[componentkey]")
      for (el in elements) {
        val id = LinkedInRehydration.jobIdFromReactContext(el.attrOrNull("f2a-react-context"))
        if (id != null) jobIdMap[el.attrOrNull("componentkey").orEmpty()] = id
      }
      elements = elements.filter { el ->
        val key = el.attrOrNull("componentkey")
        !key.isNullOrEmpty() && jobIdMap.containsKey(key)
      }
      return Layout(6, elements, elements.isNotEmpty()) { el, siteId ->
        parseV6(el, siteId, jobIdMap)
      }
    }

    // Nothing matched: report the last layout tried, like the old code.
    return Layout(version, elements, false) { _, _ -> null }
  }

  private fun parseV1(el: Element, siteId: Long): ParsedJob? {
    val externalUrl =
      el.qs(".base-card__full-link")?.attrOrNull("href")?.ifEmpty { null } ?: return null
    val externalId =
      externalUrl.substringBefore('?').substringAfterLast('/').ifEmpty { null } ?: return null
    val title = el.qs(".base-search-card__title")?.domText().jsTrimOrNull() ?: return null
    val companyName =
      el.qs(".base-search-card__subtitle")?.qs("a")?.domText().jsTrimOrNull() ?: return null
    val companyLogo =
      el.qs(".search-entity-media")?.qs("img")?.attrOrNull("data-delayed-url")?.ifEmpty { null }
    val location =
      el.qs(".job-search-card__location")?.domText()?.jsTrim()?.let(::stripWorkModeMarkers)

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
    )
  }

  private fun parseV2(el: Element, siteId: Long): ParsedJob? {
    val jobCard = el.qs("div[data-job-id]")
    // `??` in the old code: an empty attribute does not fall through to the card.
    val externalId =
      (el.attrOrNull("data-occludable-job-id")?.jsTrim()
          ?: jobCard?.attrOrNull("data-job-id")?.jsTrim())
        ?.ifEmpty { null } ?: return null

    val urlEl = el.qs(".job-card-list__title--link") ?: jobCard?.qs("a") ?: return null
    val path = urlEl.attrOrNull("href")?.jsTrim()
    var externalUrl =
      if (path != null && path.startsWith(ORIGIN)) path else "$ORIGIN${path ?: "undefined"}"
    if ("jobs/search-results/?currentJobId" in externalUrl) {
      // The job id is in the query string.
      val query = externalUrl.split('?').getOrElse(1) { "" }
      val currentJobId = WebUrl.parseForm(query).firstOrNull { it.first == "currentJobId" }?.second
      externalUrl = "$ORIGIN/jobs/view/$currentJobId"
    }

    val title =
      (urlEl.qs(":scope > strong")
          ?: urlEl.qs(":scope > span > strong")
          ?: urlEl.qs(".job-card-job-posting-card-wrapper__title > span > strong"))
        ?.domText()
        .jsTrimOrNull() ?: return null
    val companyName =
      (el.qs(".artdeco-entity-lockup__subtitle > span")
          ?: el.qs(".artdeco-entity-lockup__subtitle"))
        ?.domText()
        .jsTrimOrNull() ?: return null
    val companyLogo =
      el.qs(".ivm-view-attr__img-wrapper")?.qs("img")?.attrOrNull("src")?.ifEmpty { null }
    val rawLocation = el.qs(".artdeco-entity-lockup__caption")?.domText()?.jsTrim()

    val benefitTags =
      el
        .qs(".artdeco-entity-lockup__metadata")
        ?.domText()
        ?.jsTrim()
        ?.split('·')
        ?.map { it.jsTrim() }
        .orEmpty()

    var footerItems =
      el.qsa(".job-card-list__footer-wrapper.job-card-container__footer-wrapper > li")
    if (footerItems.isEmpty())
      footerItems = el.qsa(".job-card-job-posting-card-wrapper__footer-items > li")
    val footerTags =
      footerItems
        .map { li ->
          li.qsa(".visually-hidden").forEach(Element::remove)
          li.domText().jsTrim().lowercase()
        }
        .filter { "viewed" !in it }

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = rawLocation?.let(::stripWorkModeMarkers),
      workMode = workModeFromText(rawLocation),
      tags = benefitTags + footerTags,
    )
  }

  private fun parseV3(el: Element, siteId: Long): ParsedJob? {
    val urlEl = el.qs(":scope > a") ?: return null
    val rawUrl = urlEl.attrOrNull("href")?.ifEmpty { null } ?: return null
    val externalId =
      WebUrl.parse(rawUrl).searchParams.get("currentJobId")?.ifEmpty { null } ?: return null
    val externalUrl = "$ORIGIN/jobs/view/$externalId"

    val detailsEl = urlEl.qs(":scope > div > div > div > div:first-child") ?: return null
    val details =
      detailsEl
        .qsa("p")
        .map { it.domText().jsTrim() }
        .filter { it.length > 1 }
        .map { it.split('\n')[0].jsTrim() }

    val title = details.getOrNull(0)?.ifEmpty { null } ?: return null
    val companyName = details.getOrNull(1)?.ifEmpty { null } ?: return null
    val rawLocation = details.getOrNull(2)

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      location = rawLocation?.let { stripWorkModeMarkers(it).jsTrim() },
      workMode = workModeFromText(rawLocation),
      tags = details.drop(3),
    )
  }

  private fun parseV4(el: Element, siteId: Long): ParsedJob? {
    val card = el.qs(":scope > div > div > div > div") ?: return null

    val externalId =
      el.qsa("div[data-view-tracking-scope]").firstNotNullOfOrNull(::jobIdFromTrackingScope)
    if (externalId == null) {
      log.debug("LinkedIn v4: no externalId found")
      return null
    }
    val externalUrl = "$ORIGIN/jobs/view/$externalId"

    val detailsEl = card.qs(":scope > div:first-child > div:first-child") ?: return null
    val titleEl = detailsEl.qs(":scope > div > p:first-child")
    var title = titleEl?.domText()?.jsTrim()
    if (titleEl?.qs(":scope > span[aria-hidden=true]") != null) {
      title = titleEl.qs(":scope > span:not([aria-hidden])")?.domText()?.jsTrim() ?: title
    }
    val companyName = detailsEl.qs(":scope > div > div:nth-child(2)")?.domText()?.jsTrim()
    val location = detailsEl.qs(":scope > div > p:nth-child(3)")?.domText()?.jsTrim()
    if (title.isNullOrEmpty() || companyName.isNullOrEmpty()) return null

    val companyLogo = card.qs(":scope figure img")?.attrOrNull("src")?.ifEmpty { null }

    var infoEl = card.qs(":scope > div:nth-child(2) > div:first-child") ?: return null
    var connectionsEl = card.qs(":scope > div:nth-child(2) > div:nth-child(2)")
    var metadataEl = card.qs(":scope > div:nth-child(2) > div:nth-child(3)")
    var benefitsEl: Element? = infoEl
    if (metadataEl == null) {
      // No salary block: everything moves up by one.
      metadataEl = connectionsEl
      connectionsEl = infoEl
      benefitsEl = null
    }

    fun texts(els: List<Element>) = els.map { it.domText().jsTrim() }.filter { it.length > 1 }
    val benefitTags = texts(benefitsEl?.qsa(":scope > div:first-child > p").orEmpty())
    val connectionTags = texts(connectionsEl?.qsa(":scope > p").orEmpty())
    val metadataParagraphs = metadataEl?.qsa(":scope > p").orEmpty()
    // Only the text directly inside each paragraph, not its children.
    val metadataTags =
      metadataParagraphs
        .map { p ->
          p.childNodes()
            .filterIsInstance<TextNode>()
            .map { it.wholeText.jsTrim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .jsTrim()
        }
        .filter { it.length > 1 }
    val metadataTags2 =
      texts(metadataParagraphs.flatMap { it.qsa(":scope > span:not([aria-hidden])") })

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = externalUrl,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location?.let { stripWorkModeMarkers(it).jsTrim() },
      workMode = workModeFromText(location),
      tags = benefitTags + connectionTags + metadataTags + metadataTags2,
    )
  }

  /**
   * The job id from a v4 `data-view-tracking-scope` attribute: a JSON array whose first item has a
   * `breadcrumb.content.data` byte array, itself UTF-8 JSON with `jobPosting.objectUrn`
   * (`urn:li:fs_normalized_jobPosting:4323524962`).
   */
  private fun jobIdFromTrackingScope(el: Element): String? {
    val raw = el.attrOrNull("data-view-tracking-scope") ?: ""
    val data =
      (Json.parseToJsonElement(raw) as? JsonArray)
        ?.firstOrNull()
        ?.let { it as? JsonObject }
        ?.get("breadcrumb")
        ?.let { it as? JsonObject }
        ?.get("content")
        ?.let { it as? JsonObject }
        ?.get("data") as? JsonArray ?: return null
    val bytes = ByteArray(data.size) { (data[it] as JsonPrimitive).int.toByte() }
    val decoded = Json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: return null
    val urn =
      (decoded["jobPosting"] as? JsonObject)
        ?.get("objectUrn")
        ?.let { it as? JsonPrimitive }
        ?.content ?: return null
    return urn.substringAfterLast(':')
  }

  private fun parseV5(el: Element, siteId: Long): ParsedJob? {
    val externalId =
      el
        .attrOrNull("componentkey")
        ?.jsTrim()
        ?.replaceFirst("job-card-component-ref-", "")
        ?.ifEmpty { null }
    if (externalId == null) {
      log.debug("LinkedIn v5: no externalUrlId found")
      return null
    }
    return parseAiSearchCard(el, siteId, externalId)
  }

  private fun parseV6(el: Element, siteId: Long, jobIdMap: Map<String, String>): ParsedJob? {
    val uuid = el.attrOrNull("componentkey")?.jsTrim()?.ifEmpty { null } ?: return null
    val externalId = jobIdMap[uuid]?.ifEmpty { null } ?: return null
    return parseAiSearchCard(el, siteId, externalId)
  }

  /** The card body shared by v5 and v6. */
  private fun parseAiSearchCard(el: Element, siteId: Long, externalId: String): ParsedJob? {
    val mainInfoEl =
      el.qs(":scope > div > div > div:first-child > div:first-child > div:first-child")
        ?: return null
    val titleEl =
      mainInfoEl.qs(":scope > p:first-child")
        ?: mainInfoEl.qs(":scope > div:first-child > p:first-child")
        ?: return null
    val rawTitle =
      if (titleEl.childrenSize() > 1) {
        titleEl.qs(":scope > span:not([aria-hidden])")?.domText().jsTrimOrNull()
          ?: titleEl.qs(":scope > span[aria-hidden=\"true\"]")?.domText().jsTrimOrNull()
      } else {
        titleEl.domText().jsTrim()
      }
    val title = rawTitle?.replaceFirst("(Verified job)", "")?.jsTrim()
    val companyName = mainInfoEl.qs(":scope > div:nth-child(2) > p")?.domText()?.jsTrim()
    val locationAndType = mainInfoEl.qs(":scope > p:nth-child(3)")?.domText()?.jsTrim()
    if (title.isNullOrEmpty() || companyName.isNullOrEmpty()) return null

    val companyLogo =
      el.qs(":scope > div:first-child figure:first-child img")?.attrOrNull("src")?.ifEmpty { null }

    // One or two blocks here; tags are the paragraphs of the second one.
    val metadataEl = el.qs(":scope > div > div > div:nth-child(2)")
    val tags =
      metadataEl
        ?.qsa(":scope > div p")
        .orEmpty()
        .map { tagEl ->
          if (tagEl.childrenSize() > 1) {
            // Several spans: take the visible one.
            val visible = tagEl.qsa("span").firstOrNull { it.attrOrNull("aria-hidden") != "true" }
            visible?.domText().jsTrimOrNull() ?: tagEl.domText().jsTrim()
          } else {
            tagEl.domText().jsTrim()
          }
        }
        .filter { it.length > 1 }

    return ParsedJob(
      siteId = siteId,
      externalId = externalId,
      externalUrl = "$ORIGIN/jobs/view/$externalId",
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = locationAndType?.let { stripWorkModeMarkers(it).jsTrim() },
      workMode = workModeFromText(locationAndType),
      tags = tags,
    )
  }

  /** Cleans the tags and moves a `/yr` or `/hr` tag into the salary (old post-processing). */
  private fun moveSalaryOutOfTags(job: ParsedJob): ParsedJob {
    val tags = job.tags.map { it.jsTrim().replace("\n", "") }.filter { it.isNotEmpty() }
    if (job.salary != null) return job.copy(tags = tags)
    val salary = tags.firstOrNull { tag -> SALARY_MARKERS.any { it in tag.lowercase() } }
    return job.copy(
      salary = salary,
      tags = if (salary == null) tags else tags.filter { it != salary },
    )
  }
}
