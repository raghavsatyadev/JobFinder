package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) {
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
  /** Latest update first. */
  @SerialName("latest") LATEST("latest"),

  /** Highest ATS score first; jobs without a score last. */
  @SerialName("ats") ATS("ats"),

  /** ATS score in 10% bands, highest first; latest first inside a band. */
  @SerialName("ats_latest") ATS_LATEST("ats_latest"),

  /** Remote jobs first, then the rest; latest first inside each. */
  @SerialName("remote_first") REMOTE_FIRST("remote_first"),

  /** Onsite jobs first, then the rest; latest first inside each. */
  @SerialName("onsite_first") ONSITE_FIRST("onsite_first");

  val usesAts: Boolean
    get() = this == ATS || this == ATS_LATEST

  /** The sort key of [job], highest first; 0 for every job under [LATEST]. */
  fun key(job: Job): Long =
    when (this) {
      LATEST -> 0
      // Truncated like SQLite's CAST(... AS INTEGER), so both sides agree.
      ATS -> job.ats?.let { (it.overall * ATS_SCALE).toLong() } ?: NO_SCORE
      ATS_LATEST -> job.ats?.let { minOf(it.overall.toLong(), TOP_BAND) } ?: NO_SCORE
      REMOTE_FIRST -> if (job.workMode == WorkMode.REMOTE) 1 else 0
      ONSITE_FIRST -> if (job.workMode == WorkMode.ONSITE) 1 else 0
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

    /** Scores of 9 and up (90–100%) share the top band. */
    const val TOP_BAND = 9L

    const val NO_SCORE = -1L

    fun fromWireOrNull(value: String?): JobSort? = entries.firstOrNull { it.wire == value }
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
