package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.ktor.server.routing.Route
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlin.time.Duration.Companion.seconds

/**
 * `GET /events`: server-sent [ServerEvent]s. The scan state and the broken links are sent on
 * connect, then each event as it happens. A heartbeat comment keeps idle connections open through
 * NAT and Wi-Fi power saving.
 */
fun Route.eventsRoutes(deps: ApiDependencies) {
  sse("/events") {
    heartbeat {
      period = HEARTBEAT
      event = ServerSentEvent(comments = "heartbeat")
    }
    suspend fun sendEvent(event: ServerEvent) {
      send(
        ServerSentEvent(
          data = ApiJson.encodeToString(ServerEvent.serializer(), event),
          event = event.eventName,
        )
      )
    }
    sendEvent(ServerEvent.ScanStateChanged(deps.scanner.state.value))
    sendEvent(ServerEvent.BrokenLinks(deps.linkService.brokenLinks().map { it.id }))
    deps.events.events.collect { sendEvent(it) }
  }
}

private val HEARTBEAT = 20.seconds
