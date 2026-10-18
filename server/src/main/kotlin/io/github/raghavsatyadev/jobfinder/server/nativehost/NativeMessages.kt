package io.github.raghavsatyadev.jobfinder.server.nativehost

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Chrome native messaging framing: a 32-bit little-endian length, then that many bytes of UTF-8
 * JSON. The native host uses it with the browser and, unchanged, with the app over loopback.
 */
object NativeFraming {
  /** The browser closes the port on a larger host → extension message. */
  const val HOST_TO_EXTENSION_MAX = 1024 * 1024

  /** The browser's cap for extension → host messages. */
  const val EXTENSION_TO_HOST_MAX = 64 * 1024 * 1024

  /** One frame, or `null` at a clean end of [input]. A cut or too long frame throws. */
  fun read(input: InputStream, max: Int = EXTENSION_TO_HOST_MAX): ByteArray? {
    val header = input.readNBytes(HEADER_BYTES)
    if (header.isEmpty()) return null
    if (header.size < HEADER_BYTES) throw EOFException("The frame header was cut")
    val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and UINT_MASK
    if (length > max) throw IOException("A frame of $length bytes is over the $max byte limit")
    val body = input.readNBytes(length.toInt())
    if (body.size.toLong() != length) throw EOFException("The frame was cut")
    return body
  }

  /** Writes one frame and flushes. Callers that share [output] must not write at the same time. */
  fun write(output: OutputStream, body: ByteArray) {
    val header =
      ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array()
    output.write(header)
    output.write(body)
    output.flush()
  }

  private const val HEADER_BYTES = 4
  private const val UINT_MASK = 0xFFFFFFFFL
}

/** Message types and error codes of `browser-extension/PROTOCOL.md`, version 1. */
object NativeProtocol {
  /** The native host manifest `name` and the registry key name. */
  const val HOST_NAME = "io.github.raghavsatyadev.jobfinder"
  const val VERSION = 1

  const val HELLO = "hello"
  const val STATUS = "status"
  const val READ_PAGE = "readPage"
  const val PAGE_READ = "pageRead"
  const val JOB_FOR_URL = "jobForUrl"
  const val JOB = "job"
  const val GET_COOKIES = "getCookies"
  const val COOKIES = "cookies"
  const val ERROR = "error"

  /** Longest `id`, `url` and `waitForSelector` the protocol allows. */
  const val MAX_ID_CHARS = 128
  const val MAX_URL_CHARS = 8_192
  const val MAX_SELECTOR_CHARS = 512

  val json = Json { ignoreUnknownKeys = true }

  /** [bytes] as a JSON object, or `null` when it is not one. */
  fun parse(bytes: ByteArray): JsonObject? = runCatching {
    json.parseToJsonElement(bytes.decodeToString()) as? JsonObject
  }
    .getOrNull()

  fun encode(message: JsonObject): ByteArray = message.toString().encodeToByteArray()

  fun hello(id: String, appVersion: String, appRunning: Boolean): JsonObject = buildJsonObject {
    put("v", VERSION)
    put("type", HELLO)
    put("id", id)
    put("appVersion", appVersion)
    put("appRunning", appRunning)
  }

  fun status(appRunning: Boolean, appVersion: String?): JsonObject = buildJsonObject {
    put("v", VERSION)
    put("type", STATUS)
    put("appRunning", appRunning)
    appVersion?.let { put("appVersion", it) }
  }

  fun error(code: String, message: String, id: String?): JsonObject = buildJsonObject {
    put("v", VERSION)
    put("type", ERROR)
    id?.let { put("id", it) }
    put("code", code)
    put("message", message)
  }
}

/** The `error` codes; [NOT_CONNECTED] is local to the app and never sent. */
object NativeErrorCode {
  const val BAD_REQUEST = "bad_request"
  const val UNSUPPORTED_VERSION = "unsupported_version"
  const val UNKNOWN_TYPE = "unknown_type"
  const val NO_PERMISSION = "no_permission"
  const val TIMEOUT = "timeout"
  const val TAB_FAILED = "tab_failed"
  const val EXTRACT_FAILED = "extract_failed"
  const val BUSY = "busy"
  const val INTERNAL = "internal"

  /** No extension is connected, or it went away before it answered. */
  const val NOT_CONNECTED = "not_connected"
}

/** The string field [name], or `null` when absent or not a string. */
internal fun JsonObject.string(name: String): String? =
  (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
