package io.github.raghavsatyadev.jobfinder.desktop.browser

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * A minimal Chrome DevTools Protocol client over the JDK's WebSocket: sends commands to a browser
 * endpoint and returns their results.
 */
class DevToolsConnection private constructor(private val socket: WebSocket, listener: Listener) :
  AutoCloseable {
  private val pending = listener.pending
  private val ids = AtomicLong()

  /** `true` once the browser closed the connection (for example because it quit). */
  val isClosed: Boolean
    get() = socket.isInputClosed

  /** Runs [method] and returns its `result`. Throws on a protocol error or after [timeout]. */
  suspend fun send(
    method: String,
    params: JsonObject = JsonObject(emptyMap()),
    timeout: Duration = COMMAND_TIMEOUT,
  ): JsonObject {
    val id = ids.incrementAndGet()
    val reply = CompletableDeferred<JsonObject>()
    pending[id] = reply
    try {
      val message = buildJsonObject {
        put("id", id)
        put("method", method)
        put("params", params)
      }
      socket.sendText(message.toString(), true).await()
      val response = withTimeout(timeout) { reply.await() }
      response["error"]?.let { error("$method failed: $it") }
      return response["result"]?.jsonObject ?: JsonObject(emptyMap())
    } finally {
      pending.remove(id)
    }
  }

  override fun close() {
    if (!socket.isOutputClosed) socket.sendClose(WebSocket.NORMAL_CLOSURE, "")
    socket.abort()
  }

  private class Listener : WebSocket.Listener {
    val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val text = StringBuilder()

    override fun onText(socket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
      text.append(data)
      if (last) {
        val raw = text.toString()
        val message = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
        text.setLength(0)
        val id = message?.get("id")?.jsonPrimitive?.long
        if (id != null) pending[id]?.complete(message)
      }
      socket.request(1)
      return null
    }

    override fun onClose(socket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
      failAll("DevTools connection closed")
      return null
    }

    override fun onError(socket: WebSocket, error: Throwable) = failAll(error.toString())

    private fun failAll(reason: String) {
      pending.values.forEach { it.completeExceptionally(IllegalStateException(reason)) }
    }
  }

  companion object {
    private val COMMAND_TIMEOUT = 10.seconds

    /** Connects to a DevTools [endpoint] such as `ws://127.0.0.1:9222/devtools/browser/<id>`. */
    suspend fun connect(endpoint: String): DevToolsConnection {
      val listener = Listener()
      val socket =
        HttpClient.newHttpClient()
          .newWebSocketBuilder()
          .buildAsync(URI.create(endpoint), listener)
          .await()
      return DevToolsConnection(socket, listener)
    }

    /** `params` for [send] from pairs. */
    fun params(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(mapOf(*pairs))
  }
}
