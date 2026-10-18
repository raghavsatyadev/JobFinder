package io.github.raghavsatyadev.jobfinder.ui.jobs

import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Fixed jobs-screen states from [SampleData], for previews and screenshot tests (which draw one
 * frame, before a ViewModel could load anything).
 */
object JobsPreviewData {
  /** The time the sample data is "now" at, so relative times never change. */
  const val NOW: Long = SampleData.NOW

  private val counts = SampleData.jobs.groupingBy { it.status }.eachCount().toImmutableMap()

  /** The [status] tab with its sample jobs (without descriptions, as lists load them). */
  fun list(
    status: JobStatus = JobStatus.NEW,
    scan: ScanState = ScanState(),
  ): JobsListUiState =
    JobsListUiState(
      status = status,
      counts = counts,
      jobs =
        SampleData.jobs
          .filter { it.status == status }
          .sortedByDescending { it.updatedAt }
          .map { it.copy(description = null) }
          .toImmutableList(),
      loading = false,
      sites = SampleData.sites.toImmutableList(),
      links = SampleData.links.toImmutableList(),
      scan = scan,
    )

  fun empty(): JobsListUiState =
    JobsListUiState(
      jobs = persistentListOf(),
      loading = false,
    )

  fun loading(): JobsListUiState = JobsListUiState()

  fun error(): JobsListUiState = JobsListUiState(loading = false, failed = true)

  /** Job [jobId] fully loaded, with its board, search and notes. */
  fun detail(jobId: Long): JobDetailUiState {
    val job = SampleData.jobs.first { it.id == jobId }
    val site = SampleData.sites.firstOrNull { it.id == job.siteId }
    return JobDetailUiState(
      jobId = jobId,
      job = job,
      loading = false,
      siteName = site?.name,
      siteLogoKey = site?.logoKey,
      linkTitle = SampleData.links.firstOrNull { it.id == job.linkId }?.title,
      notes = SampleData.notes.filter { it.jobId == jobId }.toImmutableList(),
      ats =
        if (jobId == SampleData.review.jobId) AtsStatus(AtsState.READY, SampleData.review)
        else AtsStatus(AtsState.QUEUED),
    )
  }
}
