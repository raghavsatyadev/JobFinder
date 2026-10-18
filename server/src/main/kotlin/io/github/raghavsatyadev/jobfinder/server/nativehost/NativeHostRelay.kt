package io.github.raghavsatyadev.jobfinder.server.nativehost

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The native host process the browser starts: relays framed messages between the extension
 * ([stdin]/[stdout]) and the running app ([NativeHostLink], found through [connectionFile]). While
 * the app is not running it answers `hello` with `appRunning: false` itself, keeps retrying, and
 * sends `status` when it gains or loses the app. Returns when the extension closes the port.
 *
 * Nothing but frames may reach [stdout], and this class never logs (a logger could print there).
 */
class NativeHostRelay(
  private val connectionFile: Path,
  private val hostVersion: String,
  private val stdin: InputStream,
  stdout: OutputStream,
  private val logError: (String) -> Unit,
) {
  private val stdout = BufferedOutputStream(stdout)
  private val connectLock = Any()
  @Volatile private var app: AppConnection? = null
  @Volatile private var stopped = false

  fun run() {
    connect()
    Thread(::reconnectLoop, "native-host-reconnect").apply { isDaemon = true }.start()
    try {
      while (true) {
        val frame = NativeFraming.read(stdin) ?: break
        onExtensionFrame(frame)
      }
    } catch (e: Exception) {
      logError("Reading from the browser failed: $e")
    } finally {
      stopped = true
      app?.close()
    }
  }

  private fun onExtensionFrame(frame: ByteArray) {
    val connection = app
    if (connection != null && connection.send(frame)) return
    if (connection != null) lost(connection)
    // The app is not reachable: answer what needs an answer.
    val message = NativeProtocol.parse(frame) ?: return
    val type = message.string("type")
    val id = message.string("id")?.takeIf { it.length <= NativeProtocol.MAX_ID_CHARS } ?: return
    val version = (message["v"] as? JsonPrimitive)?.intOrNull
    val reply =
      when {
        // A side never answers an error with another error.
        type == NativeProtocol.ERROR || type == NativeProtocol.PAGE_READ -> null
        version != NativeProtocol.VERSION ->
          NativeProtocol.error(NativeErrorCode.UNSUPPORTED_VERSION, "Only version 1 is known", id)
        type == NativeProtocol.HELLO -> NativeProtocol.hello(id, hostVersion, appRunning = false)
        else ->
          NativeProtocol.error(NativeErrorCode.INTERNAL, "The JobFinder app is not running", id)
      }
    reply?.let { toExtension(NativeProtocol.encode(it)) }
  }

  private fun reconnectLoop() {
    while (!stopped) {
      Thread.sleep(RETRY_MS)
      if (app == null) connect()
    }
  }

  /** Connects to the app when it runs; its first frame is `status`, passed on to the extension. */
  private fun connect() =
    synchronized(connectLock) {
      if (app != null || stopped) return@synchronized
      val target = readConnectionFile() ?: return@synchronized
      val socket = Socket()
      try {
        socket.connect(
          InetSocketAddress(InetAddress.getLoopbackAddress(), target.first),
          CONNECT_MS,
        )
        socket.soTimeout = HANDSHAKE_MS
        val connection = AppConnection(socket)
        val token = buildJsonObject { put("token", target.second) }
        if (!connection.send(NativeProtocol.encode(token))) return@synchronized
        val first = NativeFraming.read(connection.input, NativeFraming.HOST_TO_EXTENSION_MAX)
        if (first == null) {
          socket.close()
          return@synchronized
        }
        socket.soTimeout = 0
        app = connection
        toExtension(first)
        Thread({ pump(connection) }, "native-host-app").apply { isDaemon = true }.start()
      } catch (_: Exception) {
        runCatching { socket.close() }
      }
    }

  /** Passes the app's messages to the extension until the app goes away. */
  private fun pump(connection: AppConnection) {
    try {
      while (true) {
        val frame =
          NativeFraming.read(connection.input, NativeFraming.HOST_TO_EXTENSION_MAX) ?: break
        toExtension(frame)
      }
    } catch (_: Exception) {
      // The app quit or sent a frame the browser would reject.
    }
    lost(connection)
  }

  private fun lost(connection: AppConnection) {
    connection.close()
    val wasCurrent = synchronized(connectLock) { (app === connection).also { if (it) app = null } }
    if (wasCurrent && !stopped) {
      toExtension(NativeProtocol.encode(NativeProtocol.status(appRunning = false, null)))
    }
  }

  private fun toExtension(frame: ByteArray) {
    if (frame.size > NativeFraming.HOST_TO_EXTENSION_MAX) return
    synchronized(stdout) { NativeFraming.write(stdout, frame) }
  }

  /** Port and token the app wrote, or `null` when it is not running. */
  private fun readConnectionFile(): Pair<Int, String>? {
    val text = runCatching { Files.readString(connectionFile) }.getOrNull() ?: return null
    val json = NativeProtocol.parse(text.encodeToByteArray()) ?: return null
    val port = (json["port"] as? JsonPrimitive)?.intOrNull ?: return null
    val token = json.string("token") ?: return null
    return port to token
  }

  private class AppConnection(private val socket: Socket) {
    val input: InputStream = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    /** `false` when the app is gone. */
    fun send(frame: ByteArray): Boolean =
      try {
        synchronized(this) { NativeFraming.write(output, frame) }
        true
      } catch (_: Exception) {
        false
      }

    fun close() {
      runCatching { socket.close() }
    }
  }

  private companion object {
    const val RETRY_MS = 2_000L
    const val CONNECT_MS = 2_000
    const val HANDSHAKE_MS = 5_000
  }
}
