package io.github.raghavsatyadev.jobfinder.server.service.ghost

import io.github.raghavsatyadev.jobfinder.server.service.insights.CompanyNames
import io.github.raghavsatyadev.jobfinder.shared.model.CareersHost
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One open job on a company's careers site. */
@Serializable data class CareersJob(val title: String, val location: String?, val url: String)

/** A company's board on one careers host: its public page and its open jobs. */
data class CareersBoard(
  val host: CareersHost,
  val slug: String,
  /** The company's name as the host gives it, when it does. */
  val name: String?,
  val jobs: List<CareersJob>,
) {
  val url: String
    get() = boardUrl(host, slug)
}

/** The company's careers page on [host]. */
fun boardUrl(host: CareersHost, slug: String): String =
  when (host) {
    CareersHost.GREENHOUSE -> "https://job-boards.greenhouse.io/$slug"
    CareersHost.LEVER -> "https://jobs.lever.co/$slug"
    CareersHost.ASHBY -> "https://jobs.ashbyhq.com/$slug"
    CareersHost.SMARTRECRUITERS -> "https://careers.smartrecruiters.com/$slug"
    CareersHost.WORKABLE -> "https://apply.workable.com/$slug/"
    CareersHost.RECRUITEE -> "https://$slug.recruitee.com/"
  }

/**
 * Careers hosts with a public job list. Every read is one GET of the list the host's own careers
 * page uses, signed out; nothing is posted and no page is scraped.
 */
object CareersSites {
  private val LINKS =
    listOf(
      CareersHost.GREENHOUSE to
        Regex(
          """(?:boards|job-boards)(?:\.eu)?\.greenhouse\.io/(?:embed/job_board\?for=)?([A-Za-z0-9_-]+)"""
        ),
      CareersHost.LEVER to Regex("""jobs\.(?:eu\.)?lever\.co/([A-Za-z0-9_.-]+)"""),
      CareersHost.ASHBY to Regex("""jobs\.ashbyhq\.com/([A-Za-z0-9_.%-]+)"""),
      CareersHost.SMARTRECRUITERS to
        Regex("""(?:jobs|careers)\.smartrecruiters\.com/([A-Za-z0-9_-]+)"""),
      CareersHost.WORKABLE to Regex("""apply\.workable\.com/([A-Za-z0-9_-]+)"""),
      CareersHost.RECRUITEE to Regex("""([A-Za-z0-9-]+)\.recruitee\.com"""),
    )

  /** Path parts that are not a company's board. */
  private val NOT_SLUGS = setOf("api", "embed", "v1", "j", "jobs", "www", "careers")

  /** The careers boards [text] links to (a job's own URL, or links in its description). */
  fun linkedBoards(text: String): List<Pair<CareersHost, String>> =
    LINKS.flatMap { (host, pattern) ->
        pattern.findAll(text).map { host to it.groupValues[1].lowercase() }
      }
      .filter { it.second !in NOT_SLUGS }
      .distinct()

  /** Board names a company called [company] would likely use: `capitalone`, `capital-one`. */
  fun guessSlugs(company: String): List<String> {
    val compact = CompanyNames.key(company)
    if (compact.length < MIN_GUESS) return emptyList()
    val words =
      company.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() && it !in SUFFIXES }
    val hyphen = words.joinToString("-")
    return listOf(compact, hyphen).distinct()
  }

  private const val MIN_GUESS = 3
  private val SUFFIXES =
    setOf("inc", "ltd", "limited", "llc", "llp", "plc", "pvt", "private", "corp", "co", "gmbh")
}

/** Reads a board's jobs from its host; `null` when the host has no board by that name. */
class CareersReader(private val http: HttpClient) {
  private val json = Json { ignoreUnknownKeys = true }

  suspend fun read(host: CareersHost, slug: String): CareersBoard? =
    when (host) {
      CareersHost.GREENHOUSE -> greenhouse(slug)
      CareersHost.LEVER -> lever(slug)
      CareersHost.ASHBY -> ashby(slug)
      CareersHost.SMARTRECRUITERS -> smartRecruiters(slug)
      CareersHost.WORKABLE -> workable(slug)
      CareersHost.RECRUITEE -> recruitee(slug)
    }

