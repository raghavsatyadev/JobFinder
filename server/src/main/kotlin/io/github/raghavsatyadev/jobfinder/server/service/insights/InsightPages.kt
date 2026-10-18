package io.github.raghavsatyadev.jobfinder.server.service.insights

import io.github.raghavsatyadev.jobfinder.shared.model.CategoryRating
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyRating
import io.github.raghavsatyadev.jobfinder.shared.model.InsightKeys
import io.github.raghavsatyadev.jobfinder.shared.model.InsightSource
import io.github.raghavsatyadev.jobfinder.shared.model.SalaryRange
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup

/** A loaded public page. */
data class InsightPage(val url: String, val finalUrl: String, val html: String, val status: Int?)

/** Company names compared without case, punctuation, spaces or legal suffixes. */
object CompanyNames {
  /** "Capital One, Inc." and "CapitalOne" both give `capitalone`. */
  fun key(name: String): String = InsightKeys.company(name)

  /** [candidate] (a site's page name) is the company called [name]. */
  fun matches(name: String, candidate: String): Boolean {
    val a = key(name)
    val b = key(candidate.replace('-', ' '))
    if (a.isEmpty() || b.isEmpty()) return false
    if (a == b) return true
    // "swiggy" for "Swiggy Instamart" is fine; short keys must match exactly.
    return minOf(a.length, b.length) >= MIN_PREFIX && (a.startsWith(b) || b.startsWith(a))
  }

  private const val MIN_PREFIX = 5
}

/** Reads AmbitionBox's public company pages: the search, reviews and salary pages. */
object AmbitionBox {
  const val BASE = "https://www.ambitionbox.com"

  fun searchUrl(company: String) =
    "$BASE/search?q=${java.net.URLEncoder.encode(company, Charsets.UTF_8).replace("+", "%20")}"

  fun reviewsUrl(slug: String) = "$BASE/reviews/$slug-reviews"

  fun salaryUrl(slug: String, roleSlug: String) = "$BASE/salaries/$slug-salaries/$roleSlug"

  private val OVERVIEW = Regex("ambitionbox\\.com/overview/([a-z0-9-]+)-overview")

  /** Company page names in the search results, best first. */
  fun searchSlugs(html: String): List<String> =
    OVERVIEW.findAll(html).map { it.groupValues[1] }.distinct().toList()

  private val CATEGORIES =
    listOf(
      "workLifeRating" to "Work-life balance",
      "compensationBenefitsRating" to "Salary and benefits",
      "careerGrowthRating" to "Career growth",
      "jobSecurityRating" to "Job security",
      "skillDevelopmentRating" to "Skill development",
      "companyCultureRating" to "Culture",
      "workSatisfactionRating" to "Work satisfaction",
    )

  /** The reviews page: overall and category ratings and a few recent likes and dislikes. */
  fun reviews(page: InsightPage): CompanyRating? {
    if (page.status == HTTP_NOT_FOUND) return null
    val props = nextData(page.html) ?: return null
    val ratings = props.obj("ratingsData") ?: return null
    val overall = ratings.num("overallCompanyRating") ?: return null
    val reviews = props.arr("reviewsData").orEmpty().mapNotNull { it as? JsonObject }
    fun quotes(key: String) =
      reviews.mapNotNull { it.str(key)?.let(::quote) }.distinct().take(MAX_QUOTES)
    return CompanyRating(
      source = InsightSource.AMBITIONBOX,
      rating = overall.oneDecimal(),
      reviews =
        props.num("reviewCount")?.toInt()
          ?: aggregateCount(page.html)
          ?: props
            .arr("ratingDistribution")
            ?.sumOf { (it as JsonObject).num("count") ?: 0.0 }
            ?.toInt(),
      categories =
        CATEGORIES.mapNotNull { (key, name) ->
          ratings.num(key)?.let { CategoryRating(name, it.oneDecimal()) }
        },
      likes = quotes("likesText"),
      dislikes = quotes("disLikesText"),
      url = page.finalUrl,
    )
  }

  /** A role's salary page; `null` when the role has no page or no salaries. */
  fun salary(page: InsightPage, exactRole: Boolean): SalaryRange? {
    if (page.status == HTTP_NOT_FOUND) return null
    val data =
      jsonLd(page.html).firstOrNull { it.str("@type") == "OccupationAggregationByEmployer" }
    val estimate = data?.arr("estimatedSalary")?.firstOrNull() as? JsonObject ?: return null
    val median = estimate.num("median") ?: return null
    return SalaryRange(
      source = InsightSource.AMBITIONBOX,
      role = data.str("name") ?: return null,
      exactRole = exactRole,
      currency = estimate.str("currency") ?: "INR",
      low = (estimate.num("percentile10") ?: median).roundToLong(),
      median = median.roundToLong(),
      high = (estimate.num("percentile90") ?: median).roundToLong(),
      minExperience = data.num("yearsExperienceMin")?.toInt(),
      maxExperience = data.num("yearsExperienceMax")?.toInt(),
      samples = data.num("sampleSize")?.toInt(),
      url = page.finalUrl,
    )
  }

