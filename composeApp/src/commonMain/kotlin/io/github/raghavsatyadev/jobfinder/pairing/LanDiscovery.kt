package io.github.raghavsatyadev.jobfinder.pairing

import io.github.raghavsatyadev.jobfinder.shared.api.Discovery
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull

/** A desktop announcing JobFinder on this network (`_jobfinder._tcp`). */
data class Announced(val id: String, val name: String, val ip: LanIp, val port: Int) {
  val address: String
    get() = "$ip:$port"

  companion object {
    /**
     * The announcement, when it has the id and a private address (IPv4 first); `null` otherwise.
     */
    fun of(
      name: String,
      attributes: Map<String, ByteArray?>,
      /** The announced IP addresses, as text. */
      addresses: List<String>,
      port: Int,
    ): Announced? {
      val id = attributes[Discovery.TXT_ID]?.decodeToString()?.trim()?.takeIf { it.isNotEmpty() }
      val ip =
        addresses.mapNotNull(LanAddress::parse).sortedBy { if (it.isIpv4) 0 else 1 }.firstOrNull()
      if (id == null || ip == null || !PairedDesktop.isPort(port)) return null
      return Announced(id, name, ip, port)
    }
  }
}

/** Computers announcing JobFinder on the local network. */
fun interface LanDiscovery {
  /** The computers found so far, while collected. Starts with an empty list. */
  fun announced(): Flow<List<Announced>>
}

/** The computer [id] when it shows up within [timeout], else `null`. */
suspend fun LanDiscovery.find(id: String, timeout: Duration = 6.seconds): Announced? =
  withTimeoutOrNull(timeout) {
    announced().mapNotNull { list -> list.firstOrNull { it.id == id } }.firstOrNull()
  }
