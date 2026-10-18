package io.github.raghavsatyadev.jobfinder.server.nativehost

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/** What the extension read from a page (`pageRead`); the JSON strings are not parsed. */
data class PageRead(
  val finalUrl: String,
  val title: String,
  val text: String,
  val jsonLd: List<String>,
  val nextData: String?,
  val html: String?,
  val truncated: List<String>,
  val elapsedMs: Long,
)

/** The result of [NativeHostLink.cookies]. */
sealed interface CookiesResult {
  /** [cookies] in the DevTools `Network.Cookie` shape; [missing] sites the browser may not read. */
  data class Read(val cookies: JsonArray, val missing: List<String>) : CookiesResult

  data class Failed(val code: String, val message: String) : CookiesResult
}

/** The result of [NativeHostLink.readPage]. */
sealed interface PageReadResult {
  data class Read(val page: PageRead) : PageReadResult

  /** [code] is a [NativeErrorCode]; [message] is English, for logs. */
  data class Failed(val code: String, val message: String) : PageReadResult
}

/**
 * The app's end of the browser extension link. The native host the browser starts (one per browser)
 * connects here over loopback TCP, proves it runs as this user with the token in [connectionFile]
 * (written in the user's own data folder), and relays the extension's messages unchanged. This side
 * answers `hello`, `jobForUrl` and the form messages ([FormMessages]), sends `status`, and sends
 * `readPage`.
 */
