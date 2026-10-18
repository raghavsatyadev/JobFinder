package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import kotlin.coroutines.cancellation.CancellationException
import org.slf4j.LoggerFactory

/** Where a scanned job goes after its description is read. */
data class MatchDecision(val status: JobStatus, val excludeReason: String? = null) {
  companion object {
    val KEEP = MatchDecision(JobStatus.NEW)
  }
}

/** The model's answer: the old `JobExclusion` schema. */
data class JobExclusion(val excluded: Boolean, val reason: String? = null)

/**
 * Asks the local LLM whether the user's matching prompt excludes a job. May throw;
 * [AdvancedMatchingFilter] fails open.
 */
fun interface LlmJobCheck {
  suspend fun check(prompt: String, job: Job): JobExclusion
}

/**
 * The old `applyAdvancedMatchingFilters`: blacklisted companies first (exact, case-insensitive
 * match of the whole name), then the LLM check when the job has a description and the user wrote a
 * prompt. Any LLM failure keeps the job.
 */
class AdvancedMatchingFilter(
  private val matching: MatchingRepository,
  private val llm: LlmJobCheck? = null,
) {
  private val log = LoggerFactory.getLogger(AdvancedMatchingFilter::class.java)

  suspend fun evaluate(job: Job): MatchDecision {
    val config = matching.getConfig()
    if (isExcludedCompany(job.companyName, config.blacklistedCompanies)) {
      return MatchDecision(
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
        "${job.companyName}$BLACKLISTED",
      )
    }
    if (llm == null || job.description.isNullOrEmpty() || config.prompt.isEmpty()) {
      return MatchDecision.KEEP
    }
    return try {
      val answer = llm.check(config.prompt, job)
      if (answer.excluded) {
        MatchDecision(JobStatus.EXCLUDED_BY_ADVANCED_MATCHING, answer.reason?.ifBlank { null })
      } else {
        MatchDecision.KEEP
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error("Advanced matching failed for job {}, keeping it", job.id, e)
      MatchDecision.KEEP
    }
  }

  companion object {
    /** The end of a blacklist exclusion's reason, after the company name. */
    const val BLACKLISTED = " is blacklisted."

    fun isExcludedCompany(companyName: String, blacklist: List<String>): Boolean {
      val name = companyName.lowercase()
      return blacklist.any { it.lowercase() == name }
    }
  }
}
