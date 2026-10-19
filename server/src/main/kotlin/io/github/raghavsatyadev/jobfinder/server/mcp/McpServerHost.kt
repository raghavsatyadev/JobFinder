package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.server.ai.SecretStore
import io.github.raghavsatyadev.jobfinder.server.http.PairingAuthProvider
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Where agents reach the MCP server, or why they can't. */
data class McpEndpoint(val url: String?, val portInUse: Boolean = false)

/**
 * Serves [McpServer] at `http://127.0.0.1:<port>/mcp` for AI agents on this computer: never on the
 * network, and every request needs the agent token (`Authorization: Bearer`), which is kept in the
 * protected store and differs from the phones' pairing token. Requests from a web page (an `Origin`
 * other than localhost) are refused, so a site can't reach it through the browser.
 */
class McpServerHost(
  private val server: McpServer,
  private val secrets: SecretStore,
  private val port: Int = DEFAULT_PORT,
) {
  private val log = LoggerFactory.getLogger(McpServerHost::class.java)
  private var running:
    EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? =
    null
  private val _endpoint = MutableStateFlow(McpEndpoint(null))
  val endpoint: StateFlow<McpEndpoint> = _endpoint.asStateFlow()

  /** The agent token, made on first use. */
  fun token(): String = secrets.get(TOKEN_NAME) ?: newToken()

  /** Replaces the token; agents set up with the old one stop working. */
  fun rotateToken(): String = newToken()

  private fun newToken(): String {
    val bytes = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes)
    val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    secrets.put(TOKEN_NAME, token)
    return token
  }

  suspend fun start() {
    if (running != null) return
    if (!secrets.available) {
      log.warn("No protected store for the agent token; the MCP server stays off")
      return
    }
    token()
    try {
      running =
        withContext(Dispatchers.IO) {
          embeddedServer(
              Netty,
              configure = {
                connector {
                  host = HOST
                  port = this@McpServerHost.port
                }
              },
              module = { mcpModule() },
            )
            .start(wait = false)
        }
      val url = "http://$HOST:$port$PATH"
      _endpoint.value = McpEndpoint(url)
      log.info("MCP server for AI agents on {}", url)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error("Could not start the MCP server on {}:{}", HOST, port, e)
      _endpoint.value = McpEndpoint(null, portInUse = true)
    }
  }

  fun stop() {
    running?.stop(GRACE_MILLIS, TIMEOUT_MILLIS)
    running = null
    _endpoint.value = McpEndpoint(null)
  }

  private fun Application.mcpModule() {
    routing {
      post(PATH) {
        if (!allowed(call)) return@post
        val answer = server.handle(call.receiveText())
        if (answer == null) call.respond(HttpStatusCode.Accepted)
        else call.respondText(answer, ContentType.Application.Json)
      }
      // No server-to-agent stream and no sessions.
      get(PATH) { call.respond(HttpStatusCode.MethodNotAllowed) }
      delete(PATH) { call.respond(HttpStatusCode.MethodNotAllowed) }
    }
  }

  private suspend fun allowed(call: RoutingCall): Boolean {
    val origin = call.request.headers[HttpHeaders.Origin]
    if (origin != null && !LOCAL_ORIGIN.matches(origin)) {
      call.respond(HttpStatusCode.Forbidden)
      return false
    }
    val presented = PairingAuthProvider.bearerToken(call.request.headers[HttpHeaders.Authorization])
    if (presented == null || !PairingAuthProvider.constantTimeEquals(presented, token())) {
      call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"jobfinder-mcp\"")
      call.respond(HttpStatusCode.Unauthorized)
      return false
    }
    return true
  }

  companion object {
    const val DEFAULT_PORT = 47822
    const val HOST = "127.0.0.1"
    const val PATH = "/mcp"
    private const val TOKEN_NAME = "mcp-agent-token"
    private const val TOKEN_BYTES = 32
    private const val GRACE_MILLIS = 300L
    private const val TIMEOUT_MILLIS = 1_000L
    private val LOCAL_ORIGIN = Regex("""^https?://(localhost|127\.0\.0\.1|\[::1])(:\d+)?$""")
  }
}
