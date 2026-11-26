package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/** Helpers the MCP tool groups share. */
internal inline fun <reified T> mcpJson(value: T): JsonElement = ApiJson.encodeToJsonElement(value)

/** [value] as the app's JSON for [T]; a bad shape is a [McpToolError] the agent can fix. */
internal inline fun <reified T> fromMcpJson(value: JsonElement, what: String): T =
  try {
    ApiJson.decodeFromJsonElement<T>(value)
  } catch (e: SerializationException) {
    throw McpToolError("Not a $what: ${e.message}")
  } catch (e: IllegalArgumentException) {
    throw McpToolError("Not a $what: ${e.message}")
  }

/** An ISO time, or `null` for "never". */
internal fun iso(millis: Long): String? =
  if (millis <= 0) null else Instant.ofEpochMilli(millis).toString()

/** The argument [key] as any JSON value, `null` when it isn't given (or is JSON null). */
internal fun JsonObject.optElement(key: String): JsonElement? =
  this[key]?.takeIf { it !is JsonNull }

/** A whole number argument that may be sent as a number or a numeric string. */
internal fun JsonObject.optInt(key: String): Int? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.let {
    it.toIntOrNull() ?: throw McpToolError("\"$key\" must be a whole number.")
  }

/** An online key as agents see it: never the key itself. */
internal fun describeKey(k: AiKey, requestsToday: Int, now: Long) = buildJsonObject {
  put("id", k.id)
  put("provider", k.provider.name.lowercase())
  put("label", k.label)
  put("model", k.defaultModel)
  put("requestsPerMinute", k.requestsPerMinute)
  put("requestsPerDay", k.requestsPerDay)
  put("requestsToday", requestsToday)
  put("expires", k.expiresAt?.let(::iso))
  put("expired", k.expired(now))
}
