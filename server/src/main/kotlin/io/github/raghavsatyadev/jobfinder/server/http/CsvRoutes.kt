package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.shared.api.JobQueryParams
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.withCharset
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** `GET /jobs/export.csv[?status=applied]`: every job, or the jobs in one status, as CSV. */
fun Route.csvRoutes(deps: ApiDependencies) {
  get("/jobs/export.csv") {
    val status = call.request.queryParameters[JobQueryParams.STATUS]?.let(::parseStatus)
    val csv = if (status == null) deps.csv.export() else deps.csv.export(listOf(status))
    call.response.header(
      HttpHeaders.ContentDisposition,
      ContentDisposition.Attachment.withParameter(
          ContentDisposition.Parameters.FileName,
          "jobfinder-${status?.wire ?: "jobs"}.csv",
        )
        .toString(),
    )
    call.respondText(csv, ContentType.Text.CSV.withCharset(Charsets.UTF_8))
  }
}
