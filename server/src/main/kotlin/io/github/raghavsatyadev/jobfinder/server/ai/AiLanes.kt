package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One lane per kind of work and model source (an added key or a CLI): a lane takes one request at a
 * time and then rests a short random gap, like a person would. While one lane rests, the next job
 * of the same kind goes to the next free lane in that work's list, so each kind of work runs on all
 * its sources at once. The per-minute and per-day quotas stay per key ([AiKeyPool.take]).
 */
class AiLanes(
  private val clock: () -> Long = System::currentTimeMillis,
  private val random: Random = Random.Default,
) {
  private class Lane(var busy: Boolean = false, var freeAt: Long = 0)

  private val lock = Mutex()
  private val lanes = HashMap<String, Lane>()

  /** Changes whenever a lane frees, to wake calls waiting for one. */
  private val changes = MutableStateFlow(0L)

  /** Takes [task]'s lane on [routeId] when it is free and rested; `false` otherwise. */
  suspend fun tryEnter(task: AiTask, routeId: String): Boolean = lock.withLock {
    val lane = lanes.getOrPut(id(task, routeId)) { Lane() }
    if (lane.busy || lane.freeAt > clock()) return false
    lane.busy = true
    true
  }

  /**
   * Frees the lane. After a request ([used]) it rests a random gap; one that never sent anything
   * (out of quota) is free at once.
   */
  suspend fun leave(task: AiTask, routeId: String, used: Boolean, requestsPerMinute: Int?) {
    lock.withLock {
      val lane = lanes.getOrPut(id(task, routeId)) { Lane() }
      lane.busy = false
      lane.freeAt = if (used) clock() + gapMs(routeId, requestsPerMinute) else 0
    }
    changes.value = changes.value + 1
  }

  /** Waits until a lane frees or rests out, at most [maxMs]. */
  suspend fun awaitChange(maxMs: Long) {
    val seen = changes.value
    val soonest = lock.withLock {
      lanes.values.filter { !it.busy && it.freeAt > clock() }.minOfOrNull { it.freeAt - clock() }
    }
    val wait = (soonest ?: maxMs).coerceIn(MIN_WAIT_MS, maxMs)
    withTimeoutOrNull(wait) { changes.first { it != seen } }
  }

  /**
   * The rest after a request: a key paces itself to its per-minute quota (at least [MIN_GAP_MS]),
   * then a random extra so the requests don't come like a clock. Antigravity rests longest.
   */
  private fun gapMs(routeId: String, requestsPerMinute: Int?): Long {
    val base =
      when (routeId) {
        AiRoute.AGY_ID -> AGY_GAP_MS
        AiRoute.OPEN_CODE_ID -> CLI_GAP_MS
        else -> maxOf(MIN_GAP_MS, requestsPerMinute?.let { MINUTE_MS / it } ?: MIN_GAP_MS)
      }
    return base + random.nextLong(0, base + 1)
  }

  private fun id(task: AiTask, routeId: String) = "${task.name}/$routeId"

  private companion object {
    const val MINUTE_MS = 60_000L
    const val MIN_GAP_MS = 2_000L
    const val CLI_GAP_MS = 3_000L
    const val AGY_GAP_MS = 30_000L
    const val MIN_WAIT_MS = 200L
  }
}
