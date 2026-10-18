package io.github.raghavsatyadev.jobfinder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.notification_new_jobs_channel
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

/**
 * A notification for each batch of new jobs the paired computer reports over its event stream,
 * while the app process runs. Tapping it opens the jobs list.
 */
class NewJobsNotifier(
  private val context: Context,
  private val scan: ScanRepository,
  private val scope: CoroutineScope,
) {
  private var job: Job? = null

  fun start() {
    if (job != null) return
    job = scope.launch {
      scan.events.filterIsInstance<ServerEvent.NewJobs>().collect { event ->
        if (event.jobIds.isNotEmpty()) notify(event.jobIds.size)
      }
    }
  }

  private suspend fun notify(count: Int) {
    if (
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    ) {
      return
    }
    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        getString(Res.string.notification_new_jobs_channel),
        NotificationManager.IMPORTANCE_DEFAULT,
      )
    )
    val open =
      Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_JOBS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val notification =
      NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getPluralString(Res.plurals.notification_new_jobs_title, count, count))
        .setContentText(getString(Res.string.notification_new_jobs_text))
        .setNumber(count)
        .setAutoCancel(true)
        .setContentIntent(
          PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
          )
        )
        .build()
    manager.notify(NOTIFICATION_ID, notification)
  }

  private companion object {
    const val CHANNEL_ID = "new_jobs"
    const val NOTIFICATION_ID = 1
  }
}
