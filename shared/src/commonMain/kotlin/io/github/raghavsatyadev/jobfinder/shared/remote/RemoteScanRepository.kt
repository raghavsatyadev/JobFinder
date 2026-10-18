package io.github.raghavsatyadev.jobfinder.shared.remote

import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.ktor.client.plugins.sse.sse
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

/**
 * Scans on the paired desktop, with its live events from `GET /v1/events` (server-sent events).
 * While [endpoints] holds a desktop, one event stream stays open and reconnects after
 * [reconnectDelay] when it drops; [connected] says whether it is open now. The desktop sends its
 * scan state and broken links on every connect, so [state] is fresh after a reconnect.
 */
class RemoteScanRepository(
  private val api: RemoteApi,
  private val links: RemoteLinksRepository,
  scope: CoroutineScope,
  endpoints: Flow<RemoteEndpoint?>,
  private val reconnectDelay: Duration = 5.seconds,
) : ScanRepository {
  private val _state = MutableStateFlow(ScanState())
  private val _events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = EVENT_BUFFER)
  private val _connected = MutableStateFlow(false)

  override val state: StateFlow<ScanState> = _state.asStateFlow()
  override val events: SharedFlow<ServerEvent> = _events.asSharedFlow()
  val connected: StateFlow<Boolean> = _connected.asStateFlow()

  init {
    scope.launch {
      endpoints.collectLatest { endpoint ->
        _connected.value = false
        if (endpoint != null) listen(endpoint)
      }
    }
  }

  private suspend fun listen(endpoint: RemoteEndpoint) {
    while (true) {
      try {
        api.http.sse(
          urlString = "${endpoint.baseUrl.trimEnd('/')}/v1/events",
          request = {
            bearerAuth(endpoint.token)
            // The stream stays open; the desktop's 20 s heartbeat keeps the socket busy.
            timeout {
              requestTimeoutMillis = Long.MAX_VALUE
              socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
          },
        ) {
          _connected.value = true
          incoming.collect { message -> message.data?.let { publish(it) } }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // Desktop off, asleep or unreachable: try again shortly.
      }
      _connected.value = false
      delay(reconnectDelay)
    }
  }

  private suspend fun publish(data: String) {
    val event =
      try {
        ApiJson.decodeFromString(ServerEvent.serializer(), data)
      } catch (_: SerializationException) {
        return
      } catch (_: IllegalArgumentException) {
        return
      }
    if (event is ServerEvent.ScanStateChanged) _state.value = event.state
    _events.emit(event)
  }

  override suspend fun startScanAll(): Boolean {
    val response = api.startScan()
    _state.value = response.state
    return response.started
  }

  override suspend fun scanLink(linkId: Long): List<Long> = api.scanLink(linkId)

  override suspend fun scanJob(jobId: Long): Job = api.scanJob(jobId)

  override suspend fun createLink(
    title: String,
    url: String,
    force: Boolean,
    siteName: String?,
  ): CreateLinkResponse = links.createLink(title, url, force, siteName)

  override suspend fun brokenLinks(): List<Link> = links.brokenLinks()

  private companion object {
    const val EVENT_BUFFER = 64
    const val SOCKET_TIMEOUT_MS = 60_000L
  }
}
