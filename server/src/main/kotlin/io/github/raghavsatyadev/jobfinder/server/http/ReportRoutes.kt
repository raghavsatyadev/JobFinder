package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.model.JobReport
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** `POST /jobs/{id}/report`: the AI checks again a job the user says passed the filter wrongly. */
fun Route.reportRoutes(deps: ApiDependencies) {
  post("/jobs/{id}/report") {
    val reports = deps.reports ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
    val note = call.receive<JobReport>().note
    if (note != null && note.length > JobReport.MAX_NOTE) {
      badRequest("The note can be up to ${JobReport.MAX_NOTE} characters.")
    }
    call.respondOk(reports.reportJob(call.idParam(), note))
  }
}
