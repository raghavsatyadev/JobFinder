package io.github.raghavsatyadev.jobfinder.desktop.server

import io.github.raghavsatyadev.jobfinder.server.http.ApiServerHost
import io.github.raghavsatyadev.jobfinder.server.http.ServerEndpoint
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostLink
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Runs [ApiServerHost] for the life of the app and logs each new address with its pairing code. The
 * pairing QR in the UI reads the same [ApiServerHost.endpoint]; [announcer] lets phones find it.
 * [nativeHost] is the browser extension's link to the app.
 */
class EmbeddedServer(
  private val host: ApiServerHost,
  private val settings: LocalSettingsRepository,
  private val announcer: LanAnnouncer? = null,
  private val nativeHost: NativeHostLink? = null,
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(EmbeddedServer::class.java)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun start() {
    host.endpoint
      .filterNotNull()
      .onEach { log.info("Phones pair with {} (pairing code in the app)", it.baseUrl) }
      .launchIn(scope)
    announcer?.start(scope)
    host.start(scope)
    nativeHost?.start(scope)
  }

  /** The pairing QR text for [endpoint]. */
  suspend fun pairingCode(endpoint: ServerEndpoint): String =
    endpoint.pairingCode(settings.pairingToken())

  override fun close() {
    nativeHost?.close()
    scope.cancel()
    announcer?.close()
    runBlocking { host.stop() }
  }
}
