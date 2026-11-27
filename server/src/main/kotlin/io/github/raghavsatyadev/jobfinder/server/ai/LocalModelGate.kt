package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Lets one call at a time reach the model on this computer, so Ollama never swaps models mid-scan.
 * Work the user is waiting on (a form answer they asked for) goes ahead of background work (ATS
 * rechecks, matching, tips): it waits only for the call already running, not for the whole queue.
 */
class LocalModelGate {
  private val lock = Any()
  private var busy = false
  private val urgent = ArrayDeque<CompletableDeferred<Unit>>()
  private val background = ArrayDeque<CompletableDeferred<Unit>>()

  suspend fun <T> withTurn(task: AiTask?, block: suspend () -> T): T {
    enter(task != null && task in URGENT)
    try {
      return block()
    } finally {
      leave()
    }
  }

  private suspend fun enter(first: Boolean) {
    val turn =
      synchronized(lock) {
        if (!busy) {
          busy = true
          return
        }
        CompletableDeferred<Unit>().also { (if (first) urgent else background).addLast(it) }
      }
    try {
      turn.await()
    } catch (e: CancellationException) {
      // Still queued: drop out. Already handed the turn: pass it on.
      val queued = synchronized(lock) { urgent.remove(turn) || background.remove(turn) }
      if (!queued) leave()
      throw e
    }
  }

  private fun leave() {
    val next =
      synchronized(lock) {
        (urgent.removeFirstOrNull() ?: background.removeFirstOrNull()).also {
          if (it == null) busy = false
        }
      }
    next?.complete(Unit)
  }

  private companion object {
    /** Tasks a person is waiting on right now. */
    val URGENT = setOf(AiTask.ANSWERS)
  }
}
