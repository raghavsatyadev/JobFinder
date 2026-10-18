package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.scan.Scanner
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Keeps the New tab true to the AI filter: in the background, checks again each untouched job in
 * `new` that the current filter has not answered for (the check failed, or the user changed the
 * prompt, the blacklist or the AI instructions). A job without a description has its page read
 * again first, and is filtered out when that finds none. Waits while AI work is paused or a scan
 * runs; jobs the user labelled or put in New by hand are left alone.
 */
class NewJobsRecheck(
  private val jobs: JobsDao,
  private val matching: AdvancedMatchingFilter,
  private val scanner: Scanner?,
  private val events: ServerEvents,
  private val pause: AiWorkPause = AiWorkPause.NEVER,
  private val clock: () -> Long = System::currentTimeMillis,
  private val gap: Duration = 2.seconds,
) {
  private val log = LoggerFactory.getLogger(NewJobsRecheck::class.java)

  /** Jobs whose check failed, and until when they are passed over. */
  private val passOver = ConcurrentHashMap<Long, Long>()

  fun start(scope: CoroutineScope) {
    scope.launch {
      delay(START)
      var moved = 0
      while (true) {
        pause.awaitResumed()
        if (scanner?.state?.value?.scanning == true) {
          delay(BUSY_WAIT)
          continue
        }
        val next =
          try {
            val now = clock()
            passOver.entries.removeIf { it.value <= now }
            jobs.nextToRecheck(matching.key(), passOver.keys.toList())
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            log.warn("Choosing the next job to check again failed: {}", e.message)
            null
          }
        if (next == null) {
          if (moved > 0) announce(moved).also { moved = 0 }
          delay(IDLE)
          continue
        }
        try {
          if (!recheck(next.id)) passOver[next.id] = clock() + FAILED_WAIT.inWholeMilliseconds
          else if (jobs.get(next.id)?.status != JobStatus.NEW.wire) moved++
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Checking job {} again failed: {}", next.id, e.message)
          passOver[next.id] = clock() + FAILED_WAIT.inWholeMilliseconds
        }
        if (moved >= ANNOUNCE_EVERY) announce(moved).also { moved = 0 }
        delay(gap)
      }
    }
  }

  /** Checks job [id] again; `false` when the filter could not answer. */
  private suspend fun recheck(id: Long): Boolean {
    val job = jobs.get(id)?.toModel() ?: return true
    if (job.description.isNullOrBlank()) {
      // Reading the page again stores the description and runs the filter.
      val read =
        try {
          scanner?.scanJob(id)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.info("Reading job {} again failed: {}", id, e.message)
          null
        }
      if (read != null && jobs.get(id)?.matchedWith != null) return true
      if (jobs.get(id)?.status != JobStatus.NEW.wire) return true
      jobs.updateMatch(
        id,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING.wire,
        AdvancedMatchingFilter.NO_DESCRIPTION,
        matching.key(),
        clock(),
      )
      log.info("Filtered out job {}: no description", id)
      return true
    }
    val decision = matching.evaluate(job)
    val key = decision.checkedWith ?: return false
    jobs.updateMatch(id, decision.status.wire, decision.excludeReason, key, clock())
    if (decision.status != JobStatus.NEW) log.info("Filtered out job {} on a second check", id)
    return true
  }

  /** Open lists read again; an empty `NewJobs` notifies no one. */
  private fun announce(moved: Int) {
    log.info("{} jobs left New after a second check", moved)
    events.emit(ServerEvent.NewJobs(emptyList()))
  }

  private companion object {
    val START = 2.minutes
    val IDLE = 5.minutes
    val BUSY_WAIT = 1.minutes
    val FAILED_WAIT = 1.hours
    const val ANNOUNCE_EVERY = 5
  }
}
