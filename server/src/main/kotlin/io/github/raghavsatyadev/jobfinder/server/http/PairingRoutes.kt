package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestBody
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestCreated
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlin.time.Duration.Companion.seconds

/**
 * `POST /pair/requests`, `GET /pair/requests/{id}?wait=<seconds>` and `DELETE /pair/requests/{id}`
 * (the phone gave up), open without a token: pairing by approval on the desktop
 * ([PairingRequests]).
 */
fun Route.pairingRoutes(requests: PairingRequests) {
  route("/pair/requests") {
    post {
      val body = call.receive<PairingRequestBody>()
      call.respondOk(PairingRequestCreated(requests.open(body.deviceName, body.code)))
    }
    get("/{id}") {
      val id = call.parameters["id"].orEmpty().take(ID_LENGTH)
      val wait = (call.request.queryParameters["wait"]?.toIntOrNull() ?: 0).coerceIn(0, MAX_WAIT_S)
      call.respondOk(requests.await(id, wait.seconds))
    }
    delete("/{id}") {
      requests.cancel(call.parameters["id"].orEmpty().take(ID_LENGTH))
      call.respondOk(Unit)
    }
  }
}

private const val ID_LENGTH = 36
private const val MAX_WAIT_S = 25
