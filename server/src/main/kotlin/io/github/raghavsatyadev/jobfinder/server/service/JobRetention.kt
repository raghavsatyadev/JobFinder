package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.scan.GonePosting
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Keeps the jobs table lean, now and every few hours: deletes jobs the AI filtered out once they
 * are older than the user's `excludedKeepDays` (never when that is `null`), and untouched jobs
 * whose stored description says the posting was removed.
 */
class JobRetention(
  private val jobs: JobsDao,
  private val scannerSettings: suspend () -> ScannerSettings,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val log = LoggerFactory.getLogger(JobRetention::class.java)

  fun start(scope: CoroutineScope) {
    scope.launch {
      while (true) {
        prune()
        delay(EVERY)
      }
    }
  }

  suspend fun prune() {
    try {
      val keepDays = scannerSettings().excludedKeepDays
      if (keepDays != null) {
        val removed = jobs.deleteExcludedBefore(clock() - keepDays.days.inWholeMilliseconds)
        if (removed > 0)
          log.info("Deleted {} filtered-out jobs older than {} days", removed, keepDays)
      }
      val gone = GonePosting.likePatterns.flatMap { jobs.untouchedWithDescriptionLike(it) }.toSet()
      gone.forEach { jobs.delete(it) }
      if (gone.isNotEmpty()) log.info("Deleted {} jobs whose posting was removed", gone.size)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.warn("Cleaning up old jobs failed", e)
    }
  }

  private companion object {
    val EVERY = 6.hours
  }
}
