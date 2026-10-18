package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/**
 * `GET|PUT /settings/scanner`, `GET|PUT /settings/server` and `GET|PUT /settings/detail-layout`.
 * The pairing token is not exposed; it is rotated from the desktop app only.
 */
fun Route.settingsRoutes(deps: ApiDependencies) {
  route("/settings") {
    get("/scanner") { call.respondOk(deps.settings.getScannerSettings()) }
    put("/scanner") {
      val body = call.receive<ScannerSettings>()
      if (body.scraperConcurrency !in ScannerSettings.SCRAPER_CONCURRENCY_RANGE) {
        badRequest("scraperConcurrency must be in ${ScannerSettings.SCRAPER_CONCURRENCY_RANGE}.")
      }
      call.respondOk(deps.settings.updateScannerSettings(body))
    }
    get("/server") { call.respondOk(deps.settings.getServerSettings()) }
    put("/server") {
      val body = call.receive<ServerSettings>()
      if (body.port !in LocalSettingsRepository.PORT_RANGE) {
        badRequest("port must be in ${LocalSettingsRepository.PORT_RANGE}.")
      }
      call.respondOk(deps.settings.updateServerSettings(body))
    }
    get("/detail-layout") { call.respondOk(deps.settings.getDetailLayout()) }
    put("/detail-layout") {
      call.respondOk(deps.settings.updateDetailLayout(call.receive<DetailLayout>()))
    }
  }
}
