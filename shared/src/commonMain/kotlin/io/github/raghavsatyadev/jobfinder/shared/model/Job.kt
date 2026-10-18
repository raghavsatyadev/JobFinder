package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/**
 * A job found by a scan. Timestamps are epoch milliseconds (UTC).
 *
 * Job lists leave [description] `null`; load it with the single-job call.
 */
@Serializable
data class Job(
  val id: Long,
  val externalId: String,
  val externalUrl: String,
  val siteId: Long,
  val linkId: Long? = null,
  val title: String,
  val companyName: String,
  val companyLogo: String? = null,
  val location: String? = null,
  val salary: String? = null,
  /** The old `jobType` column. */
  val workMode: WorkMode? = null,
  val tags: List<String> = emptyList(),
  val description: String? = null,
  val status: JobStatus = JobStatus.NEW,
  val labels: List<JobLabel> = emptyList(),
  val excludeReason: String? = null,
  /** The ATS score against the current resume, or `null` when not scored yet. */
  val ats: AtsSummary? = null,
  val createdAt: Long,
  val updatedAt: Long,
) {
  val applyMethod: ApplyMethod
    get() = ApplyMethod.fromTags(tags)

  val starred: Boolean
    get() = JobLabel.STARRED in labels

  /** [labels] with the star added or taken away. */
  fun labelsStarred(star: Boolean): List<JobLabel> =
    if (star) (labels - JobLabel.STARRED) + JobLabel.STARRED else labels - JobLabel.STARRED
}
