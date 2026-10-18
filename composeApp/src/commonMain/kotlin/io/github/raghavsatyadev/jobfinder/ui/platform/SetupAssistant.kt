package io.github.raghavsatyadev.jobfinder.ui.platform

import kotlinx.coroutines.flow.StateFlow

/** The steps of setting up the computer, in the order the assistant shows them. */
enum class SetupStepKind {
  /** The API server listens on the computer's network address. */
  SERVER,
  /** Windows treats the network as Private (Public blocks the phone). */
  NETWORK,
  /** The firewall lets the phone reach the API server. */
  FIREWALL,
  /** Ollama is installed and running. */
  OLLAMA,
  /** The matching model is downloaded. */
  MODEL,
  /** At least one search is saved. */
  SEARCHES,
  /** The user signed in to the sites of their searches. */
  SIGN_IN,
  /** A phone is paired. */
  PHONE,
  /** The browser extension connected to the app at least once. */
  EXTENSION;

  /** "Set up everything" does it; the other steps need the user. */
  val automatic: Boolean
    get() = this == NETWORK || this == FIREWALL || this == OLLAMA || this == MODEL

  /** Nice to have: setup counts as done without it. The phone page is for Android users only. */
  val optional: Boolean
    get() = this == EXTENSION || page == SetupPage.PHONE

  /** The setup page that shows the step. */
  val page: SetupPage
    get() = SetupPage.entries.first { this in it.kinds }
}

/** The setup's pages, in order, and the steps on each. */
enum class SetupPage(val kinds: List<SetupStepKind>) {
  /** First: the model download takes longest, so it starts early. */
  AI(listOf(SetupStepKind.OLLAMA, SetupStepKind.MODEL)),
  SEARCHES(listOf(SetupStepKind.SEARCHES, SetupStepKind.SIGN_IN, SetupStepKind.EXTENSION)),
  PHONE(
    listOf(SetupStepKind.SERVER, SetupStepKind.NETWORK, SetupStepKind.FIREWALL, SetupStepKind.PHONE)
  ),
}

enum class SetupStepState {
  CHECKING,
  DONE,
  TODO,
  WORKING,
  FAILED,
}

/** Why a step is not done, or why its fix failed. */
enum class SetupProblem {
  /** No network address: the computer is not on Wi-Fi or a cable. */
  NO_NETWORK,
  /** Another program listens on the server's port. */
  PORT_IN_USE,
  /** The step can only be checked once the server runs. */
  NEEDS_SERVER,
  PUBLIC_NETWORK,
  NO_FIREWALL_RULE,
  /** A firewall rule blocks this app. */
  BLOCKED_BY_FIREWALL,
  /** The user said no to Windows' permission prompt. */
  PERMISSION_DECLINED,
  OLLAMA_MISSING,
  OLLAMA_STOPPED,
  INSTALL_FAILED,
  DOWNLOAD_FAILED,
  /** Signing in waits for a saved search: it opens the sites the searches use. */
  NEEDS_SEARCH,
  /** On a done step: an online model answers, so nothing has to be installed. */
  NOT_NEEDED_ONLINE,
}

/**
 * One step. While a download runs, [doneBytes] of [totalBytes] are done (both `null` otherwise).
 */
data class SetupStep(
  val kind: SetupStepKind,
  val state: SetupStepState,
  val problem: SetupProblem? = null,
  val doneBytes: Long? = null,
  val totalBytes: Long? = null,
)

/**
 * The desktop's setup assistant: checks each step and fixes what it can in the background, after
 * the user confirms once. Bound on desktop only (resolve it with `getOrNull()`).
 */
interface SetupAssistant {
  /** The assistant is on screen. */
  val visible: StateFlow<Boolean>

  val steps: StateFlow<List<SetupStep>>

  /** The computer's name, as phones list it. */
  val computerName: String

  /** About how much the model download is, in bytes. */
  val modelBytes: Long

  fun show(show: Boolean)

  /** Checks every step again. */
  fun check()

  /** Fixes the automatic steps among [kinds] that are not done; Windows asks once at most. */
  fun fix(kinds: Set<SetupStepKind>)

  /** The user's sign-ins were copied from their own browser: the sign-in step is done. */
  fun markSignedIn()

  /**
   * The step's own action: fixes an automatic step, opens the sign-in browser, or goes to the
   * searches.
   */
  fun act(kind: SetupStepKind)
}
