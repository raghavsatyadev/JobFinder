package io.github.raghavsatyadev.jobfinder.ui.platform

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The doctor's checks, in the order shown. */
enum class DoctorCheckKind {
  /** The phone is on Wi-Fi (or Ethernet). */
  WIFI,

  /** The phone and the computer are on the same network. */
  SAME_NETWORK,

  /** The computer announces itself on the network. */
  FOUND,

  /** The computer accepts a connection on the app's port. */
  PORT,

  /** JobFinder answers there, and it is the paired computer. */
  APP,

  /** The computer still accepts this phone's token. */
  TOKEN,
}

enum class DoctorCheckState {
  WAITING,
  RUNNING,
  PASSED,

  /** Not as expected, but the connection can still work. */
  WARNING,
  FAILED,

  /** Not run, because an earlier check failed. */
  SKIPPED,
}

/** What a check found; picks the explanation and the fix. */
enum class DoctorProblem {
  WIFI_OFF,
  OTHER_NETWORK,
  NOT_ANNOUNCED,

  /** Found at a new address, which is now saved (a pass). */
  ADDRESS_UPDATED,

  /** No answer: asleep, firewall, or guest Wi-Fi that keeps devices apart. */
  PORT_TIMEOUT,

  /** The computer is on but JobFinder is closed or uses another port. */
  PORT_REFUSED,
  NOT_JOBFINDER,
  OTHER_COMPUTER,
  APP_ERROR,
  TOKEN_REJECTED,
}

@Immutable
data class DoctorCheck(
  val kind: DoctorCheckKind,
  val state: DoctorCheckState,
  val problem: DoctorProblem? = null,
)

/**
 * Finds why the phone can't reach its computer, step by step, and applies the fixes it can (a new
 * address). Bound on Android only.
 */
interface ConnectionDoctor {
  /** `true` while the doctor screen is open. */
  val visible: StateFlow<Boolean>

  fun show(visible: Boolean)

  /** Runs every check; each emission is the whole list so far. */
  fun run(): Flow<List<DoctorCheck>>

  fun openWifiSettings()
}