  private suspend fun greenhouse(slug: String): CareersBoard? {
    val board = getJson("https://boards-api.greenhouse.io/v1/boards/$slug")?.obj ?: return null
    val jobs = getJson("https://boards-api.greenhouse.io/v1/boards/$slug/jobs")?.obj ?: return null
    return CareersBoard(
      CareersHost.GREENHOUSE,
      slug,
      board.str("name"),
      jobs.arr("jobs").mapNotNull {
        job(it.str("title"), it.obj("location")?.str("name"), it.str("absolute_url"))
      },
    )
  }

  private suspend fun lever(slug: String): CareersBoard? {
    val list =
      getJson("https://api.lever.co/v0/postings/$slug?mode=json") as? JsonArray ?: return null
    return CareersBoard(
      CareersHost.LEVER,
      slug,
      null,
      list.mapNotNull {
        job(it.str("text"), it.obj("categories")?.str("location"), it.str("hostedUrl"))
      },
    )
  }

  private suspend fun ashby(slug: String): CareersBoard? {
    val board = getJson("https://api.ashbyhq.com/posting-api/job-board/$slug")?.obj ?: return null
    return CareersBoard(
      CareersHost.ASHBY,
      slug,
      null,
      board.arr("jobs").mapNotNull { job(it.str("title"), it.str("location"), it.str("jobUrl")) },
    )
  }

  private suspend fun smartRecruiters(slug: String): CareersBoard? {
    val page =
      getJson("https://api.smartrecruiters.com/v1/companies/$slug/postings?limit=100")?.obj
        ?: return null
    val content = page.arr("content")
    // An unknown company gives an empty list rather than a 404.
    if (content.isEmpty()) return null
    return CareersBoard(
      CareersHost.SMARTRECRUITERS,
      slug,
      content.first().obj("company")?.str("name"),
      content.mapNotNull {
        val location = it.obj("location")
        job(
          it.str("name"),
          listOfNotNull(location?.str("city"), location?.str("country")).joinToString(", "),
          "https://jobs.smartrecruiters.com/$slug/${it.str("id")}",
        )
      },
    )
  }

  private suspend fun workable(slug: String): CareersBoard? {
    val board =
      getJson("https://apply.workable.com/api/v1/widget/accounts/$slug")?.obj ?: return null
    return CareersBoard(
      CareersHost.WORKABLE,
      slug,
      board.str("name"),
      board.arr("jobs").mapNotNull {
        job(
          it.str("title"),
          listOfNotNull(it.str("city"), it.str("country")).joinToString(", "),
          it.str("url"),
        )
      },
    )
  }

  private suspend fun recruitee(slug: String): CareersBoard? {
    val board = getJson("https://$slug.recruitee.com/api/offers/")?.obj ?: return null
    return CareersBoard(
      CareersHost.RECRUITEE,
      slug,
      null,
      board.arr("offers").mapNotNull {
        job(it.str("title"), it.str("location"), it.str("careers_url"))
      },
    )
  }

  /** The JSON at [url]; `null` when the host has no such board (any 4xx). */
  private suspend fun getJson(url: String): JsonElement? {
    val response = http.get(url) { header("Accept", "application/json") }
    if (response.status.value in 400..499) return null
    if (response.status != HttpStatusCode.OK) {
      throw CareersReadException("$url answered ${response.status.value}")
    }
    return try {
      json.parseToJsonElement(response.bodyAsText())
    } catch (e: Exception) {
      throw CareersReadException("$url gave no JSON: ${e.message}")
    }
  }

  private fun job(title: String?, location: String?, url: String?): CareersJob? =
    if (title.isNullOrBlank() || url.isNullOrBlank()) null
    else CareersJob(title.trim(), location?.takeIf { it.isNotBlank() }, url)

  private val JsonElement.obj: JsonObject?
    get() = this as? JsonObject

  private fun JsonElement.obj(name: String): JsonObject? = (this as? JsonObject)?.get(name)?.obj

  private fun JsonElement.str(name: String): String? =
    ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.contentOrNull

  private fun JsonObject.arr(name: String): List<JsonElement> =
    (get(name) as? JsonArray)?.toList().orEmpty()
}

/** A careers host could not be read (server error, offline); the check is tried again later. */
class CareersReadException(message: String) : Exception(message)
