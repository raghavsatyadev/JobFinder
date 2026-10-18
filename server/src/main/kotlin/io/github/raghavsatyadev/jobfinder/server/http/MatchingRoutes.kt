package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** `GET|PUT /matching`: the advanced-matching prompt and blacklisted companies. */
fun Route.matchingRoutes(deps: ApiDependencies) {
  route("/matching") {
    get { call.respondOk(deps.matching.getConfig()) }
    put {
      val body = call.receive<AdvancedMatchingConfig>()
      if (body.prompt.length > AdvancedMatchingConfig.MAX_PROMPT_LENGTH) {
        badRequest(
          "The prompt can be up to ${AdvancedMatchingConfig.MAX_PROMPT_LENGTH} characters."
        )
      }
      call.respondOk(deps.matching.updateConfig(body))
    }
  }
}
