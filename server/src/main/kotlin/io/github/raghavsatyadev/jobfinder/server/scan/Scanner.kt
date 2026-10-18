package io.github.raghavsatyadev.jobfinder.server.scan

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Runs scans: saved links, then the descriptions of the jobs they found (the old `jobScanner`). */
interface Scanner {
  val state: StateFlow<ScanState>

  /** Starts a scan of every link unless one is running. Returns `false` if one was running. */
  fun startScanAll(): Boolean

  /** Scans one link now and returns the ids of the new jobs (old `scan-link`). */
  suspend fun scanLink(linkId: Long): List<Long>

  /** Reads one job's description now (old `scan-job-description`). Returns the updated job. */
  suspend fun scanJob(jobId: Long): Job
}

/** Server events for SSE subscribers and the desktop UI. Never suspends the sender. */
class ServerEvents {
  private val flow =
    MutableSharedFlow<ServerEvent>(
      extraBufferCapacity = BUFFER,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  val events: SharedFlow<ServerEvent> = flow.asSharedFlow()

  fun emit(event: ServerEvent) {
    flow.tryEmit(event)
  }

  private companion object {
    const val BUFFER = 64
  }
}

/** The scanner until the scan pipeline is wired: reports idle and refuses scans. */
class UnavailableScanner : Scanner {
  override val state: StateFlow<ScanState> = MutableStateFlow(ScanState())

  override fun startScanAll(): Boolean = throw unavailable()

  override suspend fun scanLink(linkId: Long): List<Long> = throw unavailable()

  override suspend fun scanJob(jobId: Long): Job = throw unavailable()

  private fun unavailable() =
    ApiException(ApiErrorCode.UNAVAILABLE, "Scanning is not available in this build yet.")
}
