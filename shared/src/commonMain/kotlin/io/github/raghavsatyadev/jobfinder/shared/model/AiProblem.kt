package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/** Why a model source no longer does its work. Busy or out of quota for now is never a problem. */
@Serializable
enum class AiProblemKind {
  /** The site refused the key (HTTP 401 or 403): wrong, revoked or expired there. */
  KEY_REFUSED,
  /** The key's expiry date has passed; the work skips it. */
  KEY_EXPIRED,
  /** Several calls in a row failed (no answer, an error, a CLI that stopped working). */
  FAILING,
}

/**
 * A model source that keeps failing: an added key ([routeId] is its id), a CLI
 * ([AiRoute.OPEN_CODE_ID], [AiRoute.AGY_ID]) or this computer ([LOCAL_ID]). It clears by itself at
 * the next answer.
 */
@Serializable
data class AiProblem(
  val routeId: String,
  /** The key's label or the site's name, e.g. "Groq". */
  val name: String,
  val kind: AiProblemKind,
  /** The last error as the site gave it, without the key. */
  val detail: String = "",
  /** When the problem began. */
  val since: Long = 0,
  /** Failed calls in a row. */
  val failures: Int = 1,
) {
  companion object {
    /** The route id of the main AI choice, usually this computer. */
    const val LOCAL_ID = "local"
  }
}
