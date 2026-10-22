package io.github.raghavsatyadev.jobfinder.pairing

import com.russhwolf.settings.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The pairings, in the app's private [Settings] (Android: the `pairing` preferences, with backup
 * off; iOS: the app's defaults), so they never leave the phone.
 */
class PairingStore
private constructor(
  saved: Pair<List<PairedDesktop>, String?>,
  persist: (List<PairedDesktop>, String?) -> Unit,
) : PairedComputers(saved.first, saved.second, persist) {
  companion object {
    private const val KEY_COMPUTERS = "computers"
    private const val KEY_ACTIVE = "active"
    private const val LEGACY_IP = "ip"
    private const val LEGACY_PORT = "port"
    private const val LEGACY_TOKEN = "token"

    operator fun invoke(settings: Settings): PairingStore {
      val write: (List<PairedDesktop>, String?) -> Unit = { list, active ->
        settings.putString(KEY_COMPUTERS, encode(list))
        if (active != null) settings.putString(KEY_ACTIVE, active) else settings.remove(KEY_ACTIVE)
        settings.remove(LEGACY_IP)
        settings.remove(LEGACY_PORT)
        settings.remove(LEGACY_TOKEN)
      }
      val saved =
        read(settings.getStringOrNull(KEY_COMPUTERS)) to settings.getStringOrNull(KEY_ACTIVE)
      val legacy = legacy(settings)
      if (saved.first.isEmpty() && legacy != null) {
        write(listOf(legacy), legacy.key)
        return PairingStore(listOf(legacy) to legacy.key, write)
      }
      return PairingStore(saved, write)
    }

    /** The single pairing kept before several computers were supported. */
    private fun legacy(settings: Settings): PairedDesktop? {
      val ip = settings.getStringOrNull(LEGACY_IP)?.let(LanAddress::parse) ?: return null
      val token = settings.getStringOrNull(LEGACY_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
      val port = settings.getInt(LEGACY_PORT, 0).takeIf(PairedDesktop::isPort) ?: return null
      return PairedDesktop(ip, port, token)
    }

    private fun encode(list: List<PairedDesktop>): String =
      JsonArray(
          list.map {
            buildJsonObject {
              put("ip", it.ip.literal)
              put("port", it.port)
              put("token", it.token)
              it.id?.let { id -> put("id", id) }
              it.name?.let { name -> put("name", name) }
            }
          }
        )
        .toString()

    private fun read(text: String?): List<PairedDesktop> {
      if (text == null) return emptyList()
      val array = runCatching { Json.parseToJsonElement(text) as JsonArray }.getOrNull()
      return array.orEmpty().mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        val ip = o.text("ip")?.let(LanAddress::parse) ?: return@mapNotNull null
        val port =
          (o["port"] as? JsonPrimitive)?.intOrNull?.takeIf(PairedDesktop::isPort)
            ?: return@mapNotNull null
        val token = o.text("token")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        PairedDesktop(
          ip,
          port,
          token,
          id = o.text("id")?.ifBlank { null },
          name = o.text("name")?.ifBlank { null },
        )
      }
    }

    private fun JsonObject.text(key: String): String? =
      (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
  }
}