  private fun aggregateCount(html: String): Int? =
    jsonLd(html)
      .firstNotNullOfOrNull { ld ->
        (ld.obj("aggregateRating") ?: ld.takeIf { it.str("@type") == "EmployerAggregateRating" })
          ?.num("ratingCount")
      }
      ?.toInt()

  private fun nextData(html: String): JsonObject? {
    val script = Jsoup.parse(html).getElementById("__NEXT_DATA__")?.data() ?: return null
    return runCatching { JSON.parseToJsonElement(script).jsonObject }
      .getOrNull()
      ?.obj("props")
      ?.obj("pageProps")
  }

  private const val MAX_QUOTES = 3
}

/** Reads Glassdoor's public search and company overview pages. */
object Glassdoor {
  const val BASE = "https://www.glassdoor.co.in"

  fun searchUrl(company: String) =
    "$BASE/Search/results.htm?keyword=${java.net.URLEncoder.encode(company, Charsets.UTF_8)}"

  private val OVERVIEW = Regex("/Overview/Working-at-([A-Za-z0-9-]+)-EI_IE\\d+[^\"'\\s<>]*\\.htm")

  /** Overview links in the search results as (page name, absolute URL), best first. */
  fun searchResults(page: InsightPage): List<Pair<String, String>> {
    if (OVERVIEW.containsMatchIn(page.finalUrl)) {
      val m = OVERVIEW.find(page.finalUrl)!!
      return listOf(m.groupValues[1] to page.finalUrl)
    }
    return OVERVIEW.findAll(page.html)
      .map { it.groupValues[1] to BASE + it.value }
      .distinctBy { it.second }
      .toList()
  }

  private val RECOMMEND = Regex("(\\d{1,3})% of employees would recommend")
  private val REVIEWS = Regex("based on ([\\d,]+) anonymous reviews")
  private val CULTURE = Regex("has a (\\d(?:\\.\\d)?)/5 rating for company culture")
  private val WORK_LIFE = Regex("work-life balance at .{1,80}? at (\\d(?:\\.\\d)?) out of 5")

  /** The overview page: rating, review count, recommend share, culture and work-life ratings. */
  fun overview(page: InsightPage): CompanyRating? {
    if (page.status == HTTP_NOT_FOUND) return null
    val lds = jsonLd(page.html)
    val aggregate = lds.firstOrNull { it.str("@type") == "EmployerAggregateRating" } ?: return null
    val rating = aggregate.num("ratingValue") ?: return null
    val faq =
      lds
        .filter { it.str("@type") == "FAQPage" }
        .flatMap { it.arr("mainEntity").orEmpty() }
        .mapNotNull { (it as? JsonObject)?.obj("acceptedAnswer")?.str("text") }
        .joinToString("\n") { Jsoup.parse(it).text() }
    return CompanyRating(
      source = InsightSource.GLASSDOOR,
      rating = rating.oneDecimal(),
      reviews =
        REVIEWS.find(faq)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
          ?: aggregate.num("ratingCount")?.toInt(),
      recommendPercent = RECOMMEND.find(faq)?.groupValues?.get(1)?.toIntOrNull(),
      categories =
        listOfNotNull(
          WORK_LIFE.find(faq)?.let { CategoryRating("Work-life balance", it.number()) },
          CULTURE.find(faq)?.let { CategoryRating("Culture", it.number()) },
        ),
      url = page.finalUrl,
    )
  }

  private fun MatchResult.number() = groupValues[1].toDouble()
}

private const val HTTP_NOT_FOUND = 404
private const val MAX_QUOTE_CHARS = 200

private val JSON = Json { ignoreUnknownKeys = true }

/** Every JSON-LD object on the page; broken blocks are skipped. */
internal fun jsonLd(html: String): List<JsonObject> =
  Jsoup.parse(html).select("script[type=application/ld+json]").flatMap { script ->
    when (val parsed = runCatching { JSON.parseToJsonElement(script.data()) }.getOrNull()) {
      is JsonObject -> listOf(parsed)
      is JsonArray -> parsed.mapNotNull { it as? JsonObject }
      else -> emptyList()
    }
  }

private fun quote(text: String): String? {
  val clean = text.replace(Regex("\\s+"), " ").trim()
  if (clean.length < 3) return null
  return if (clean.length <= MAX_QUOTE_CHARS) clean
  else clean.take(MAX_QUOTE_CHARS - 1).trimEnd() + "…"
}

private fun Double.oneDecimal() = (this * 10).roundToInt() / 10.0

private fun JsonObject.obj(key: String) = this[key] as? JsonObject

private fun JsonObject.arr(key: String) = this[key] as? JsonArray

private fun JsonObject.str(key: String): String? =
  (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf(String::isNotBlank)

/** Numbers come as JSON numbers or as strings ("3.7", "1854665.43"). */
private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.number()

private fun JsonElement.number(): Double? =
  (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()
