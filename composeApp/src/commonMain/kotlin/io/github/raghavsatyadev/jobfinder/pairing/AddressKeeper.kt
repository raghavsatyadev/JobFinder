package io.github.raghavsatyadev.jobfinder.pairing

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Finds the active computer again when it can't be reached: while disconnected, it looks for the
 * computer's id on the network now and then, and saves the new address when the computer moved (a
 * new DHCP lease).
 */
class AddressKeeper(
  private val store: PairingStorage,
  private val connected: StateFlow<Boolean>,
  private val discovery: LanDiscovery,
  /** Whether a background search is allowed now (no system sheet would show). */
  private val allowed: () -> Boolean = { true },
  private val grace: Duration = 10.seconds,
  private val every: Duration = 1.minutes,
) {
  fun start(scope: CoroutineScope): Job = scope.launch {
    combine(connected, store.desktop) { up, desktop -> desktop?.id.takeUnless { up } }
      .distinctUntilChanged()
      .collectLatest { id ->
        if (id == null) return@collectLatest
        delay(grace)
        while (true) {
          val found = if (allowed()) discovery.find(id) else null
          val current = store.desktop.value
          if (found != null && current?.id == id && found.address != current.address) {
            store.updateAddress(id, found.ip, found.port)
          }
          delay(every)
        }
      }
  }
}
