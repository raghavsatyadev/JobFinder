package io.github.raghavsatyadev.jobfinder.ui.platform

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Why pairing failed. */
enum class PairingError {
  /** Neither a JobFinder pairing code nor a computer address. */
  NOT_A_PAIRING_CODE,

  /** The address is not on a home or office network (only private LAN addresses). */
  NOT_A_LAN_ADDRESS,

  /** The desktop app did not answer, or refused the token. */
  DESKTOP_DID_NOT_ANSWER,

  /** The user clicked Deny on the computer. */
  DENIED,

  /** Nobody answered on the computer in time. */
  EXPIRED,

  /** The computer is answering another phone's request. */
  BUSY,
}

/**
 * A computer this phone is paired with. [key] names it to [PhonePairing.select] and
 * [PhonePairing.forget].
 */
@Immutable
data class SavedComputer(
  val key: String,
  val name: String?,
  val address: String,
  val active: Boolean,
) {
  /** The computer's name, or its address for an old pairing that has no name. */
  val label: String
    get() = name ?: address
}

/**
 * A computer announcing JobFinder on this Wi-Fi. [savedKey] is set when this phone already has it.
 */
@Immutable
data class FoundComputer(
  val id: String,
  val name: String,
  val address: String,
  val savedKey: String?,
)

/**
 * Pairing this phone with desktop apps. Android binds it in Koin; on desktop it is absent (the
 * desktop is the server). While [address] is `null`, or [adding] is set, the app shows the pairing
 * screen.
 */
interface PhonePairing {
  /** The active computer's LAN address (`192.168.1.20:8787`), or `null` when not paired. */
  val address: StateFlow<String?>

  /** `true` when the event stream to the active computer is open. */
  val connected: StateFlow<Boolean>

  /** Every saved computer; one of them is active. */
  val computers: StateFlow<List<SavedComputer>>

  /** `true` while the pairing screen is open to add another computer. */
  val adding: StateFlow<Boolean>

  /** Opens or closes the pairing screen over a paired app. */
  fun showPairingScreen(show: Boolean)

  /**
   * The computers announcing JobFinder on this Wi-Fi while collected. A saved computer found at a
   * new address has its address updated.
   */
  fun discover(): Flow<List<FoundComputer>>

  /** Checks [code] (the desktop's QR text) with the desktop, then saves it. `null` on success. */
  suspend fun pair(code: String): PairingError?

  /**
   * Asks the computer at [address] (`192.168.1.20:47821`) to pair. [onCode] gets the 6 digits to
   * compare with the computer's dialog; returns once the user allowed (`null`) or did not.
   */
  suspend fun requestApproval(address: String, onCode: (String) -> Unit): PairingError?

  /** Opens the QR code scanner; returns the scanned text, or `null` when cancelled or failed. */
  suspend fun scanCode(): String?

  /** Makes the saved computer [key] the active one. */
  fun select(key: String)

  /** Forgets the computer [key]. With none left, the app goes back to the pairing screen. */
  fun forget(key: String)
}
