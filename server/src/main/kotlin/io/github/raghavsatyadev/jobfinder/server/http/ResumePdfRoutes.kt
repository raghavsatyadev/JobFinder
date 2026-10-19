package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.model.MakeResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `GET /resume/pdf/status`, `PUT /resume/pdf/prefs`, `POST /resume/pdf` with [MakeResumePdf], and
 * `PUT|DELETE /resume/linkedin` (PUT is multipart; the first file part is the export ZIP).
 */
fun Route.resumePdfRoutes(deps: ApiDependencies) {
  fun pdfs(): ResumePdfRepository = deps.resumePdf ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
  get("/resume/pdf/status") { call.respondOk(pdfs().status()) }
  put("/resume/pdf/prefs") { call.respondOk(pdfs().savePrefs(call.receive<ResumePdfPrefs>())) }
  post("/resume/pdf") { call.respondOk(pdfs().makePdf(call.receive<MakeResumePdf>())) }
  put("/resume/linkedin") {
    val max = ResumePdfRepository.MAX_EXPORT_BYTES
    var upload: Pair<String, ByteArray>? = null
    call.receiveMultipart(formFieldLimit = max + 1L).forEachPart { part ->
      try {
        if (part is PartData.FileItem && upload == null) {
          val name = part.originalFileName ?: badRequest("The file has no name.")
          val bytes =
            withContext(Dispatchers.IO) {
              part.provider().toInputStream().use { it.readNBytes(max + 1) }
            }
          upload = name to bytes
        }
      } finally {
        part.dispose()
      }
    }
    val (name, bytes) = upload ?: badRequest("Attach the LinkedIn export ZIP.")
    call.respondOk(pdfs().importLinkedIn(name, bytes))
  }
  delete("/resume/linkedin") { call.respondOk(pdfs().deleteLinkedIn()) }
}
