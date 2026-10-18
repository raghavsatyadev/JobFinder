package io.github.raghavsatyadev.jobfinder.pairing

import io.github.raghavsatyadev.jobfinder.shared.api.PairingPayload
import io.github.raghavsatyadev.jobfinder.shared.remote.RemoteEndpoint
import io.ktor.http.URLProtocol
import io.ktor.http.parseUrl
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A computer this phone is paired with. [id] and [name] come from its `/health`; pairings from
 * before they existed have neither until the next pairing.
 */
data class PairedDesktop(
  val ip: LanIp,
  val port: Int,
  val token: String,
  val id: String? = null,
  val name: String? = null,
) {
  /** As shown to the user: `192.168.1.20:8787`. */
  val address: String
    get() = "$ip:$port"

  /** Names this computer among the saved ones. */
  val key: String
    get() = id ?: address

  /** The API, through the synthetic host [PairedHost] that the platform maps to [ip]. */
  val endpoint: RemoteEndpoint
    get() = RemoteEndpoint("http://${PairedHost.NAME}:$port", token)

  /** What a pairing code holds, or why it is refused. */
  sealed interface Parsed {
    data class Ok(val desktop: PairedDesktop) : Parsed

    data object NotACode : Parsed

    /** The address is not a private-network IP. */
    data object NotOnLan : Parsed
  }

  companion object {
    private val PORTS = 1..65535

    fun fromCode(code: String): Parsed {
      val payload = PairingPayload.decodeOrNull(code) ?: return Parsed.NotACode
      val url = parseUrl(payload.url) ?: return Parsed.NotACode
      if (url.protocol != URLProtocol.HTTP || url.specifiedPort !in PORTS) return Parsed.NotACode
      val ip = LanAddress.parse(url.host) ?: return Parsed.NotOnLan
      return Parsed.Ok(PairedDesktop(ip, url.specifiedPort, payload.token))
    }

    fun isPort(port: Int) = port in PORTS
  }
}

/** The saved computers. [desktop] is the active one, `null` when none is saved. */
interface PairingStorage {
  val computers: StateFlow<List<PairedDesktop>>
  val desktop: StateFlow<PairedDesktop?>

  /** Adds [desktop], or replaces the saved one it matches, and makes it active. */
  fun save(desktop: PairedDesktop)

  fun select(key: String)

  /** Forgets [key]; when it was active, the first one left becomes active. */
  fun remove(key: String)

  /** The computer [id] is now at [ip]:[port] (a new DHCP lease). */
  fun updateAddress(id: String, ip: LanIp, port: Int)
}

/** [PairingStorage] in memory; [persist] writes each change. */
open class PairedComputers(
  initial: List<PairedDesktop>,
  activeKey: String?,
  private val persist: (List<PairedDesktop>, String?) -> Unit = { _, _ -> },
) : PairingStorage {
  private val lock = SynchronizedObject()
  private val _computers = MutableStateFlow(initial)
  private val _desktop =
    MutableStateFlow(initial.firstOrNull { it.key == activeKey } ?: initial.firstOrNull())
  override val computers: StateFlow<List<PairedDesktop>> = _computers.asStateFlow()
  override val desktop: StateFlow<PairedDesktop?> = _desktop.asStateFlow()

  override fun save(desktop: PairedDesktop) = change { list, _ ->
    val index = list.indexOfFirst { it.sameComputer(desktop) }
    val next =
      if (index < 0) list + desktop
      else list.toMutableList().apply { set(index, desktop) }.distinctBy { it.key }
    next to desktop.key
  }

  override fun select(key: String) = change { list, active ->
    list to (key.takeIf { k -> list.any { it.key == k } } ?: active)
  }

  override fun remove(key: String) = change { list, active ->
    val next = list.filterNot { it.key == key }
    next to (active.takeIf { it != key } ?: next.firstOrNull()?.key)
  }

  override fun updateAddress(id: String, ip: LanIp, port: Int) = change { list, active ->
    list.map { if (it.id == id) it.copy(ip = ip, port = port) else it } to active
  }

  private fun change(block: (List<PairedDesktop>, String?) -> Pair<List<PairedDesktop>, String?>) {
    synchronized(lock) {
      val (list, active) = block(_computers.value, _desktop.value?.key)
      val activeDesktop = list.firstOrNull { it.key == active }
      if (list == _computers.value && activeDesktop == _desktop.value) return
      persist(list, activeDesktop?.key)
      _computers.value = list
      _desktop.value = activeDesktop
    }
  }

  /** The same id, or the same address for a pairing that had no id yet. */
  private fun PairedDesktop.sameComputer(other: PairedDesktop): Boolean =
    if (id != null && other.id != null) id == other.id else address == other.address
}
