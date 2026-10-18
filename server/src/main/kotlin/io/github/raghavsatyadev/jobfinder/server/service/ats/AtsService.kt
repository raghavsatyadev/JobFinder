package io.github.raghavsatyadev.jobfinder.server.service.ats

import io.github.raghavsatyadev.jobfinder.server.db.AtsReviewEntity
import io.github.raghavsatyadev.jobfinder.server.db.AtsReviewsDao
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeEntity
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.JobAiRider
import io.github.raghavsatyadev.jobfinder.server.service.Ride
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import java.security.MessageDigest
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * The resume and the ATS reviews. One worker scores a job at a time on the shared model: jobs
 * someone opened first, then every new job with a description, newest first. Changing the resume
 * drops every review, so all jobs are scored again against the new one.
 */
class AtsService(
  private val resumes: ResumeDao,
  private val reviews: AtsReviewsDao,
  private val jobs: JobsDao,
  private val model: AtsModel,
  private val events: ServerEvents,
  private val clock: () -> Long = System::currentTimeMillis,
) : AtsRepository, JobAiRider {
  private val wake = Channel<Unit>(Channel.CONFLATED)
  private val asked = ConcurrentLinkedDeque<Long>()

  /**
   * Reviews that came in the AI filter's merged call, by job and resume hash, until the worker
   * stores them. Bounded: a job the filter excluded never gets scored, so its entry ages out.
   */
  private val readyMade =
    object : LinkedHashMap<Pair<Long, String>, AtsJudgement>() {
      override fun removeEldestEntry(eldest: Map.Entry<Pair<Long, String>, AtsJudgement>) =
        size > MAX_READY_MADE
    }

  override val task: AiTask = AiTask.ATS

  /** The review of a job that has none yet against the current resume, for the filter's call. */
  override suspend fun ride(job: Job): Ride<AtsJudgement>? {
    if (job.description.isNullOrBlank()) return null
    val resume = resumes.get() ?: return null
    if (reviews.get(job.id, resume.hash) != null) return null
    val part = model.part(resume.text, job) ?: return null
    return Ride(part) { judgement ->
      synchronized(readyMade) { readyMade[job.id to resume.hash] = judgement }
      wake.trySend(Unit)
    }
  }

  /** Starts the worker in [scope]; new jobs wake it. */
  fun start(scope: CoroutineScope) {
    scope.launch {
      events.events.collect { if (it is ServerEvent.NewJobs) wake.trySend(Unit) }
    }
    scope.launch {
      while (true) {
        val next = next()
        if (next == null) wake.receive() else score(next)
      }
    }
  }

  override suspend fun getResume(): ResumeInfo? = resumes.get()?.toInfo()

  override suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeInfo {
    if (bytes.size > AtsRepository.MAX_RESUME_BYTES) {
      throw ApiException(ApiErrorCode.PAYLOAD_TOO_LARGE, "The resume must be 5 MB or less.")
    }
    val read = withContext(Dispatchers.IO) { ResumeReader.read(fileName, bytes) }
    val entity =
      ResumeEntity(
        fileName = fileName.substringAfterLast('/').substringAfterLast('\\').take(200),
        text = read.text,
        pages = read.pages,
        words = read.words,
        flags = read.flags.map { it.name },
        hash = sha256(read.text),
        uploadedAt = clock(),
      )
    val changed = resumes.get()?.hash != entity.hash
    if (changed) reviews.deleteAll()
    resumes.upsert(entity)
    if (changed) {
      events.emit(ServerEvent.ResumeChanged)
      wake.trySend(Unit)
    }
    return entity.toInfo()
  }

  /** Puts back a resume from a backup; like an upload, a different one is scored again. */
  suspend fun restoreResume(entity: ResumeEntity) {
    val restored = entity.copy(hash = sha256(entity.text))
    val changed = resumes.get()?.hash != restored.hash
    if (changed) reviews.deleteAll()
    resumes.upsert(restored)
    if (changed) {
      events.emit(ServerEvent.ResumeChanged)
      wake.trySend(Unit)
    }
  }

  override suspend fun deleteResume() {
    resumes.delete()
    reviews.deleteAll()
    asked.clear()
    events.emit(ServerEvent.ResumeChanged)
  }

  override suspend fun requestReview(jobId: Long): AtsStatus {
    val job = jobs.get(jobId) ?: notFound("Job")
    val resume = resumes.get() ?: return AtsStatus(AtsState.NO_RESUME)
    if (job.description.isNullOrBlank()) return AtsStatus(AtsState.NO_DESCRIPTION)
    val stored = reviews.get(jobId, resume.hash)
    if (stored != null && !stored.failed) {
      val review = stored.review?.let { ApiJson.decodeFromString(AtsReview.serializer(), it) }
      if (review != null) return AtsStatus(AtsState.READY, review)
    }
    // Missing or failed: this job goes next.
    if (stored != null) reviews.delete(jobId)
    asked.remove(jobId)
    asked.addFirst(jobId)
    wake.trySend(Unit)
    return AtsStatus(AtsState.QUEUED)
  }

  /** The job's review against the current resume, without queueing one; `null` when none. */
  suspend fun storedReview(jobId: Long): AtsReview? {
    val hash = resumes.get()?.hash ?: return null
    val stored = reviews.get(jobId, hash)?.takeIf { !it.failed } ?: return null
    return stored.review?.let { ApiJson.decodeFromString(AtsReview.serializer(), it) }
  }

  /** The summary the job detail shows, or `null` when not scored against the current resume. */
  suspend fun summary(jobId: Long): AtsSummary? {
    val hash = resumes.get()?.hash ?: return null
    val stored = reviews.get(jobId, hash)?.takeIf { !it.failed } ?: return null
    return AtsSummary(stored.overall, stored.atsMatch, stored.shortlisted)
  }

  /** The job asked for first, else the newest unscored new job; `null` when there is none. */
  internal suspend fun next(): Long? {
    val hash = resumes.get()?.hash ?: return null
    while (true) {
      val id = asked.pollFirst() ?: break
      if (reviews.get(id, hash) == null) return id
    }
    return reviews.nextUnscored(JobStatus.NEW.wire, hash)
  }

  internal suspend fun score(jobId: Long) {
    val resume = resumes.get() ?: return
    val job = jobs.get(jobId)?.toModel() ?: return
    val description = job.description
    if (description.isNullOrBlank()) return
    val now = clock()
    val entity =
      try {
        val judgement =
          synchronized(readyMade) { readyMade.remove(jobId to resume.hash) }
            ?: model.judge(resume.text, job)
        val review =
          AtsScoring.review(
            jobId = jobId,
            jobText = "${job.title}\n$description",
            resume = resume.text,
            flags = resume.flags(),
            judgement = judgement,
            now = now,
          )
        AtsReviewEntity(
          jobId = jobId,
          resumeHash = resume.hash,
          overall = review.summary.overall,
          atsMatch = review.summary.atsMatch,
          shortlisted = review.summary.shortlisted,
          review = ApiJson.encodeToString(AtsReview.serializer(), review),
          createdAt = now,
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.warn("ATS review of job {} failed: {}", jobId, e.message)
        AtsReviewEntity(jobId = jobId, resumeHash = resume.hash, failed = true, createdAt = now)
      }
    // The resume changed while the model worked: this review is for the old one.
    if (resumes.get()?.hash != resume.hash) return
    // The job was deleted meanwhile.
    jobs.get(jobId) ?: return
    reviews.upsert(entity)
    val summary =
      if (entity.failed) null else AtsSummary(entity.overall, entity.atsMatch, entity.shortlisted)
    events.emit(ServerEvent.AtsScored(jobId, summary))
  }

  private fun ResumeEntity.flags(): List<ResumeFlag> = flags.mapNotNull { name ->
    ResumeFlag.entries.firstOrNull { it.name == name }
  }

  private fun ResumeEntity.toInfo(): ResumeInfo =
    ResumeInfo(fileName, uploadedAt, pages, words, flags())

  private companion object {
    val log = LoggerFactory.getLogger("jobfinder.ats")

    const val MAX_READY_MADE = 200

    fun sha256(text: String): String =
      MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
        "%02x".format(it)
      }
  }
}
