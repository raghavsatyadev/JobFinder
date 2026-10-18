package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Job board that a site row belongs to. [wire] matches the old `sites.provider` values. */
@Serializable
enum class SiteProvider(val wire: String) {
  @SerialName("linkedin") LINKEDIN("linkedin"),
  @SerialName("glassdoor") GLASSDOOR("glassdoor"),
  @SerialName("indeed") INDEED("indeed"),
  @SerialName("remoteok") REMOTEOK("remoteok"),
  @SerialName("weworkremotely") WEWORKREMOTELY("weworkremotely"),
  @SerialName("dice") DICE("dice"),
  @SerialName("flexjobs") FLEXJOBS("flexjobs"),
  @SerialName("bestjobs") BESTJOBS("bestjobs"),
  @SerialName("echojobs") ECHOJOBS("echojobs"),
  @SerialName("remotive") REMOTIVE("remotive"),
  @SerialName("remoteio") REMOTEIO("remoteio"),
  @SerialName("builtin") BUILTIN("builtin"),
  @SerialName("naukri") NAUKRI("naukri"),
  @SerialName("robertHalf") ROBERT_HALF("robertHalf"),
  @SerialName("zipRecruiter") ZIP_RECRUITER("zipRecruiter"),
  @SerialName("usaJobs") USA_JOBS("usaJobs"),
  @SerialName("talent") TALENT("talent"),
  @SerialName("hiringCafe") HIRING_CAFE("hiringCafe"),
  /** Any site not in the list above; scanned with the LLM parser. */
  @SerialName("custom") CUSTOM("custom");

  companion object {
    /** Unknown providers fall back to [CUSTOM]. */
    fun fromWire(value: String): SiteProvider = entries.firstOrNull { it.wire == value } ?: CUSTOM
  }
}
