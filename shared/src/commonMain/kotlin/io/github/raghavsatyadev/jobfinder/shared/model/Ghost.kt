package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A careers-site host whose public job list the app can read. */
@Serializable
enum class CareersHost(val label: String) {
  @SerialName("greenhouse") GREENHOUSE("Greenhouse"),
  @SerialName("lever") LEVER("Lever"),
  @SerialName("ashby") ASHBY("Ashby"),
  @SerialName("smartrecruiters") SMARTRECRUITERS("SmartRecruiters"),
  @SerialName("workable") WORKABLE("Workable"),
  @SerialName("recruitee") RECRUITEE("Recruitee"),
}

@Serializable
enum class CareersResult {
  /** The company's own careers site lists this job. */
  @SerialName("found") FOUND,
  /** The careers site was read and this job is not on it. */
  @SerialName("not_found") NOT_FOUND,
  /** No careers site the app can read was found for the company. */
  @SerialName("unknown") UNKNOWN,
}

/** Whether the company's own careers site lists the job. */
@Serializable
data class CareersCheck(
  val result: CareersResult,
  val host: CareersHost? = null,
  /** The company's careers page on [host]. */
  val boardUrl: String? = null,
  /** The job's own page there, when [result] is [CareersResult.FOUND]. */
  val jobUrl: String? = null,
  /** The careers site's title for the job, when it differs from the board's. */
  val matchedTitle: String? = null,
  /** How many open jobs the careers site lists. */
  val openJobs: Int? = null,
  val checkedAt: Long,
)

/** One reason a posting may be a ghost job: old, reposted, or not on the company's own site. */
@Serializable
sealed interface GhostSignal {
  /** The same title from the same company was found [times] times since [firstSeen]. */
  @Serializable
  @SerialName("reposted")
  data class Reposted(val times: Int, val firstSeen: Long) : GhostSignal

  /** First found [days] days ago. */
  @Serializable @SerialName("old") data class OldPosting(val days: Int) : GhostSignal

  @Serializable @SerialName("no_salary") data object NoSalary : GhostSignal

  /** A short or generic description. */
  @Serializable @SerialName("vague") data object VagueText : GhostSignal

  @Serializable @SerialName("not_on_careers") data object NotOnCareersSite : GhostSignal

  /** None of the company's last [jobs] checked postings is on its own careers site. */
  @Serializable
  @SerialName("company_not_hiring")
  data class CompanyNeverOnOwnSite(val jobs: Int) : GhostSignal
}

/** The ghost-job check of one job. */
@Serializable
data class GhostCheck(
  val signals: List<GhostSignal> = emptyList(),
  /** `null` until the careers site is read. */
  val careers: CareersCheck? = null,
) {
  /**
   * Not on the company's own site counts most; a missing salary least, since most boards leave it
   * out. Two points make a possible ghost job.
   */
  val score: Double
    get() = signals.sumOf {
      when (it) {
        GhostSignal.NotOnCareersSite,
        is GhostSignal.CompanyNeverOnOwnSite -> 2.0
        is GhostSignal.Reposted -> if (it.times >= STRONG_REPOSTS) 2.0 else 1.0
        is GhostSignal.OldPosting,
        GhostSignal.VagueText -> 1.0
        GhostSignal.NoSalary -> 0.5
      }
    }

  val possibleGhost: Boolean
    get() = score >= GHOST_SCORE

  companion object {
    const val STRONG_REPOSTS = 3
    const val GHOST_SCORE = 2.0
  }
}

/** [GhostCheck] with where its careers-site read stands; the same states as insights. */
@Serializable data class GhostStatus(val state: InsightsState, val check: GhostCheck? = null)
