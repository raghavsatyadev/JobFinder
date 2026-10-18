package io.github.raghavsatyadev.jobfinder.server.http

import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** `GET /sites`. */
fun Route.sitesRoutes(deps: ApiDependencies) {
  get("/sites") { call.respondOk(deps.sites.listSites()) }
}
