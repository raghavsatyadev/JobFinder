package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkRequest
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateLinkRequest
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** `GET|POST /links`, `GET /links/broken`, `GET|PUT|DELETE /links/{id}`. */
fun Route.linksRoutes(deps: ApiDependencies) {
  val links = deps.links
  route("/links") {
    get { call.respondOk(links.listLinks()) }
    post {
      val body = call.receive<CreateLinkRequest>()
      call.respondOk(
        deps.linkService.createLink(body.title, body.url, body.force, siteName = body.siteName)
      )
    }
    get("/broken") { call.respondOk(deps.linkService.brokenLinks()) }
    route("/{id}") {
      get { call.respondOk(links.getLink(call.idParam()) ?: notFound("Link")) }
      put {
        val id = call.idParam()
        val body = call.receive<UpdateLinkRequest>()
        if (body.title.isBlank() || body.url.isBlank()) badRequest("Title and URL are required.")
        call.respondOk(links.updateLink(id, body.title.trim(), body.url.trim()) ?: notFound("Link"))
      }
      delete {
        val id = call.idParam()
        links.getLink(id) ?: notFound("Link")
        links.deleteLink(id)
        call.respondOk(Unit)
      }
    }
  }
}
