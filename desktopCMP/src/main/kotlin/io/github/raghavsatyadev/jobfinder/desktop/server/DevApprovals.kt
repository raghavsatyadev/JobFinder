package io.github.raghavsatyadev.jobfinder.desktop.server

import io.github.raghavsatyadev.jobfinder.server.http.PairingRequests
import io.github.raghavsatyadev.jobfinder.server.http.RestoreRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Dev runs only: allows every phone pairing and restore request without a click, so an agent can
 * pair the emulator with the isolated dev desktop. Turned on by `./gradlew :desktopCMP:run
 * -Pf2aDevAutoApprove=true`; the packaged app never honours it.
 */
object DevApprovals {
  const val PROPERTY = "jobfinder.dev.autoApprove"

  /** jpackage's launcher sets this; `gradle run` doesn't. */
  private const val PACKAGED = "jpackage.app-path"

  fun enabled(properties: (String) -> String? = System::getProperty): Boolean =
    properties(PROPERTY) == "true" && properties(PACKAGED) == null

  fun start(scope: CoroutineScope, pairing: PairingRequests, restores: RestoreRequests) {
    val log = LoggerFactory.getLogger("jobfinder.dev")
    log.warn("Dev run: phone pairing and restore requests are allowed without asking")
    scope.launch {
      pairing.pending.filterNotNull().collect {
        log.warn("Dev run: allowed pairing for {}", it.deviceName)
        pairing.approve(it.id)
      }
    }
    scope.launch {
      restores.pending.filterNotNull().collect {
        log.warn("Dev run: allowed a restore")
        restores.approve(it.id)
      }
    }
  }
}
