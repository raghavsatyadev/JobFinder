@file:OptIn(ExperimentalForeignApi::class)

package io.github.raghavsatyadev.jobfinder.ios

import io.github.raghavsatyadev.jobfinder.pairing.LanIp
import io.github.raghavsatyadev.jobfinder.pairing.LocalLink
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.sockaddr_in

/**
 * [LocalLink] from the phone's IPv4 interfaces: Wi-Fi (`en0`) or wired (`en*`) with their subnet
 * masks. Cellular (`pdp_ip*`) does not count as the local network.
 */
data class IosLocalLink(override val onLan: Boolean, val subnets: List<Pair<UInt, UInt>>) :
  LocalLink {
  override fun contains(ip: LanIp): Boolean {
    val address = ipv4(ip.literal) ?: return false
    return subnets.any { (own, mask) -> own and mask == address and mask }
  }

  companion object {
    fun current(): IosLocalLink = memScoped {
      val list = alloc<kotlinx.cinterop.CPointerVar<ifaddrs>>()
      if (getifaddrs(list.ptr) != 0) return IosLocalLink(false, emptyList())
      val subnets = mutableListOf<Pair<UInt, UInt>>()
      try {
        var entry = list.value
        while (entry != null) {
          val item = entry.pointed
          val name = item.ifa_name?.toKString().orEmpty()
          val flags = item.ifa_flags.toInt()
          val address = item.ifa_addr
          val mask = item.ifa_netmask
          if (
            name.startsWith("en") &&
              flags and IFF_UP != 0 &&
              flags and IFF_LOOPBACK == 0 &&
              address != null &&
              mask != null &&
              address.pointed.sa_family.toInt() == AF_INET
          ) {
            val own = address.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
            val bits = mask.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
            subnets += networkOrder(own) to networkOrder(bits)
          }
          entry = item.ifa_next
        }
      } finally {
        freeifaddrs(list.value)
      }
      IosLocalLink(subnets.isNotEmpty(), subnets)
    }

    /** `in_addr.s_addr` is big-endian in memory; Apple devices are little-endian. */
    private fun networkOrder(raw: UInt): UInt =
      ((raw and 0xFFu) shl 24) or
        ((raw and 0xFF00u) shl 8) or
        ((raw shr 8) and 0xFF00u) or
        (raw shr 24)

    private fun ipv4(literal: String): UInt? {
      val parts = literal.split('.').map { it.toIntOrNull() ?: return null }
      if (parts.size != 4 || parts.any { it !in 0..255 }) return null
      return parts.fold(0u) { acc, part -> (acc shl 8) or part.toUInt() }
    }
  }
}
