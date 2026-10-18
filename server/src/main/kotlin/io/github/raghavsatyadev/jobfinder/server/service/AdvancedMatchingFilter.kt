package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import org.slf4j.LoggerFactory

/**
 * Where a scanned job goes after its description is read. [checkedWith] is the filter's
 * [AdvancedMatchingFilter.key] when the filter gave an answer, `null` when the check failed.
 */
data class MatchDecision(
  val status: JobStatus,
  val excludeReason: String? = null,
  val checkedWith: String? = null,
) {
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
 * match of the whole name), then a job without a description is filtered out, then the LLM check
 * when the user wrote a prompt. An LLM failure keeps the job, unchecked, for [NewJobsRecheck].
 * [instructions] are the AI instructions for matching, part of [key].
 */
class AdvancedMatchingFilter(
  private val matching: MatchingRepository,
  private val llm: LlmJobCheck? = null,
  private val instructions: suspend () -> String = { "" },
) {
  private val log = LoggerFactory.getLogger(AdvancedMatchingFilter::class.java)

  /** Changes whenever what the filter would answer may change: prompt, blacklist, instructions. */
  suspend fun key(): String {
    val config = matching.getConfig()
    val text =
      listOf(
          config.prompt,
          config.blacklistedCompanies.map { it.lowercase() }.sorted().joinToString("\n"),
          if (llm == null) "" else instructions(),
        )
        .joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256")
      .digest(text.toByteArray())
      .take(KEY_BYTES)
      .joinToString("") { "%02x".format(it) }
  }

  suspend fun evaluate(job: Job): MatchDecision {
    val config = matching.getConfig()
    val key = key()
    if (isExcludedCompany(job.companyName, config.blacklistedCompanies)) {
      return MatchDecision(
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
        "${job.companyName}$BLACKLISTED",
        key,
      )
    }
    if (job.description.isNullOrBlank()) {
      return MatchDecision(JobStatus.EXCLUDED_BY_ADVANCED_MATCHING, NO_DESCRIPTION, key)
    }
    if (llm == null || config.prompt.isEmpty()) return MatchDecision(JobStatus.NEW, null, key)
    return try {
      val answer = llm.check(config.prompt, job)
      if (answer.excluded) {
        MatchDecision(JobStatus.EXCLUDED_BY_ADVANCED_MATCHING, answer.reason?.ifBlank { null }, key)
      } else {
        MatchDecision(JobStatus.NEW, null, key)
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

    /** The reason of a job whose description could not be read. */
    const val NO_DESCRIPTION = "No job description could be read."

    private const val KEY_BYTES = 8

    fun isExcludedCompany(companyName: String, blacklist: List<String>): Boolean {
      val name = companyName.lowercase()
      return blacklist.any { it.lowercase() == name }
    }
  }
}
