package io.github.raghavsatyadev.jobfinder.pairing

import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * Resolves only [HOST], to the paired computer's address from [paired]; every other name fails. The
 * API client can therefore reach nothing but the computer on the local network, and cleartext HTTP
 * is allowed for [HOST] alone (`network_security_config.xml`).
 */
class PairedHostDns(private val paired: () -> LanIp?) : Dns {
  override fun lookup(hostname: String): List<InetAddress> {
    if (!hostname.equals(HOST, ignoreCase = true)) {
      throw UnknownHostException("$hostname is not the paired computer")
    }
    val address = paired() ?: throw UnknownHostException("No computer is paired")
    if (!LanAddress.isPrivate(address.literal)) {
      throw UnknownHostException("The paired address is not on the local network")
    }
    // An IP literal: no lookup happens.
    return listOf(InetAddress.getByName(address.literal))
  }

  companion object {
    /** The synthetic host name of the paired computer. */
    const val HOST = PairedHost.NAME
  }
}
