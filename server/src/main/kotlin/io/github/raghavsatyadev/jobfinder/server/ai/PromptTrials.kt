package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.db.AdvancedMatchingDao
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeDao
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.OllamaJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.ats.AtsScoring
import io.github.raghavsatyadev.jobfinder.server.service.ats.OllamaAtsModel
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTrial
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.shared.model.tryable

/**
 * "Try it" for edited AI instructions: runs them once, through the same routes as the real work, on
 * the newest new job with a description (or [run]'s job). Nothing is saved.
 */
class PromptTrials(
  private val jobs: JobsDao,
  private val resumes: ResumeDao,
  private val matching: AdvancedMatchingDao,
  private val client: LlmClient,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  suspend fun run(id: PromptId, text: String, jobId: Long? = null): PromptTrial {
    if (!id.tryable)
      throw ApiException(ApiErrorCode.BAD_REQUEST, "These instructions can't be tried here.")
    val job =
      (jobId?.let { jobs.get(it) } ?: jobs.newestWithDescription())?.toModel()
        ?: throw ApiException(ApiErrorCode.NOT_FOUND, "No job with a description to try it on.")
    val base = PromptTrial(job.id, job.title, job.companyName)
    try {
      return when (id) {
        PromptId.MATCHING -> {
          val wishes = matching.get()?.prompt.orEmpty()
          val answer = OllamaJobCheck(client) { text }.check(wishes, job)
          base.copy(excluded = answer.excluded, reason = answer.reason)
        }
        else -> {
          val resume =
            resumes.get() ?: throw ApiException(ApiErrorCode.BAD_REQUEST, "Add your resume first.")
          val judgement = OllamaAtsModel(client, instructions = { text }).judge(resume.text, job)
          val review =
            AtsScoring.review(
              jobId = job.id,
              jobText = "${job.title}\n${job.description}",
              resume = resume.text,
              flags =
                resume.flags.mapNotNull { n -> ResumeFlag.entries.firstOrNull { it.name == n } },
              judgement = judgement,
              now = clock(),
            )
          base.copy(
            atsMatch = review.summary.atsMatch,
            overall = review.summary.overall,
            assessment = review.assessment,
            fixes = review.fixes.take(MAX_FIXES),
          )
        }
      }
    } catch (e: LlmException) {
      throw ApiException(ApiErrorCode.UNAVAILABLE, e.message ?: "The AI gave no usable answer.")
    }
  }

  private companion object {
    const val MAX_FIXES = 5
  }
}
