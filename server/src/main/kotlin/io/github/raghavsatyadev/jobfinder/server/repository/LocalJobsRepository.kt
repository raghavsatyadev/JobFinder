package io.github.raghavsatyadev.jobfinder.server.repository

import io.github.raghavsatyadev.jobfinder.server.db.JobQueries
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.JsonColumnConverters
import io.github.raghavsatyadev.jobfinder.server.db.PageToken
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobCounts
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobPage
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository

/** [JobsRepository] over the local Room database. */
class LocalJobsRepository(
  private val dao: JobsDao,
  private val clock: () -> Long = System::currentTimeMillis,
  /** The job's ATS score against the current resume; none when `null`. */
  private val atsSummary: suspend (Long) -> AtsSummary? = { null },
) : JobsRepository {
  override suspend fun listJobs(
    status: JobStatus,
    filter: JobFilter,
    limit: Int,
    after: String?,
  ): JobPage {
    require(limit > 0) { "limit must be positive: $limit" }
    val cursor = after?.let(PageToken::decode)
    // One extra row tells whether another page exists, so an exactly-full last page has no token.
    val rows = dao.listRows(JobQueries.list(status, filter, limit + 1, cursor))
    val page = rows.take(limit).map { it.toModel() }
    val sort = filter.sort
    val next =
      if (rows.size > limit)
        page.last().let {
          PageToken(it.id, it.updatedAt, if (sort == JobSort.LATEST) null else sort.key(it))
            .encode()
        }
      else null
    return JobPage(page, next)
  }

  override suspend fun countJobs(filter: JobFilter, status: JobStatus?): JobCounts {
    val counts = mutableMapOf<JobStatus, Int>()
    for (row in dao.countRows(JobQueries.count(filter, status))) {
      val key = JobStatus.fromWire(row.status)
      counts[key] = (counts[key] ?: 0) + row.count.toInt()
    }
    return JobCounts(counts)
  }

  override suspend fun getJob(id: Long): Job? =
    dao.get(id)?.toModel()?.let { it.copy(ats = atsSummary(id)) }

  override suspend fun updateStatus(id: Long, status: JobStatus) {
    dao.updateStatus(id, status.wire, clock())
  }

  override suspend fun updateLabels(id: Long, labels: List<JobLabel>): Job? {
    dao.updateLabels(id, JsonColumnConverters.encodeStringList(labels.map { it.wire }))
    return getJob(id)
  }

  override suspend fun changeAllStatuses(from: JobStatus, to: JobStatus): Int =
    dao.changeAllStatuses(from.wire, to.wire, clock())
}
