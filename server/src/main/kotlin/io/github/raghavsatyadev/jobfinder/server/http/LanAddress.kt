package io.github.raghavsatyadev.jobfinder.server.http

import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException

/** Picks the IPv4 address the API server listens on. */
object LanAddress {
  /**
   * A public address used only to ask the OS for its route. Connecting a UDP socket sends nothing;
   * it just selects the source address of the default route, so Hyper-V, WSL, Docker and VPN
   * adapters without the default route are never picked.
   */
  private val ROUTE_PROBE = InetSocketAddress(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8)), 53)

  /**
   * The address for [interfaceName] when it is set, otherwise the default-route address. `null`
   * when there is no usable IPv4 (offline, or the named interface is down or missing).
   */
  fun pick(interfaceName: String? = null): Inet4Address? =
    if (interfaceName.isNullOrBlank()) defaultRouteIpv4() else interfaceIpv4(interfaceName)

  /** The source IPv4 of the default route, or `null`. */
  fun defaultRouteIpv4(): Inet4Address? =
    try {
      DatagramSocket().use { socket ->
        socket.connect(ROUTE_PROBE)
        (socket.localAddress as? Inet4Address)?.takeIf(::usable)
      }
    } catch (_: SocketException) {
      null
    }

  /** The first usable IPv4 of the interface named [name] (`NetworkInterface.getName`). */
  fun interfaceIpv4(name: String): Inet4Address? =
    try {
      NetworkInterface.getByName(name)
        ?.takeIf { it.isUp }
        ?.inetAddresses
        ?.asSequence()
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull(::usable)
    } catch (_: SocketException) {
      null
    }

  private fun usable(address: Inet4Address): Boolean =
    !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress
}
