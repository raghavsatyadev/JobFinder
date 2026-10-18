package io.github.raghavsatyadev.jobfinder.pairing

import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.ui.platform.FoundComputer
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingError
import io.github.raghavsatyadev.jobfinder.ui.platform.PhonePairing
import io.github.raghavsatyadev.jobfinder.ui.platform.SavedComputer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Pairing on a phone. A computer is saved only at a private-network address, and only once [verify]
 * reaches it with the token: from a scanned code, or from the user's approval on the computer.
 */
class LanPhonePairing(
  private val store: PairingStorage,
  private val scanCode: suspend () -> String?,
  override val connected: StateFlow<Boolean>,
  scope: CoroutineScope,
  private val verify: suspend (PairedDesktop) -> Boolean,
  private val approvals: ApprovalPairing,
  private val discovery: LanDiscovery,
  private val beforeDiscovery: suspend () -> Unit = {},
) : PhonePairing {
  override val address: StateFlow<String?> =
    store.desktop
      .map { it?.address }
      .stateIn(scope, SharingStarted.Eagerly, store.desktop.value?.address)

  override val computers: StateFlow<List<SavedComputer>> =
    combine(store.computers, store.desktop) { list, active -> saved(list, active) }
      .stateIn(scope, SharingStarted.Eagerly, saved(store.computers.value, store.desktop.value))

  private val _adding = MutableStateFlow(false)
  override val adding: StateFlow<Boolean> = _adding.asStateFlow()

  override fun showPairingScreen(show: Boolean) {
    _adding.value = show && store.desktop.value != null
  }

  override fun discover(): Flow<List<FoundComputer>> = flow {
    beforeDiscovery()
    emitAll(discovery.announced())
  }
    .onEach { list ->
      // A saved computer on a new address: follow it.
      list.forEach { found ->
        val saved = store.computers.value.firstOrNull { it.id == found.id }
        if (saved != null && saved.address != found.address) {
          store.updateAddress(found.id, found.ip, found.port)
        }
      }
    }
    .map { list ->
      list.map { found ->
        FoundComputer(
          id = found.id,
          name = found.name,
          address = found.address,
          savedKey = store.computers.value.firstOrNull { it.id == found.id }?.key,
        )
      }
    }

  override suspend fun pair(code: String): PairingError? {
    val desktop =
      when (val parsed = PairedDesktop.fromCode(code)) {
        is PairedDesktop.Parsed.Ok -> parsed.desktop
        PairedDesktop.Parsed.NotACode -> return PairingError.NOT_A_PAIRING_CODE
        PairedDesktop.Parsed.NotOnLan -> return PairingError.NOT_A_LAN_ADDRESS
      }
    if (!verify(desktop)) return PairingError.DESKTOP_DID_NOT_ANSWER
    val health = approvals.health(desktop.ip, desktop.port)
    saveActive(desktop.copy(id = health?.id, name = health?.name))
    return null
  }

  override suspend fun requestApproval(address: String, onCode: (String) -> Unit): PairingError? {
    val (host, port) = parseAddress(address) ?: return PairingError.NOT_A_PAIRING_CODE
    val ip = LanAddress.parse(host) ?: return PairingError.NOT_A_LAN_ADDRESS
    return when (val outcome = approvals.request(ip, port, onCode)) {
      is ApprovalPairing.Outcome.Failed -> outcome.error
      is ApprovalPairing.Outcome.Approved -> {
        if (!verify(outcome.desktop)) return PairingError.DESKTOP_DID_NOT_ANSWER
        saveActive(outcome.desktop)
        null
      }
    }
  }

  override suspend fun scanCode(): String? = scanCode.invoke()

  override fun select(key: String) {
    store.select(key)
    _adding.value = false
  }

  override fun forget(key: String) = store.remove(key)

  private fun saveActive(desktop: PairedDesktop) {
    store.save(desktop)
    _adding.value = false
  }

  private fun saved(list: List<PairedDesktop>, active: PairedDesktop?) = list.map {
    SavedComputer(it.key, it.name, it.address, it.key == active?.key)
  }

  companion object {
    private val WITH_PORT = Regex("""^\[?([0-9A-Fa-f:.]+?)]?:(\d{1,5})$""")
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * `host` and port from what the user typed: `192.168.1.20:47821`, `192.168.1.20` (the default
     * port), `[fe80::1]:47821` or a pasted `http://…/` address. `null` when it is none of these.
     */
    fun parseAddress(text: String): Pair<String, Int>? {
      val trimmed = text.trim().removePrefix("http://").trimEnd('/')
      if (IPV4.matches(trimmed)) return trimmed to ServerSettings.DEFAULT_PORT
      if (trimmed.contains(':') && !trimmed.contains('.') && !trimmed.startsWith("[")) {
        // A bare IPv6 literal has no port.
        return trimmed to ServerSettings.DEFAULT_PORT
      }
      val match = WITH_PORT.matchEntire(trimmed) ?: return null
      val port = match.groupValues[2].toInt().takeIf(PairedDesktop::isPort) ?: return null
      return match.groupValues[1] to port
    }
  }
}
