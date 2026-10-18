package io.github.raghavsatyadev.jobfinder.pairing

import kotlin.jvm.JvmInline

/**
 * An IP literal on a private network, as [LanAddress.parse] accepted it: IPv4 dotted
 * (`192.168.1.20`) or IPv6 (`fe80::1`, with an optional `%zone`). Never a host name, so using it
 * never resolves one.
 */
@JvmInline
value class LanIp internal constructor(val literal: String) {
  val isIpv4: Boolean
    get() = '.' in literal && ':' !in literal

  override fun toString(): String = literal
}

/**
 * Addresses the phone may talk to: the paired computer on the local network, never the internet.
 */
object LanAddress {
  private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
  private val HEXTET = Regex("""^[0-9a-fA-F]{1,4}$""")
  private const val MAX_OCTET = 255
  private const val MAX_HEXTETS = 8
  private const val ULA_MASK = 0xFE00
  private const val ULA_PREFIX = 0xFC00
  private const val LINK_LOCAL_MASK = 0xFFC0
  private const val LINK_LOCAL_PREFIX = 0xFE80

  /**
   * [host] as an address when it is an IP literal on a private network (RFC 1918, IPv4 link-local,
   * IPv6 unique-local or link-local); `null` for a host name or any other address. Never resolves a
   * name.
   */
  fun parse(host: String): LanIp? {
    val literal = host.trim().removePrefix("[").removeSuffix("]")
    return when {
      IPV4.matches(literal) -> ipv4(literal)?.takeIf(::isPrivateIpv4)?.let { LanIp(literal) }
      ':' in literal -> {
        val first = firstHextet(literal.substringBefore('%')) ?: return null
        LanIp(literal).takeIf { isPrivateIpv6(first) }
      }
      else -> null
    }
  }

  /** Whether [literal] (an IP address as text) is on a private network. */
  fun isPrivate(literal: String): Boolean = parse(literal) != null

  private fun ipv4(literal: String): List<Int>? =
    IPV4.matchEntire(literal)
      ?.groupValues
      ?.drop(1)
      ?.map { it.toInt() }
      ?.takeIf { octets ->
        octets.all { it <= MAX_OCTET }
      }

  private fun isPrivateIpv4(o: List<Int>): Boolean =
    o[0] == 10 ||
      (o[0] == 172 && o[1] in 16..31) ||
      (o[0] == 192 && o[1] == 168) ||
      (o[0] == 169 && o[1] == 254)

  private fun isPrivateIpv6(first: Int): Boolean =
    (first and LINK_LOCAL_MASK) == LINK_LOCAL_PREFIX || (first and ULA_MASK) == ULA_PREFIX

  /** The first 16 bits of a valid IPv6 literal, or `null` when it is not one. */
  private fun firstHextet(literal: String): Int? {
    if (literal.count { it == ':' } < 2) return null
    val halves = literal.split("::")
    if (halves.size > 2) return null
    val groups = halves.map { half -> if (half.isEmpty()) emptyList() else half.split(':') }
    val all = groups.flatten()
    // An embedded IPv4 tail (`::ffff:10.0.0.1`) counts as two groups.
    val last = all.lastOrNull()
    val tailIpv4 = last != null && '.' in last
    if (tailIpv4 && ipv4(last) == null) return null
    val hextets = if (tailIpv4) all.dropLast(1) else all
    if (!hextets.all(HEXTET::matches)) return null
    val count = hextets.size + if (tailIpv4) 2 else 0
    if (halves.size == 1 && count != MAX_HEXTETS) return null
    if (halves.size == 2 && count >= MAX_HEXTETS) return null
    // `::1` and other literals starting with `::` begin with zeros.
    return groups[0].firstOrNull()?.toInt(16) ?: 0
  }
}

/**
 * The synthetic host name the phone's HTTP client maps to the paired computer, and to nothing else.
 */
object PairedHost {
  const val NAME = "f2a-pc.lan"
}
