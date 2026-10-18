package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.JobQueryParams
import io.github.raghavsatyadev.jobfinder.shared.model.ApplyMethod
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.ktor.http.Parameters
import io.ktor.server.application.ApplicationCall

internal fun badRequest(message: String): Nothing =
  throw ApiException(ApiErrorCode.BAD_REQUEST, message)

/** The path parameter [name] as an id. */
internal fun ApplicationCall.idParam(name: String = "id"): Long =
  parameters[name]?.toLongOrNull()?.takeIf { it > 0 } ?: badRequest("Invalid $name.")

internal fun parseStatus(value: String): JobStatus =
  JobStatus.entries.firstOrNull { it.wire == value } ?: badRequest("Unknown status: $value")

/** The job filters of the list, count and export routes; list filters are repeated parameters. */
internal fun Parameters.jobFilter(): JobFilter =
  JobFilter(
    search = this[JobQueryParams.SEARCH]?.takeIf { it.isNotBlank() },
    siteIds = ids(JobQueryParams.SITE),
    linkIds = ids(JobQueryParams.LINK),
    labels =
      getAll(JobQueryParams.LABEL).orEmpty().map {
        JobLabel.fromWireOrNull(it) ?: badRequest("Unknown label: $it")
      },
    workModes =
      getAll(JobQueryParams.WORK_MODE).orEmpty().map {
        WorkMode.fromWireOrNull(it) ?: badRequest("Unknown work mode: $it")
      },
    applyMethods =
      getAll(JobQueryParams.APPLY_METHOD).orEmpty().map { value ->
        ApplyMethod.entries.firstOrNull { it.wire == value }
          ?: badRequest("Unknown apply method: $value")
      },
    sort =
      this[JobQueryParams.SORT]?.let {
        JobSort.fromWireOrNull(it) ?: badRequest("Unknown sort: $it")
      } ?: JobSort.LATEST,
    starred = this[JobQueryParams.STARRED] == "true",
  )

internal fun Parameters.limit(): Int {
  val raw = this[JobQueryParams.LIMIT] ?: return JobsRepository.DEFAULT_PAGE_SIZE
  return raw.toIntOrNull()?.takeIf { it in 1..JobQueryParams.MAX_LIMIT }
    ?: badRequest("limit must be 1 to ${JobQueryParams.MAX_LIMIT}.")
}

private fun Parameters.ids(name: String): List<Long> =
  getAll(name).orEmpty().map { it.toLongOrNull() ?: badRequest("Invalid $name: $it") }
