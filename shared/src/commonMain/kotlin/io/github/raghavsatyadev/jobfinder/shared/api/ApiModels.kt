package io.github.raghavsatyadev.jobfinder.shared.api

import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The envelope of every `/v1` response. On success [data] is set; on failure [code] and a generic
 * [message] are, and the HTTP status matches the code.
 */
@Serializable
data class BaseResponse<T>(
  val success: Boolean,
  val data: T? = null,
  val code: ApiErrorCode? = null,
  val message: String? = null,
) {
  companion object {
    fun <T> ok(data: T): BaseResponse<T> = BaseResponse(success = true, data = data)

    fun error(code: ApiErrorCode, message: String = code.defaultMessage): BaseResponse<Unit> =
      BaseResponse(success = false, code = code, message = message)
  }
}

/** Error codes of [BaseResponse]; [httpStatus] is the status sent with them. */
@Serializable
enum class ApiErrorCode(val httpStatus: Int, val defaultMessage: String) {
  @SerialName("bad_request") BAD_REQUEST(400, "The request is not valid."),
  @SerialName("unauthorized") UNAUTHORIZED(401, "Pair this device with the desktop app again."),
  @SerialName("denied") DENIED(403, "The computer did not allow it."),
  @SerialName("not_found") NOT_FOUND(404, "Not found."),
  @SerialName("conflict") CONFLICT(409, "The request conflicts with the current state."),
  @SerialName("payload_too_large") PAYLOAD_TOO_LARGE(413, "The upload is too large."),
  @SerialName("unavailable") UNAVAILABLE(503, "This is not available right now."),
  @SerialName("internal") INTERNAL(500, "Something went wrong on the desktop app."),
}

/**
 * `GET /health`, open without a token. [id] stays the same for a computer across address and token
 * changes, so a phone can find its computer again; [name] is the computer's name.
 */
@Serializable
data class HealthResponse(
  val status: String = "ok",
  val app: String = "jobfinder",
  val id: String? = null,
  val name: String? = null,
)

/** How desktops announce themselves on the local network (mDNS / DNS-SD). */
object Discovery {
  /** The DNS-SD service type, without the `.local.` domain. */
  const val SERVICE_TYPE = "_jobfinder._tcp"

  /** TXT key holding [HealthResponse.id]. */
  const val TXT_ID = "id"
}

/**
 * `POST /pair/requests`, open without a token: a phone asks to pair. The desktop shows [deviceName]
 * and [code] (six digits the phone also shows) and the user allows or denies it.
 */
@Serializable data class PairingRequestBody(val deviceName: String, val code: String)

@Serializable data class PairingRequestCreated(val requestId: String)

@Serializable
enum class PairingRequestState {
  @SerialName("pending") PENDING,
  @SerialName("approved") APPROVED,
  @SerialName("denied") DENIED,
  @SerialName("expired") EXPIRED,
}

/**
 * `GET /pair/requests/{id}`: the answer so far. [token] is set once, on the first read after the
 * user allowed the request.
 */
@Serializable
data class PairingRequestStatus(val state: PairingRequestState, val token: String? = null)

@Serializable data class UpdateJobStatusRequest(val status: JobStatus)

@Serializable data class UpdateJobLabelsRequest(val labels: List<JobLabel>)

@Serializable data class ChangeAllStatusesRequest(val from: JobStatus, val to: JobStatus)

@Serializable data class CountResponse(val count: Int)

/**
 * `POST /v1/links`. The desktop scans [url] in a hidden browser to check it is a job list. When the
 * check fails the link is still saved if [force] is set (the old "save it anyway").
 */
@Serializable
data class CreateLinkRequest(
  val title: String,
  val url: String,
  val force: Boolean = false,
  /** The name of a custom board; `null` takes it from the address. */
  val siteName: String? = null,
)

@Serializable data class UpdateLinkRequest(val title: String, val url: String)

/** Result of creating a link: the link and the jobs found on its page. */
@Serializable
data class CreateLinkResponse(
  val link: Link,
  val newJobIds: List<Long>,
  /** No job list was recognised on the page; the link was saved because of `force`. */
  val parseFailed: Boolean,
)

@Serializable data class CreateNoteRequest(val text: String)

@Serializable data class UpdateNoteRequest(val text: String)

/**
 * Server-sent events on `GET /v1/events`. The SSE `event` field is [eventName]; `data` is this
 * object as JSON.
 */
@Serializable
sealed interface ServerEvent {
  val eventName: String

  /** A scan stored new jobs (status `new`, after advanced matching). */
  @Serializable
  @SerialName("new_jobs")
  data class NewJobs(val jobIds: List<Long>) : ServerEvent {
    override val eventName: String
      get() = NAME_NEW_JOBS
  }

