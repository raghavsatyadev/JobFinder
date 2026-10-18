@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/** Filters shared by job lists and job counts. Empty lists and a blank search mean "no filter". */
@Serializable
data class JobFilter(
  /** Free text over title and company, with English stemming and stop words. */
  val search: String? = null,
  /**
   * Sources: whole job boards and single saved searches. A job matches when its board is in
   * [siteIds] or its search is in [linkIds] (either one), so a board and a search on another board
   * can be picked together.
   */
  val siteIds: List<Long> = emptyList(),
  val linkIds: List<Long> = emptyList(),
  /** Jobs that carry any of these labels. */
  val labels: List<JobLabel> = emptyList(),
  val workModes: List<WorkMode> = emptyList(),
  val applyMethods: List<ApplyMethod> = emptyList(),
  /** The list's order; counts ignore it. */
  val sort: JobSort = JobSort.LATEST,
  /**
   * Only starred jobs, of every status in [STARRED_STATUSES]: the Starred tab. A list ignores the
   * status it is asked for.
   */
  val starred: Boolean = false,
) {
  companion object {
    /** The tabs a starred job shows from. */
    val STARRED_STATUSES: List<JobStatus> =
      listOf(
        JobStatus.NEW,
        JobStatus.APPLIED,
        JobStatus.ARCHIVED,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
      )
  }

  /** The job's board or search is one of the chosen sources; `true` with none chosen. */
  fun matchesSource(siteId: Long, linkId: Long?): Boolean =
    (siteIds.isEmpty() && linkIds.isEmpty()) ||
      siteId in siteIds ||
      (linkId != null && linkId in linkIds)
}

/**
 * The order of a job list. Every order ends with the latest update first, ties by id; [key] is the
 * part before that, highest first. The server's SQL computes the same key (`JobQueries`).
 */
@Serializable
enum class JobSort(val wire: String) {
  /** Latest update first. Saved sorts that were dropped come back as this. */
  @SerialName("latest") @JsonNames("remote_first", "onsite_first") LATEST("latest"),

  /** Oldest update first. */
  @SerialName("oldest") OLDEST("oldest"),

  /** Highest ATS score first; jobs without a score last. */
  @SerialName("ats") @JsonNames("ats_latest") ATS("ats"),

  /** Lowest ATS score first; jobs without a score last. */
  @SerialName("ats_lowest") ATS_LOWEST("ats_lowest");

  val usesAts: Boolean
    get() = this == ATS || this == ATS_LOWEST

  /** The sort key of [job], highest first; 0 for every job under [LATEST]. */
  fun key(job: Job): Long =
    when (this) {
      LATEST -> 0
      OLDEST -> -job.updatedAt
      // Truncated like SQLite's CAST(... AS INTEGER), so both sides agree.
      ATS -> job.ats?.let { (it.overall * ATS_SCALE).toLong() } ?: NO_SCORE
      ATS_LOWEST -> job.ats?.let { -(it.overall * ATS_SCALE).toLong() } ?: NO_SCORE_LOWEST
    }

  /** Highest [key] first, then latest update, then highest id. */
  val comparator: Comparator<Job>
    get() =
      compareByDescending<Job> { key(it) }
        .thenByDescending { it.updatedAt }
        .thenByDescending { it.id }

  companion object {
    /** The overall score is out of 10; ×100 keeps two decimals in an integer key. */
    const val ATS_SCALE = 100

    const val NO_SCORE = -1L

    /** Below every negated score (at most 10 × [ATS_SCALE]). */
    const val NO_SCORE_LOWEST = -1_000_000L

    /** Sorts that were dropped, as what they became. */
    private val DROPPED =
      mapOf("ats_latest" to ATS, "remote_first" to LATEST, "onsite_first" to LATEST)

    fun fromWireOrNull(value: String?): JobSort? =
      entries.firstOrNull { it.wire == value } ?: DROPPED[value]
  }
}

/** One page of jobs, newest update first. */
@Serializable
data class JobPage(
  val jobs: List<Job>,
  /** Pass to the next call to continue; `null` when there are no more jobs. */
  val nextPageToken: String? = null,
)

/** Job counts per status, for the status tabs. Statuses without jobs are absent. */
@Serializable
data class JobCounts(val byStatus: Map<JobStatus, Int> = emptyMap()) {
  operator fun get(status: JobStatus): Int = byStatus[status] ?: 0
}
