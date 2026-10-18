package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `GET|PUT|DELETE /resume` (PUT is multipart; the first file part is read) and `POST
 * /jobs/{id}/ats`, which queues the job's review and returns where it stands.
 */
fun Route.atsRoutes(deps: ApiDependencies) {
  fun ats(): AtsRepository = deps.ats ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
  route("/resume") {
    get { call.respondOk(ats().getResume() ?: notFound("Resume")) }
    put {
      var upload: Pair<String, ByteArray>? = null
      call.receiveMultipart(formFieldLimit = AtsRepository.MAX_RESUME_BYTES + 1L).forEachPart { part
        ->
        try {
          if (part is PartData.FileItem && upload == null) {
            val name = part.originalFileName ?: badRequest("The file has no name.")
            val bytes =
              withContext(Dispatchers.IO) {
                part.provider().toInputStream().use {
                  it.readNBytes(AtsRepository.MAX_RESUME_BYTES + 1)
                }
              }
            upload = name to bytes
          }
        } finally {
          part.dispose()
        }
      }
      val (name, bytes) = upload ?: badRequest("Attach the resume file.")
      call.respondOk(ats().uploadResume(name, bytes))
    }
    delete {
      ats().deleteResume()
      call.respondOk(Unit)
    }
  }
  post("/jobs/{id}/ats") { call.respondOk(ats().requestReview(call.idParam())) }
}

/** `POST /jobs/{id}/tips[?write=true]`: the negotiation tips, or new ones queued. */
fun Route.tipsRoutes(deps: ApiDependencies) {
  post("/jobs/{id}/tips") {
    val tips = deps.tips ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
    val write = call.request.queryParameters["write"] == "true"
    call.respondOk(tips.requestTips(call.idParam(), write))
  }
}

/** `POST /jobs/{id}/ghost[?retry=true]`: the ghost-job check, or its queued careers-site read. */
fun Route.ghostRoutes(deps: ApiDependencies) {
  post("/jobs/{id}/ghost") {
    val ghost = deps.ghost ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
    val retry = call.request.queryParameters["retry"] == "true"
    call.respondOk(ghost.requestGhostCheck(call.idParam(), retry))
  }
}

/** `POST /jobs/{id}/insights[?retry=true]`: the company's ratings and salary, or a queued read. */
fun Route.insightsRoutes(deps: ApiDependencies) {
  post("/jobs/{id}/insights") {
    val insights = deps.insights ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
    val retry = call.request.queryParameters["retry"] == "true"
    call.respondOk(insights.requestInsights(call.idParam(), retry))
  }
}
