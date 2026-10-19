package io.github.raghavsatyadev.jobfinder.server.service.translate

import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobTranslationEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.TranslationsDao
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.AiWorkPause
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.JobTranslation
import io.github.raghavsatyadev.jobfinder.shared.model.LanguageGuess
import io.github.raghavsatyadev.jobfinder.shared.model.TranslationState
import io.github.raghavsatyadev.jobfinder.shared.model.TranslationStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.TranslationRepository
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Translates jobs posted in another language into the user's language ([target], Settings →
 * Scanning). The language is guessed from the job's words, with no AI; only a job in another
 * language goes to the AI. A job the user opens goes first; besides, new jobs are translated in the
 * background unless AI work is paused. A result (also "same language" and "failed") is kept per job
 * until the job's text or the language setting changes.
 */
class TranslationService(
  private val dao: TranslationsDao,
  private val jobs: JobsDao,
  private val translator: JobTranslator,
  private val events: ServerEvents,
  /** The language setting now; `null` is off. */
  private val target: suspend () -> String?,
  /** The language setting after each change. */
  private val targetChanges: Flow<String?>,
  private val pause: AiWorkPause = AiWorkPause.NEVER,
  private val clock: () -> Long = System::currentTimeMillis,
) : TranslationRepository {
  private val asked = ConcurrentLinkedDeque<Long>()
  private val wake = Channel<Unit>(Channel.CONFLATED)
  @Volatile private var working: Long? = null

  fun start(scope: CoroutineScope) {
    scope.launch { events.events.collect { if (it is ServerEvent.NewJobs) wake.trySend(Unit) } }
    scope.launch { pause.paused.collect { if (!it) wake.trySend(Unit) } }
    scope.launch { targetChanges.collect { wake.trySend(Unit) } }
    scope.launch {
      while (true) {
        val next = next()
        if (next == null) wake.receive() else work(next)
      }
    }
  }

  override suspend fun requestTranslation(jobId: Long, retry: Boolean): TranslationStatus {
    val job = jobs.get(jobId) ?: notFound("Job")
    val to = target() ?: return TranslationStatus(TranslationState.OFF)
    val stored = current(job, to)
    if (jobId == working || jobId in asked) {
      return TranslationStatus(TranslationState.QUEUED, from = stored?.fromLang ?: guess(job))
    }
    when (stored?.state) {
      JobTranslationEntity.READY ->
        return TranslationStatus(TranslationState.READY, stored.toModel(), stored.fromLang)
      JobTranslationEntity.SAME -> return TranslationStatus(TranslationState.SAME)
      JobTranslationEntity.FAILED ->
        if (!retry) return TranslationStatus(TranslationState.FAILED, from = stored.fromLang)
    }
    val from = guess(job)
    if (from == null || from == to) {
      dao.upsert(entity(job, from, to, JobTranslationEntity.SAME))
      return TranslationStatus(TranslationState.SAME)
    }
    dao.delete(jobId)
    asked.remove(jobId)
    asked.addFirst(jobId)
    wake.trySend(Unit)
    return TranslationStatus(TranslationState.QUEUED, from = from)
  }

  /** [jobId]'s translation for the job detail, without queueing one; `null` when none. */
  suspend fun translation(jobId: Long): JobTranslation? {
    val job = jobs.get(jobId) ?: return null
    val to = target() ?: return null
    return current(job, to)?.takeIf { it.state == JobTranslationEntity.READY }?.toModel()
  }

  /** The job asked for first, else (unless AI work is paused) the newest new job not checked. */
  internal suspend fun next(): Long? {
    val to = target() ?: return null
    while (true) {
      val id = asked.peekFirst() ?: break
      val job = jobs.get(id)
      if (job != null && current(job, to) == null) return id
      asked.remove(id)
    }
    if (pause.paused.value) return null
    return dao.nextUnchecked(JobStatus.NEW.wire, to)
  }

  internal suspend fun work(jobId: Long) {
    working = jobId
    asked.remove(jobId)
    try {
      val job = jobs.get(jobId) ?: return
      val to = target() ?: return
      val from = guess(job)
      if (from == null || from == to) {
        dao.upsert(entity(job, from, to, JobTranslationEntity.SAME))
        return
      }
      val stored =
        try {
          val done = translator.translate(job.title, job.description, from, to)
          entity(job, from, to, JobTranslationEntity.READY, done.title, done.description)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Translation of job {} failed: {}", jobId, e.message)
          entity(job, from, to, JobTranslationEntity.FAILED)
        }
      dao.upsert(stored)
      val title = stored.title?.let { JobTranslation(from, to, it) }
      events.emit(ServerEvent.TranslationReady(jobId, title))
    } finally {
      working = null
    }
  }

  /** The stored result for [job] in [to], or `null` when none or made from older text. */
  private suspend fun current(job: JobEntity, to: String): JobTranslationEntity? =
    dao.get(job.id)?.takeIf { it.toLang == to && it.sourceHash == hash(job) }

  private fun guess(job: JobEntity): String? = LanguageGuess.guess(job.title, job.description)

  private fun entity(
    job: JobEntity,
    from: String?,
    to: String,
    state: String,
    title: String? = null,
    description: String? = null,
  ) = JobTranslationEntity(job.id, from, to, hash(job), state, title, description, clock())

  private fun JobTranslationEntity.toModel(): JobTranslation =
    JobTranslation(fromLang.orEmpty(), toLang, title.orEmpty(), description)

  companion object {
    private val log = LoggerFactory.getLogger("jobfinder.translate")

    fun hash(job: JobEntity): String {
      val digest = MessageDigest.getInstance("SHA-256")
      val bytes = digest.digest((job.title + "\n" + job.description.orEmpty()).toByteArray())
      return bytes.joinToString("") { "%02x".format(it) }
    }
  }
}
