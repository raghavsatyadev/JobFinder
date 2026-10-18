package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a rating or salary comes from. Only public pages are read, signed out. */
@Serializable
enum class InsightSource {
  @SerialName("ambitionbox") AMBITIONBOX,
  @SerialName("glassdoor") GLASSDOOR,
}

/** One rated aspect, such as work-life balance, out of 5. */
@Serializable data class CategoryRating(val name: String, val rating: Double)

/** A company's employee rating on one site. */
@Serializable
data class CompanyRating(
  val source: InsightSource,
  /** Out of 5. */
  val rating: Double,
  val reviews: Int? = null,
  /** Share of employees who would recommend the company to a friend. */
  val recommendPercent: Int? = null,
  val categories: List<CategoryRating> = emptyList(),
  /** Short quotes from recent reviews. */
  val likes: List<String> = emptyList(),
  val dislikes: List<String> = emptyList(),
  /** The page it was read from. */
  val url: String,
)

/** A yearly base salary range for one role at one company. */
@Serializable
data class SalaryRange(
  val source: InsightSource,
  /** The role the numbers are for. */
  val role: String,
  /** `false` when [role] is the nearest role to the job's title, not the title itself. */
  val exactRole: Boolean,
  val currency: String,
  /** 10th percentile, median and 90th percentile. */
  val low: Long,
  val median: Long,
  val high: Long,
  val minExperience: Int? = null,
  val maxExperience: Int? = null,
  /** Salaries the range is based on. */
  val samples: Int? = null,
  val url: String,
)

/** Ratings and salary for a job's company, read from public pages and cached on the computer. */
@Serializable
data class CompanyInsights(
  val company: String,
  val ratings: List<CompanyRating> = emptyList(),
  val salary: SalaryRange? = null,
  val fetchedAt: Long,
)

@Serializable
enum class InsightsState {
  /**
   * Waiting for the browser; an
   * [io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent.InsightsReady] follows.
   */
  @SerialName("queued") QUEUED,
  @SerialName("ready") READY,
  /** The sites could not be read (blocked, offline); asking again retries. */
  @SerialName("failed") FAILED,
  /** Only the computer can read the sites; this app has no browser. */
  @SerialName("unavailable") UNAVAILABLE,
}

@Serializable
data class InsightsStatus(val state: InsightsState, val insights: CompanyInsights? = null)

/** How ratings and salaries are filed, the same on the computer and in backups. */
object InsightKeys {
  private val SUFFIXES =
    Regex(
      "\\b(inc|incorporated|ltd|limited|llc|llp|plc|pvt|private|corp|corporation|co|gmbh|ag|sa|bv)\\b"
    )

  /** "Capital One, Inc." and "CapitalOne" both give `capitalone`. */
  fun company(name: String): String =
    name.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(SUFFIXES, " ").replace(" ", "")

  /** Titles that differ only in case or punctuation share a salary. */
  fun title(title: String): String = title.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
