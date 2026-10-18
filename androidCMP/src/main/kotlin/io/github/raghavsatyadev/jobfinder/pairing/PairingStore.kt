package io.github.raghavsatyadev.jobfinder.pairing

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The pairings, in app-private preferences (backup is off, so they never leave the phone). */
class PairingStore
private constructor(
  saved: Pair<List<PairedDesktop>, String?>,
  persist: (List<PairedDesktop>, String?) -> Unit,
) : PairedComputers(saved.first, saved.second, persist) {
  companion object {
    private const val FILE = "pairing"
    private const val KEY_COMPUTERS = "computers"
    private const val KEY_ACTIVE = "active"
    private const val LEGACY_IP = "ip"
    private const val LEGACY_PORT = "port"
    private const val LEGACY_TOKEN = "token"

    operator fun invoke(context: Context): PairingStore {
      val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
      val write: (List<PairedDesktop>, String?) -> Unit = { list, active ->
        prefs.edit {
          putString(KEY_COMPUTERS, encode(list))
          putString(KEY_ACTIVE, active)
          remove(LEGACY_IP)
          remove(LEGACY_PORT)
          remove(LEGACY_TOKEN)
        }
      }
      val saved = read(prefs.getString(KEY_COMPUTERS, null)) to prefs.getString(KEY_ACTIVE, null)
      val legacy = legacy(prefs)
      if (saved.first.isEmpty() && legacy != null) {
        write(listOf(legacy), legacy.key)
        return PairingStore(listOf(legacy) to legacy.key, write)
      }
      return PairingStore(saved, write)
    }

    /** The single pairing kept before several computers were supported. */
    private fun legacy(prefs: android.content.SharedPreferences): PairedDesktop? {
      val ip = prefs.getString(LEGACY_IP, null)?.let(LanAddress::parse) ?: return null
      val token = prefs.getString(LEGACY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
      val port = prefs.getInt(LEGACY_PORT, 0).takeIf(PairedDesktop::isPort) ?: return null
      return PairedDesktop(ip, port, token)
    }

    private fun encode(list: List<PairedDesktop>): String =
      JSONArray(
          list.map {
            JSONObject()
              .put("ip", it.ip.literal)
              .put("port", it.port)
              .put("token", it.token)
              .putOpt("id", it.id)
              .putOpt("name", it.name)
          }
        )
        .toString()

    private fun read(text: String?): List<PairedDesktop> {
      if (text == null) return emptyList()
      return try {
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { i ->
          val o = array.getJSONObject(i)
          val ip = LanAddress.parse(o.optString("ip")) ?: return@mapNotNull null
          val port = o.optInt("port").takeIf(PairedDesktop::isPort) ?: return@mapNotNull null
          val token = o.optString("token").takeIf { it.isNotBlank() } ?: return@mapNotNull null
          PairedDesktop(
            ip,
            port,
            token,
            id = o.optString("id").ifBlank { null },
            name = o.optString("name").ifBlank { null },
          )
        }
      } catch (_: JSONException) {
        emptyList()
      }
    }
  }
}
