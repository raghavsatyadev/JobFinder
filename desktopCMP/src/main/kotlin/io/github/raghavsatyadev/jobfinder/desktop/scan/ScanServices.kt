package io.github.raghavsatyadev.jobfinder.desktop.scan

import io.github.raghavsatyadev.jobfinder.desktop.browser.CefBrowserPool
import io.github.raghavsatyadev.jobfinder.desktop.system.SleepBlocker
import io.github.raghavsatyadev.jobfinder.desktop.system.TrayIcon
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.JobScanner
import io.github.raghavsatyadev.jobfinder.server.scan.ScanScheduler
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.BackupService
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Everything that runs scans without the user asking: the cron schedule, the sleep blocker, the
 * browser pool size, the "new jobs" notification and, once at startup, the jobs a previous run left
 * in `processing`, and the daily automatic backup. All of it follows the scanner settings, whoever
 * changes them.
 */
class ScanServices(
  private val scanner: JobScanner,
  private val settings: LocalSettingsRepository,
  private val events: ServerEvents,
  private val db: ServerDatabase,
  private val pool: CefBrowserPool,
  private val sleep: SleepBlocker,
  private val tray: TrayIcon,
  /** Saves a full backup once a day, keeping the last 7. */
  private val backups: BackupService? = null,
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(ScanServices::class.java)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun start() {
    ScanScheduler(scanner, settings.scannerSettings.map { it.cronRule }).start(scope)
    scope.launch {
      settings.scannerSettings.collect {
        sleep.set(it.preventSleep)
        pool.resize(it.scraperConcurrency)
      }
    }
    scope.launch {
      events.events.filterIsInstance<ServerEvent.NewJobs>().collect { notifyNewJobs(it.jobIds) }
    }
    backups?.let { service ->
      scope.launch {
        while (true) {
          try {
            service.automaticBackup()?.let { log.info("Saved the daily backup {}", it.fileName) }
          } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.error("Could not save the daily backup", e)
          }
          delay(BACKUP_CHECK_MS)
        }
      }
    }
    scanner.resumeProcessing()
  }

  private suspend fun notifyNewJobs(ids: List<Long>) {
    try {
      val jobs = ids.take(NewJobsMessage.SHOWN_JOBS).mapNotNull { db.jobsDao().get(it)?.toModel() }
      val text = NewJobsMessage.text(jobs, ids.size) ?: return
      tray.notify(NewJobsMessage.TITLE, text, sound = settings.getScannerSettings().useSound)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error("Could not show the new jobs notification", e)
    }
  }

  private companion object {
    /** How often the daily backup checks whether a day has passed. */
    const val BACKUP_CHECK_MS = 60L * 60 * 1000
  }

  override fun close() {
    scope.cancel()
    scanner.close()
    sleep.close()
    tray.close()
  }
}

/** The text of the "new jobs" notification, as the old app wrote it. */
object NewJobsMessage {
  const val TITLE = "Job Search Update"

  /** Jobs named in the notification; the rest are counted. */
  const val SHOWN_JOBS = 3

  /**
   * "Kotlin dev at Acme, Android dev at Beta and 2 others are now available!" for [shown] (the
   * first jobs) out of [total] new jobs. `null` when there are none.
   */
  fun text(shown: List<Job>, total: Int): String? {
    if (shown.isEmpty()) return null
    val named = shown.take(SHOWN_JOBS)
    val names = named.joinToString(", ") { "${it.title} at ${it.companyName}" }
    val others = total - named.size
    val plural = if (others > 1) "s" else ""
    val rest = if (others > 0) " and $others other$plural" else ""
    val verb = if (named.size > 1) "are" else "is"
    return "$names$rest $verb now available!"
  }
}
