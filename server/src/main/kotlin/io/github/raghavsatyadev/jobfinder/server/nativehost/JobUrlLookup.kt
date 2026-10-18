package io.github.raghavsatyadev.jobfinder.server.nativehost

import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import java.net.URI
import java.net.URLDecoder
import kotlin.math.roundToInt

/** A job found for a browser tab's URL; [atsScore] is 0–100, `null` when not scored yet. */
data class NativeJob(val id: Long, val title: String, val company: String, val atsScore: Int?)

/**
 * Finds the saved job a browser tab shows. Two URLs match when host (without `www.`) and path
 * (without a trailing slash) are equal and no query parameter outside [TRACKING_PARAMS] differs.
 * LinkedIn's search page (`currentJobId`) and slug job pages map to `/jobs/view/<id>` first.
 */
class JobUrlLookup(private val jobs: JobsDao, private val ats: suspend (Long) -> AtsSummary?) {
  suspend fun find(url: String): NativeJob? {
    val tab = ParsedUrl.of(canonical(url)) ?: return null
    val pattern = "%" + likeEscape(tab.host + tab.path) + "%"
    val match =
      jobs
        .findByUrlLike(pattern, MAX_CANDIDATES)
        .mapNotNull { job -> ParsedUrl.of(job.externalUrl)?.let { job to it } }
        .filter { (_, stored) -> stored.host == tab.host && stored.path == tab.path }
        .filter { (_, stored) ->
          stored.idParams.all { (k, v) -> tab.idParams[k]?.equals(v) ?: true }
        }
        .maxByOrNull { (job, stored) ->
          stored.idParams.count { (k, v) -> tab.idParams[k] == v } * ID_WEIGHT + job.id
        }
        ?.first ?: return null
    return match.toNativeJob()
  }

  private suspend fun JobEntity.toNativeJob() =
    NativeJob(
      id = id,
      title = title,
      company = companyName,
      atsScore = ats(id)?.overall?.let { (it * ATS_TO_PERCENT).roundToInt().coerceIn(0, 100) },
    )

  /** A URL reduced to what identifies a job page. */
  private data class ParsedUrl(
    val host: String,
    val path: String,
    val idParams: Map<String, String>,
  ) {
    companion object {
      fun of(url: String): ParsedUrl? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in WEB_SCHEMES) return null
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val path = (uri.rawPath ?: "").trimEnd('/')
        return ParsedUrl(host, path, idParams(uri.rawQuery))
      }

      private fun idParams(query: String?): Map<String, String> =
        query
          .orEmpty()
          .split('&')
          .filter { it.isNotEmpty() }
          .map { it.substringBefore('=') to it.substringAfter('=', "") }
          .map { (k, v) -> decode(k) to decode(v) }
          .filter { (k, _) ->
            k.lowercase().let { it !in TRACKING_PARAMS && !it.startsWith("utm_") }
          }
          .toMap()

      private fun decode(s: String) = runCatching {
        URLDecoder.decode(s, Charsets.UTF_8)
      }
        .getOrDefault(s)
    }
  }

  companion object {
    /** Rewrites URLs whose job id is not in the path the scanner saved. */
    internal fun canonical(url: String): String {
      val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url
      val host = uri.host?.lowercase() ?: return url
      if (!host.endsWith("linkedin.com")) return url
      val currentJobId =
        uri.rawQuery
          ?.split('&')
          ?.firstOrNull { it.startsWith("currentJobId=") }
          ?.substringAfter('=')
          ?.takeIf { id -> id.isNotEmpty() && id.all { it.isDigit() } }
      if (currentJobId != null) return "$LINKEDIN_ORIGIN/jobs/view/$currentJobId"
      val view = LINKEDIN_VIEW.find(uri.rawPath.orEmpty()) ?: return url
      return "$LINKEDIN_ORIGIN/jobs/view/${view.groupValues[1]}"
    }

    private fun likeEscape(s: String) =
      s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private const val LINKEDIN_ORIGIN = "https://www.linkedin.com"
    /** `/jobs/view/<slug>-<id>/` or `/jobs/view/<id>/`. */
    private val LINKEDIN_VIEW = Regex("^/jobs/view/(?:[^/]*-)?(\\d+)/?$")
    private val WEB_SCHEMES = setOf("http", "https")
    /** Query parameters that track the visit rather than name the job, in lowercase. */
    private val TRACKING_PARAMS =
      setOf(
        "refid",
        "trackingid",
        "ebp",
        "trk",
        "trkinfo",
        "alternatechannel",
        "lipi",
        "licu",
        "ref",
        "src",
        "source",
        "from",
        "position",
        "pagenum",
        "origin",
        "vjs",
        "tk",
        "gclid",
        "fbclid",
      )
    private const val MAX_CANDIDATES = 200
    /** ATS `overall` is out of 10. */
    private const val ATS_TO_PERCENT = 10.0
    /** Matching identity parameters outweigh recency. */
    private const val ID_WEIGHT = 1L shl 40
  }
}
