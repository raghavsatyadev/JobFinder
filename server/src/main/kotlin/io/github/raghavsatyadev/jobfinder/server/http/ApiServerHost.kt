package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.shared.api.PairingPayload
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import java.net.InetAddress
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Where the API server listens. */
data class ServerEndpoint(val host: String, val port: Int) {
  val baseUrl: String
    get() = "http://$host:$port"

  /** The text of the pairing QR code. */
  fun pairingCode(token: String): String = PairingPayload(baseUrl, token).encode()
}

/**
 * Runs the API server (Netty) on the LAN address only, never on all interfaces. The address is
 * checked every [recheckEvery]; when it changes (new Wi-Fi, DHCP lease) the server is restarted on
 * the new one and [endpoint] changes, so the desktop shows a new QR code. [endpoint] is `null`
 * while there is no address or the port is taken; [portInUse] tells the two apart.
 */
class ApiServerHost(
  private val serverSettings: suspend () -> ServerSettings,
  private val module: Application.() -> Unit,
  private val pickAddress: (interfaceName: String?) -> InetAddress? = LanAddress::pick,
  private val recheckEvery: Duration = 45.seconds,
) {
  private val log = LoggerFactory.getLogger(ApiServerHost::class.java)
  private val mutex = Mutex()
  private var server:
    EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? =
    null
  private val _endpoint = MutableStateFlow<ServerEndpoint?>(null)

  val endpoint: StateFlow<ServerEndpoint?> = _endpoint.asStateFlow()
  private val _portInUse = MutableStateFlow<Int?>(null)

  /** The port the server could not listen on (another program has it), else `null`. */
  val portInUse: StateFlow<Int?> = _portInUse.asStateFlow()

  /** Starts the server and the address watch in [scope]. Cancel the job, then call [stop]. */
  fun start(scope: CoroutineScope): Job =
    scope.launch(Dispatchers.IO) {
      while (true) {
        try {
          refresh()
        } catch (e: Exception) {
          if (e is CancellationException) throw e
          log.error("API server refresh failed", e)
        }
        delay(recheckEvery)
      }
    }

  /** Rebinds now if the address or the port setting changed. Returns the current endpoint. */
  suspend fun refresh(): ServerEndpoint? = mutex.withLock {
    val settings = serverSettings()
    val wanted = pickAddress(settings.networkInterface)?.hostAddress
    val current = _endpoint.value
    if (wanted == current?.host && settings.port == current?.port && server != null) {
      return current
    }
    stopLocked()
    _portInUse.value = null
    if (wanted == null) {
      log.warn("No LAN address; the API server is off until one appears")
      return null
    }
    val started =
      try {
        withContext(Dispatchers.IO) {
          embeddedServer(
              Netty,
              configure = {
                connector {
                  host = wanted
                  port = settings.port
                }
              },
              module = module,
            )
            .start(wait = false)
        }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.error("Could not start the API server on {}:{}", wanted, settings.port, e)
        _portInUse.value = settings.port
        return null
      }
    server = started
    val port = started.engine.resolvedConnectors().firstOrNull()?.port ?: settings.port
    ServerEndpoint(wanted, port).also {
      _endpoint.value = it
      log.info("API server listening on {}", it.baseUrl)
    }
  }

  /** Stops the server. */
  suspend fun stop() {
    mutex.withLock { stopLocked() }
  }

  private suspend fun stopLocked() {
    val running = server ?: return
    server = null
    _endpoint.value = null
    withContext(Dispatchers.IO) { running.stop(GRACE_MILLIS, TIMEOUT_MILLIS) }
  }

  companion object {
    /** The host for the app's API ([jobFinderApi]) with the port and interface from settings. */
    fun forApi(deps: ApiDependencies): ApiServerHost =
      ApiServerHost(
        serverSettings = { deps.settings.getServerSettings() },
        module = { jobFinderApi(deps) },
      )

    private const val GRACE_MILLIS = 500L
    private const val TIMEOUT_MILLIS = 2_000L
  }
}
