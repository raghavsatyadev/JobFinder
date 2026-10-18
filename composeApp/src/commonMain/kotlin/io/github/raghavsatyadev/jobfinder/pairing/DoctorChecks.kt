package io.github.raghavsatyadev.jobfinder.pairing

import io.github.raghavsatyadev.jobfinder.shared.api.HealthResponse
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckKind
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckState
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorProblem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The phone's local network, as the platform sees it. */
interface LocalLink {
  /** On Wi-Fi or Ethernet. */
  val onLan: Boolean

  /** `true` when [ip] is inside one of the phone's own subnets. */
  fun contains(ip: LanIp): Boolean
}

enum class PortResult {
  OPEN,
  TIMEOUT,
  REFUSED,
}

/** What the token check found. */
enum class TokenResult {
  ACCEPTED,
  REJECTED,
  ERROR,
}

/** The doctor's steps; each network step is a function the platform gives. */
class DoctorChecks(
  private val store: PairingStorage,
  private val link: () -> LocalLink,
  private val discovery: LanDiscovery,
  private val probe: suspend (LanIp, Int) -> PortResult,
  private val health: suspend (LanIp, Int) -> HealthResponse?,
  private val token: suspend (PairedDesktop) -> TokenResult,
) {
  fun run(): Flow<List<DoctorCheck>> = flow {
    val checks =
      DoctorCheckKind.entries
        .associateWith { DoctorCheck(it, DoctorCheckState.WAITING) }
        .toMutableMap()
    suspend fun set(
      kind: DoctorCheckKind,
      state: DoctorCheckState,
      problem: DoctorProblem? = null,
    ) {
      checks[kind] = DoctorCheck(kind, state, problem)
      emit(DoctorCheckKind.entries.map { checks.getValue(it) })
    }
    suspend fun skipRest(from: DoctorCheckKind) {
      DoctorCheckKind.entries
        .filter { it.ordinal >= from.ordinal }
        .forEach {
          if (checks.getValue(it).state == DoctorCheckState.WAITING)
            set(it, DoctorCheckState.SKIPPED)
        }
    }
    var desktop = store.desktop.value ?: return@flow

    set(DoctorCheckKind.WIFI, DoctorCheckState.RUNNING)
    val local = link()
    if (!local.onLan) {
      set(DoctorCheckKind.WIFI, DoctorCheckState.FAILED, DoctorProblem.WIFI_OFF)
      skipRest(DoctorCheckKind.SAME_NETWORK)
      return@flow
    }
    set(DoctorCheckKind.WIFI, DoctorCheckState.PASSED)

    // Look for the computer first: a new address changes the checks after it.
    set(DoctorCheckKind.FOUND, DoctorCheckState.RUNNING)
    val announced = desktop.id?.let { discovery.find(it) }
    var moved = false
    if (announced != null && announced.address != desktop.address) {
      store.updateAddress(announced.id, announced.ip, announced.port)
      desktop = store.desktop.value ?: return@flow
      moved = true
    }

    set(DoctorCheckKind.PORT, DoctorCheckState.RUNNING)
    val port = probe(desktop.ip, desktop.port)
    // A refusal comes from the computer itself, so the network path works.
    val reachable = port != PortResult.TIMEOUT
    // Another subnet or no announcement only matters when the computer can't be reached.
    set(
      DoctorCheckKind.SAME_NETWORK,
      when {
        local.contains(desktop.ip) -> DoctorCheckState.PASSED
        reachable -> DoctorCheckState.WARNING
        else -> DoctorCheckState.FAILED
      },
      DoctorProblem.OTHER_NETWORK.takeUnless { local.contains(desktop.ip) },
    )
    when {
      announced != null ->
        set(
          DoctorCheckKind.FOUND,
          DoctorCheckState.PASSED,
          DoctorProblem.ADDRESS_UPDATED.takeIf { moved },
        )
      reachable -> set(DoctorCheckKind.FOUND, DoctorCheckState.WARNING, DoctorProblem.NOT_ANNOUNCED)
      else -> set(DoctorCheckKind.FOUND, DoctorCheckState.FAILED, DoctorProblem.NOT_ANNOUNCED)
    }
    when (port) {
      PortResult.OPEN -> set(DoctorCheckKind.PORT, DoctorCheckState.PASSED)
      PortResult.TIMEOUT -> {
        set(DoctorCheckKind.PORT, DoctorCheckState.FAILED, DoctorProblem.PORT_TIMEOUT)
        skipRest(DoctorCheckKind.APP)
        return@flow
      }
      PortResult.REFUSED -> {
        set(DoctorCheckKind.PORT, DoctorCheckState.FAILED, DoctorProblem.PORT_REFUSED)
        skipRest(DoctorCheckKind.APP)
        return@flow
      }
    }

    set(DoctorCheckKind.APP, DoctorCheckState.RUNNING)
    val answer = health(desktop.ip, desktop.port)
    val problem =
      when {
        answer == null -> DoctorProblem.NOT_JOBFINDER
        desktop.id != null && answer.id != null && answer.id != desktop.id ->
          DoctorProblem.OTHER_COMPUTER
        else -> null
      }
    if (problem != null) {
      set(DoctorCheckKind.APP, DoctorCheckState.FAILED, problem)
      skipRest(DoctorCheckKind.TOKEN)
      return@flow
    }
    set(DoctorCheckKind.APP, DoctorCheckState.PASSED)

    set(DoctorCheckKind.TOKEN, DoctorCheckState.RUNNING)
    when (token(desktop)) {
      TokenResult.ACCEPTED -> set(DoctorCheckKind.TOKEN, DoctorCheckState.PASSED)
      TokenResult.REJECTED ->
        set(DoctorCheckKind.TOKEN, DoctorCheckState.FAILED, DoctorProblem.TOKEN_REJECTED)
      TokenResult.ERROR ->
        set(DoctorCheckKind.TOKEN, DoctorCheckState.FAILED, DoctorProblem.APP_ERROR)
    }
  }
}
