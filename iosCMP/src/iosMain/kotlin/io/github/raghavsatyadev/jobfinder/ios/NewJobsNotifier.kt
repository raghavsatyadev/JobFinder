package io.github.raghavsatyadev.jobfinder.ios

import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.notification_new_jobs_text
import io.github.raghavsatyadev.jobfinder.resources.notification_new_jobs_title
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * A notification for each batch of new jobs the paired computer reports over its event stream. iOS
 * suspends the app soon after it leaves the screen, so this works while the app is open or was just
 * closed; background push would need Apple's push service.
 */
class NewJobsNotifier(private val scan: ScanRepository, private val scope: CoroutineScope) {
  private var job: Job? = null

  fun start() {
    if (job != null) return
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
      UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
    ) { _, _ ->
    }
    job = scope.launch {
      scan.events.filterIsInstance<ServerEvent.NewJobs>().collect { event ->
        if (event.jobIds.isNotEmpty()) notify(event.jobIds.size)
      }
    }
  }

  private suspend fun notify(count: Int) {
    val content =
      UNMutableNotificationContent().apply {
        setTitle(getPluralString(Res.plurals.notification_new_jobs_title, count, count))
        setBody(getString(Res.string.notification_new_jobs_text))
        setSound(UNNotificationSound.defaultSound)
      }
    val request =
      UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content = content, trigger = null)
    UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
  }

  private companion object {
    const val IDENTIFIER = "new_jobs"
  }
}
