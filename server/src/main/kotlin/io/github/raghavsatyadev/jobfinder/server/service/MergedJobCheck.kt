package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmException
import io.github.raghavsatyadev.jobfinder.server.ai.MergedAnswer
import io.github.raghavsatyadev.jobfinder.server.ai.MergedCall
import io.github.raghavsatyadev.jobfinder.server.ai.MergedPart
import io.github.raghavsatyadev.jobfinder.server.ai.RequestTooLargeException
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory

/** Another task's work on a job, ready to go in the AI filter's call; [accept] keeps its answer. */
class Ride<T>(val part: MergedPart<T>, val accept: suspend (T) -> Unit) {
  internal suspend fun deliver(answer: MergedAnswer): Boolean {
    val value = answer[part] ?: return false
    accept(value)
    return true
  }
}

/** Work that can ride along in the AI filter's call when both go to the same online model. */
interface JobAiRider {
  val task: AiTask

  /** This job's part, or `null` when there is nothing to do for it now. */
  suspend fun ride(job: Job): Ride<*>?
}

/**
 * The AI filter, with the other per-job work ([riders], such as the ATS review) in the same request
 * when each task's first model is the same online model: one request per job instead of one per
 * task, counted once against the quota. A part the merged answer misses is done alone: the filter
 * here, a rider by its own worker later. The model on this computer always gets the tasks one by
 * one, and so does a model that once said the merged request is too large (until restart).
 */
class MergedJobCheck(
  private val client: LlmClient,
  private val filter: OllamaJobCheck,
  private val riders: () -> List<JobAiRider>,
  private val usage: AiUsageRecorder = AiUsageRecorder.NONE,
) : LlmJobCheck {
  private val log = LoggerFactory.getLogger(MergedJobCheck::class.java)

  /** Key and model pairs whose per-request limit the merged request went over. */
  private val tooLarge = ConcurrentHashMap.newKeySet<String>()

  override suspend fun check(prompt: String, job: Job): JobExclusion {
    val rides =
      riders().mapNotNull { rider ->
        val target =
          client.sharedTarget(listOf(AiTask.MATCHING, rider.task)) ?: return@mapNotNull null
        rider.ride(job)?.let { target to it }
      }
    // Riders on different models ride separately; only those on the filter's model come along.
    val target = rides.firstOrNull()?.first ?: return filter.check(prompt, job)
    val name = "${target.keyId}/${target.config.model}"
    if (name in tooLarge) return filter.check(prompt, job)
    val along = rides.filter { (t, _) ->
      t.keyId == target.keyId && t.config.model == target.config.model
    }
    val own = filter.part(prompt, job)
    val answer =
      try {
        MergedCall.run(client, target, listOf(own) + along.map { it.second.part })
      } catch (e: CancellationException) {
        throw e
      } catch (e: RequestTooLargeException) {
        tooLarge += name
        log.info("Merged AI call is too large for {}, asking one by one from now on", name)
        return filter.check(prompt, job)
      } catch (e: LlmException) {
        log.info("Merged AI call for job {} failed, asking one by one: {}", job.id, e.message)
        return filter.check(prompt, job)
      }
    usage.record(answer.inputTokens, answer.outputTokens)
    val delivered = along.count { it.second.deliver(answer) }
    log.info(
      "Job {}: one AI request for the filter and {} of {} other tasks",
      job.id,
      delivered,
      along.size,
    )
    return answer[own] ?: filter.check(prompt, job)
  }
}