  /** The scan state changed. Sent once on connect too. */
  @Serializable
  @SerialName("scan_state")
  data class ScanStateChanged(val state: ScanState) : ServerEvent {
    override val eventName: String
      get() = NAME_SCAN_STATE
  }

  /**
   * Links that failed [BROKEN_LINK_THRESHOLD] or more scans in a row (the old post-scan-hook email,
   * now an in-app warning). Empty when every link is fine.
   */
  @Serializable
  @SerialName("broken_links")
  data class BrokenLinks(val linkIds: List<Long>) : ServerEvent {
    override val eventName: String
      get() = NAME_BROKEN_LINKS
  }

  /** A job's ATS review finished; [summary] is `null` when it failed. */
  @Serializable
  @SerialName("ats_scored")
  data class AtsScored(val jobId: Long, val summary: AtsSummary? = null) : ServerEvent {
    override val eventName: String
      get() = NAME_ATS_SCORED
  }

  /** The resume was uploaded, replaced or removed, so every ATS score starts again. */
  @Serializable
  @SerialName("resume_changed")
  data object ResumeChanged : ServerEvent {
    override val eventName: String
      get() = NAME_RESUME_CHANGED
  }

  /** Ratings and salary for these jobs' company were read, or could not be. */
  @Serializable
  @SerialName("insights_ready")
  data class InsightsReady(val jobIds: List<Long>) : ServerEvent {
    override val eventName: String
      get() = NAME_INSIGHTS_READY
  }

  /** The ghost-job check of these jobs finished reading the company's careers site. */
  @Serializable
  @SerialName("ghost_checked")
  data class GhostChecked(val jobIds: List<Long>) : ServerEvent {
    override val eventName: String
      get() = NAME_GHOST_CHECKED
  }

  /** Negotiation tips for this job were written, or could not be. */
  @Serializable
  @SerialName("tips_ready")
  data class TipsReady(val jobId: Long) : ServerEvent {
    override val eventName: String
      get() = NAME_TIPS_READY
  }

  /** A resume fix plan for this job was written, or could not be. */
  @Serializable
  @SerialName("improvement_ready")
  data class ImprovementReady(val jobId: Long) : ServerEvent {
    override val eventName: String
      get() = NAME_IMPROVEMENT_READY
  }

  /** The job detail's section order changed; open job details read it again. */
  @Serializable
  @SerialName("detail_layout_changed")
  data object DetailLayoutChanged : ServerEvent {
    override val eventName: String
      get() = NAME_DETAIL_LAYOUT_CHANGED
  }

  /**
   * The phone only, never from the computer: something the phone showed from its copy was read
   * again and had changed, so open screens read it again.
   */
  @Serializable
  @SerialName("copy_refreshed")
  data object CopyRefreshed : ServerEvent {
    override val eventName: String
      get() = NAME_COPY_REFRESHED
  }

  companion object {
    const val NAME_COPY_REFRESHED = "copy_refreshed"
    const val NAME_NEW_JOBS = "new_jobs"
    const val NAME_SCAN_STATE = "scan_state"
    const val NAME_BROKEN_LINKS = "broken_links"
    const val NAME_ATS_SCORED = "ats_scored"
    const val NAME_RESUME_CHANGED = "resume_changed"
    const val NAME_INSIGHTS_READY = "insights_ready"
    const val NAME_GHOST_CHECKED = "ghost_checked"
    const val NAME_TIPS_READY = "tips_ready"
    const val NAME_IMPROVEMENT_READY = "improvement_ready"
    const val NAME_DETAIL_LAYOUT_CHANGED = "detail_layout_changed"

    /** The old post-scan-hook `failureThreshold`. */
    const val BROKEN_LINK_THRESHOLD = 3
  }
}

/** Query parameter names of the job list and count routes. */
object JobQueryParams {
  const val STATUS = "status"
  const val LIMIT = "limit"
  const val AFTER = "after"
  const val SEARCH = "search"
  /** Repeated: `site=1&site=2`. The same for the other list filters. */
  const val SITE = "site"
  const val LINK = "link"
  const val LABEL = "label"
  const val WORK_MODE = "workMode"
  const val APPLY_METHOD = "applyMethod"
  /** `JobSort.wire`; latest first when absent. */
  const val SORT = "sort"

  /** Largest page a client may ask for. */
  const val MAX_LIMIT = 500
}

/** `POST /v1/scan`: [started] is `false` when a scan was already running. */
@Serializable data class ScanStartResponse(val started: Boolean, val state: ScanState)
