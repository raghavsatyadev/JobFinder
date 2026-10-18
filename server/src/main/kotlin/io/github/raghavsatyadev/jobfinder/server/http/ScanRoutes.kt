package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.scan.UrgentRead
import io.github.raghavsatyadev.jobfinder.shared.api.ScanStartResponse
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.withContext

/**
 * `GET /scan/state`, `POST /scan` (all links, in the background), and `POST /scan/links/{id}` and
 * `POST /scan/jobs/{id}`, which wait for the result.
 */
fun Route.scanRoutes(deps: ApiDependencies) {
  val scanner = deps.scanner
  route("/scan") {
    get("/state") { call.respondOk(scanner.state.value) }
    post {
      val started = scanner.startScanAll()
      call.respondOk(ScanStartResponse(started, scanner.state.value))
    }
    post("/links/{id}") { call.respondOk(scanner.scanLink(call.idParam())) }
    // A job the user has open: its page does not wait behind the background readers.
    post("/jobs/{id}") {
      call.respondOk(withContext(UrgentRead) { scanner.scanJob(call.idParam()) })
    }
  }
}