class NativeHostLink(
  private val connectionFile: Path,
  appVersion: String,
  private val jobs: JobUrlLookup,
  /** Answers `saveAnswers`, `answersFor` and `draftAnswer`; `null` refuses them as unknown. */
  private val forms: FormMessages? = null,
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(NativeHostLink::class.java)
  private val appVersion = appVersion.ifBlank { DEV_VERSION }
  private val peers = CopyOnWriteArrayList<Peer>()
  private val pending = ConcurrentHashMap<String, Pending>()
  private val nextId = AtomicLong()
  private var server: ServerSocket? = null

  /** Whether a browser extension is connected now. */
  val isConnected: Boolean
    get() = peers.isNotEmpty()

  fun start(scope: CoroutineScope) {
    val socket =
      try {
        ServerSocket(0, BACKLOG, InetAddress.getLoopbackAddress())
      } catch (e: Exception) {
        log.warn("The browser extension link could not start", e)
        return
      }
    server = socket
    val token =
      HexFormat.of().formatHex(ByteArray(TOKEN_BYTES).also { SecureRandom().nextBytes(it) })
    writeConnectionFile(socket.localPort, token)
    scope.launch(Dispatchers.IO) {
      while (!socket.isClosed) {
        val client =
          try {
            socket.accept()
          } catch (_: SocketException) {
            break
          }
        launch { serve(client, token.encodeToByteArray(), scope) }
      }
    }
    log.info("Browser extension link on 127.0.0.1:{}", socket.localPort)
  }

  /**
   * Asks the connected extension to read [url] in a background tab of the user's browser. Waits for
   * the read itself ([timeoutMs], clamped as the protocol says) plus time for reads queued before
   * it.
   */
  suspend fun readPage(
    url: String,
    waitForSelector: String? = null,
    timeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    includeHtml: Boolean = false,
  ): PageReadResult {
    val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull()
    if (scheme !in WEB_SCHEMES || url.length > NativeProtocol.MAX_URL_CHARS) {
      return PageReadResult.Failed(
        NativeErrorCode.BAD_REQUEST,
        "Not an http(s) URL up to 8192 chars",
      )
    }
    if (waitForSelector != null && waitForSelector.length > NativeProtocol.MAX_SELECTOR_CHARS) {
      return PageReadResult.Failed(NativeErrorCode.BAD_REQUEST, "The selector is too long")
    }
    val peer =
      peers.lastOrNull()
        ?: return PageReadResult.Failed(NativeErrorCode.NOT_CONNECTED, "No browser is connected")
    val timeout = timeoutMs.coerceIn(MIN_READ_TIMEOUT_MS, MAX_READ_TIMEOUT_MS)
    val id = "r-${nextId.incrementAndGet()}"
    val request = buildJsonObject {
      put("v", NativeProtocol.VERSION)
      put("type", NativeProtocol.READ_PAGE)
      put("id", id)
      put("url", url)
      waitForSelector?.let { put("waitForSelector", it) }
      put("timeoutMs", timeout)
      put("includeHtml", includeHtml)
    }
    val reply = CompletableDeferred<JsonObject?>()
    pending[id] = Pending(peer, reply)
    try {
      if (!peer.send(request)) {
        return PageReadResult.Failed(NativeErrorCode.NOT_CONNECTED, "The browser went away")
      }
      val message =
        withTimeoutOrNull(timeout + QUEUE_ALLOWANCE_MS) { reply.await() }
          ?: return PageReadResult.Failed(NativeErrorCode.TIMEOUT, "No answer from the browser")
      return toResult(message)
    } finally {
      pending.remove(id)
    }
  }

  /**
   * Asks the connected extension for the cookies of [sites] (host names; subdomains included) in
   * the user's browser, so their sign-ins can be copied to the scanner's browser.
   */
  suspend fun cookies(sites: List<String>): CookiesResult {
    val peer =
      peers.lastOrNull()
        ?: return CookiesResult.Failed(NativeErrorCode.NOT_CONNECTED, "No browser is connected")
    val id = "c-${nextId.incrementAndGet()}"
    val request = buildJsonObject {
      put("v", NativeProtocol.VERSION)
      put("type", NativeProtocol.GET_COOKIES)
      put("id", id)
      put("sites", JsonArray(sites.distinct().take(MAX_COOKIE_SITES).map(::JsonPrimitive)))
    }
    val reply = CompletableDeferred<JsonObject?>()
    pending[id] = Pending(peer, reply)
    try {
      if (!peer.send(request)) {
        return CookiesResult.Failed(NativeErrorCode.NOT_CONNECTED, "The browser went away")
      }
      val message =
        withTimeoutOrNull(COOKIES_TIMEOUT_MS) { reply.await() }
          ?: return CookiesResult.Failed(NativeErrorCode.TIMEOUT, "No answer from the browser")
      if (message.string("type") == NativeProtocol.ERROR) {
        return CookiesResult.Failed(
          message.string("code") ?: NativeErrorCode.INTERNAL,
          message.string("message").orEmpty(),
        )
      }
      val missing =
        (message["missing"] as? JsonArray)?.mapNotNull {
          (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
        } ?: emptyList()
      return CookiesResult.Read(message["cookies"] as? JsonArray ?: JsonArray(emptyList()), missing)
    } finally {
      pending.remove(id)
    }
  }

  override fun close() {
    runCatching { server?.close() }
    peers.forEach { it.close() }
    runCatching { Files.deleteIfExists(connectionFile) }
  }

  private fun toResult(message: JsonObject?): PageReadResult {
    if (message == null) {
      return PageReadResult.Failed(NativeErrorCode.NOT_CONNECTED, "The browser went away")
    }
    if (message.string("type") == NativeProtocol.ERROR) {
      return PageReadResult.Failed(
        message.string("code") ?: NativeErrorCode.INTERNAL,
        message.string("message").orEmpty(),
      )
    }
    fun strings(name: String) =
      (message[name] as? JsonArray)?.mapNotNull {
        (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
      } ?: emptyList()
    return PageReadResult.Read(
      PageRead(
        finalUrl = message.string("finalUrl").orEmpty(),
        title = message.string("title").orEmpty(),
        text = message.string("text").orEmpty(),
        jsonLd = strings("jsonLd"),
        nextData = message.string("nextData"),
        html = message.string("html"),
        truncated = strings("truncated"),
        elapsedMs = (message["elapsedMs"] as? JsonPrimitive)?.longOrNull ?: 0,
      )
    )
  }

  /** One native host connection: checks the token, then reads messages until it closes. */
  private suspend fun serve(socket: Socket, token: ByteArray, scope: CoroutineScope) {
    val peer = Peer(socket)
    try {
      socket.soTimeout = HANDSHAKE_TIMEOUT_MS
      val input = BufferedInputStream(socket.getInputStream())
      val hello = NativeFraming.read(input, NativeFraming.HOST_TO_EXTENSION_MAX)
      val given = hello?.let { NativeProtocol.parse(it)?.string("token") }?.encodeToByteArray()
      if (given == null || !MessageDigest.isEqual(given, token)) {
        log.warn("A native host connection had a wrong token; closed")
        return
      }
      socket.soTimeout = 0
      peers += peer
      peer.send(NativeProtocol.status(appRunning = true, appVersion))
      while (true) {
        val frame = NativeFraming.read(input) ?: break
        val message = NativeProtocol.parse(frame)
        scope.launch(Dispatchers.IO) { handle(peer, message) }
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.debug("A native host connection ended", e)
    } finally {
      peers -= peer
      peer.close()
      pending.values.filter { it.peer === peer }.forEach { it.reply.complete(null) }
    }
  }

  private suspend fun handle(peer: Peer, message: JsonObject?) {
    if (message == null) {
      peer.send(NativeProtocol.error(NativeErrorCode.BAD_REQUEST, "Not a JSON object", null))
      return
    }
    val type = message.string("type")
    val id = message.string("id")?.takeIf { it.length <= NativeProtocol.MAX_ID_CHARS }
    // A side never answers an error with another error.
    if (
      type == NativeProtocol.ERROR ||
        type == NativeProtocol.PAGE_READ ||
        type == NativeProtocol.COOKIES
    ) {
      id?.let { pending[it] }?.reply?.complete(message)
      return
    }
    val version = (message["v"] as? JsonPrimitive)?.longOrNull
    when {
      version != NativeProtocol.VERSION.toLong() ->
        peer.send(error(NativeErrorCode.UNSUPPORTED_VERSION, "Only version 1 is known", id))
      type == null -> peer.send(error(NativeErrorCode.BAD_REQUEST, "No string type", id))
      id == null -> peer.send(error(NativeErrorCode.BAD_REQUEST, "No usable id", null))
      type == NativeProtocol.HELLO ->
        peer.send(NativeProtocol.hello(id, appVersion, appRunning = true))
      type == NativeProtocol.JOB_FOR_URL -> answerJobForUrl(peer, id, message.string("url"))
      forms != null && type in FormMessages.REQUESTS -> peer.send(forms.handle(type, id, message))
      else -> peer.send(error(NativeErrorCode.UNKNOWN_TYPE, "Unknown type $type", id))
    }
  }

  private suspend fun answerJobForUrl(peer: Peer, id: String, url: String?) {
    if (url == null) {
      peer.send(error(NativeErrorCode.BAD_REQUEST, "No string url", id))
      return
    }
    val job =
      try {
        jobs.find(url)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.warn("Job lookup for a browser tab failed", e)
        peer.send(error(NativeErrorCode.INTERNAL, "Lookup failed", id))
        return
      }
    peer.send(
      buildJsonObject {
        put("v", NativeProtocol.VERSION)
        put("type", NativeProtocol.JOB)
        put("id", id)
        put("found", job != null)
        if (job != null) {
          put("jobId", job.id.toString())
          put("title", job.title)
          put("company", job.company)
          job.atsScore?.let { put("atsScore", it) }
        }
      }
    )
  }

  private fun error(code: String, message: String, id: String?) =
    NativeProtocol.error(code, message, id)

  /** Port and token for the native host; replaced atomically so a reader never sees half. */
  private fun writeConnectionFile(port: Int, token: String) {
    val body = buildJsonObject {
      put("port", port)
      put("token", token)
    }
    Files.createDirectories(connectionFile.parent)
    val temp = connectionFile.resolveSibling("${connectionFile.fileName}.tmp")
    Files.writeString(temp, body.toString())
    Files.move(
      temp,
      connectionFile,
      StandardCopyOption.REPLACE_EXISTING,
      StandardCopyOption.ATOMIC_MOVE,
    )
  }

  private class Pending(val peer: Peer, val reply: CompletableDeferred<JsonObject?>)

  private class Peer(private val socket: Socket) {
    private val output: OutputStream = BufferedOutputStream(socket.getOutputStream())

    /** `false` when the connection is gone. */
    fun send(message: JsonObject): Boolean =
      try {
        synchronized(this) { NativeFraming.write(output, NativeProtocol.encode(message)) }
        true
      } catch (_: Exception) {
        close()
        false
      }

    fun close() {
      runCatching { socket.close() }
    }
  }

  companion object {
    /** In the app's data folder; the native host reads it to find the app. */
    const val CONNECTION_FILE = "native-host.json"
    private const val DEV_VERSION = "dev"
    private const val BACKLOG = 8
    private const val TOKEN_BYTES = 32
    private const val HANDSHAKE_TIMEOUT_MS = 5_000
    const val DEFAULT_READ_TIMEOUT_MS = 30_000L
    private const val MIN_READ_TIMEOUT_MS = 1_000L
    private const val MAX_READ_TIMEOUT_MS = 120_000L
    /** Reads run one at a time in the extension, so a read may wait behind others. */
    private const val QUEUE_ALLOWANCE_MS = 120_000L
    private const val COOKIES_TIMEOUT_MS = 15_000L
    private const val MAX_COOKIE_SITES = 200
    private val WEB_SCHEMES = setOf("http", "https")
  }
}
