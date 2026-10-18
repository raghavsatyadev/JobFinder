package io.github.raghavsatyadev.jobfinder.shared.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The JSON both ends of the API use. Strict: unknown keys and malformed values are errors, so a
 * client and server out of step fail loudly instead of dropping fields.
 */
val ApiJson: Json = Json {
  encodeDefaults = true
  ignoreUnknownKeys = false
  isLenient = false
  classDiscriminator = "type"
}

/**
 * What the desktop app's pairing QR code holds: the server URL and the bearer token. Encoded as
 * JSON with [ApiJson].
 */
@Serializable
data class PairingPayload(val url: String, val token: String, val version: Int = VERSION) {
  fun encode(): String = ApiJson.encodeToString(serializer(), this)

  companion object {
    const val VERSION = 1

    /** Returns `null` when [text] is not a JobFinder pairing code. */
    fun decodeOrNull(text: String): PairingPayload? =
      try {
        ApiJson.decodeFromString(serializer(), text).takeIf {
          it.version == VERSION && it.url.startsWith("http://") && it.token.isNotBlank()
        }
      } catch (_: IllegalArgumentException) {
        null
      }
  }
}
