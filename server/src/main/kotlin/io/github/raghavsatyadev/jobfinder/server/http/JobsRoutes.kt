package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ChangeAllStatusesRequest
import io.github.raghavsatyadev.jobfinder.shared.api.CountResponse
import io.github.raghavsatyadev.jobfinder.shared.api.JobQueryParams
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateJobLabelsRequest
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateJobStatusRequest
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * `GET /jobs?status=new` with optional `limit`, `after`, `search` and repeated `site`, `link`,
 * `label`, `workMode`, `applyMethod`; `GET /jobs/counts`; `GET /jobs/{id}`; `PUT
 * /jobs/{id}/status|labels`; `POST /jobs/change-all-statuses`.
 */
fun Route.jobsRoutes(deps: ApiDependencies) {
  val jobs = deps.jobs
  route("/jobs") {
    get {
      val params = call.request.queryParameters
      val status =
        params[JobQueryParams.STATUS]?.let(::parseStatus) ?: badRequest("status is required.")
      val after = params[JobQueryParams.AFTER]?.takeIf { it.isNotEmpty() }
      call.respondOk(jobs.listJobs(status, params.jobFilter(), params.limit(), after))
    }
    get("/counts") {
      val params = call.request.queryParameters
      val status = params[JobQueryParams.STATUS]?.let(::parseStatus)
      call.respondOk(jobs.countJobs(params.jobFilter(), status))
    }
    post("/change-all-statuses") {
      val body = call.receive<ChangeAllStatusesRequest>()
      call.respondOk(CountResponse(jobs.changeAllStatuses(body.from, body.to)))
    }
    route("/{id}") {
      get { call.respondOk(jobs.getJob(call.idParam()) ?: notFound("Job")) }
      put("/status") {
        val id = call.idParam()
        val body = call.receive<UpdateJobStatusRequest>()
        jobs.getJob(id) ?: notFound("Job")
        jobs.updateStatus(id, body.status)
        call.respondOk(jobs.getJob(id) ?: notFound("Job"))
      }
      put("/labels") {
        val id = call.idParam()
        val body = call.receive<UpdateJobLabelsRequest>()
        call.respondOk(jobs.updateLabels(id, body.labels) ?: notFound("Job"))
      }
    }
  }
}
