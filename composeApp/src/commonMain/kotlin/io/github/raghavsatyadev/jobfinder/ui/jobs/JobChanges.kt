package io.github.raghavsatyadev.jobfinder.ui.jobs

import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** A change to one job, made by one jobs screen and shown by the others. */
sealed interface JobChange {
  val jobId: Long

  /** The job moved from [from] to [to]. */
  data class StatusChanged(override val jobId: Long, val from: JobStatus, val to: JobStatus) :
    JobChange

  /** The job's labels (or other fields) changed; [job] is the new copy. */
  data class Updated(val job: Job) : JobChange {
    override val jobId: Long
      get() = job.id
  }

  /** Many jobs moved at once (archive all, delete all). */
  data class BulkChanged(val from: JobStatus, val to: JobStatus) : JobChange {
    override val jobId: Long
      get() = -1
  }
}

/**
 * Keeps the job list and the job detail in step: the detail archives a job and the list drops it,
 * the list archives the open job and the detail shows its new status.
 */
class JobChanges {
  private val changes = MutableSharedFlow<JobChange>(extraBufferCapacity = 32)

  val flow: SharedFlow<JobChange> = changes.asSharedFlow()

  fun publish(change: JobChange) {
    changes.tryEmit(change)
  }
}

/** The current time in epoch milliseconds, for "2 hours ago". Fixed in screenshots and tests. */
fun interface JobsClock {
  fun now(): Long
}
