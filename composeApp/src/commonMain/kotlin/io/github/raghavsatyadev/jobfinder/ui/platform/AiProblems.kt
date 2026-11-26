package io.github.raghavsatyadev.jobfinder.ui.platform

import io.github.raghavsatyadev.jobfinder.shared.model.AiProblem
import kotlinx.coroutines.flow.StateFlow

/**
 * The AI models that keep failing (a refused or expired key, a source that errors again and again),
 * for the warning over the main window. Only the desktop binds it: the AI runs there.
 */
interface AiProblems {
  /** The models with a problem now, the oldest first; empty when all work. */
  val problems: StateFlow<List<AiProblem>>

  /** Until when (epoch ms) the warning stays hidden; 0 when it is not muted. */
  val mutedUntil: StateFlow<Long>

  /** Hides the warning for a day. Settings still shows the problems. */
  fun muteForDay()
}
