package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.toModel
import io.github.raghavsatyadev.jobfinder.server.service.AdvancedMatchingFilter
import io.github.raghavsatyadev.jobfinder.server.service.OllamaJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.ats.AtsScoring
import io.github.raghavsatyadev.jobfinder.server.service.ats.OllamaAtsModel
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheckMiss
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheckState
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Checks a local model against the answers already saved in the database: the AI filter on the
 * latest kept and excluded jobs, and the latest ATS reviews of the current resume. Those answers
 * came from the model in use before (the benchmark `jobfinder-qwen` by default), so the result says
 * how close the new model comes to it on the user's own jobs. An unfinished answer counts as no
 * answer.
 */
class AccuracyCheck(
  private val db: ServerDatabase,
  private val http: HttpClient,
  private val ollamaUrl: String = AiProviderUrls.OLLAMA_API,
  private val clock: () -> Long = System::currentTimeMillis,
  private val nanos: () -> Long = System::nanoTime,
  private val client: (model: String) -> LlmClient = { model ->
    LlmClient(http, LlmConfig(model = model))
  },
) {
  /** What the check compares with. */
  class Samples(
    val prompt: String,
    /** Each job with `true` when it was excluded by the AI filter. */
    val matching: List<Pair<Job, Boolean>>,
    val resume: String?,
    val flags: List<ResumeFlag>,
    /** Each job with its saved overall score and verdict. */
    val ats: List<Triple<Job, Double, Boolean>>,
  ) {
    val size: Int
      get() = matching.size + ats.size
  }

  /** The saved answers to compare with; empty when there are none yet. */
  suspend fun samples(): Samples {
    val prompt = db.advancedMatchingDao().get()?.prompt.orEmpty()
    val matching =
      if (prompt.isBlank()) {
        emptyList()
      } else {
        val jobs = db.jobsDao()
        val kept = jobs.listRecentWithDescription(JobStatus.NEW.wire, PER_SIDE)
        val excluded =
          jobs
            .listRecentWithDescription(JobStatus.EXCLUDED_BY_ADVANCED_MATCHING.wire, PER_SIDE * 3)
            .filterNot { it.excludeReason.orEmpty().endsWith(AdvancedMatchingFilter.BLACKLISTED) }
            .take(PER_SIDE)
        kept.map { it.toModel() to false } + excluded.map { it.toModel() to true }
      }
    val resume = db.resumeDao().get()
    val ats =
      if (resume == null) {
        emptyList()
      } else {
        db.atsReviewsDao().listRecent(resume.hash, ATS_JOBS).mapNotNull { review ->
          db.jobsDao().get(review.jobId)?.toModel()?.let {
            Triple(it, review.overall, review.shortlisted)
          }
        }
      }
    val flags =
      resume?.flags.orEmpty().mapNotNull { name ->
        ResumeFlag.entries.firstOrNull { it.name == name }
      }
    return Samples(prompt, matching, resume?.text, flags, ats)
  }

  /** Runs [model] on the [samples]; [onProgress] gets checks done and the total. */
  suspend fun run(
    model: String,
    samples: Samples? = null,
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
  ): ModelCheck {
    val sample = samples ?: samples()
    if (sample.size == 0) {
      return ModelCheck(model, ModelCheckState.NO_SAMPLES, 0, 0, 0, 0, checkedAt = clock())
    }
    val llm = client(model)
    val filter = OllamaJobCheck(llm)
    val ats = OllamaAtsModel(llm)
    val misses = mutableListOf<ModelCheckMiss>()
    var agreed = 0
    var failed = 0
    var done = 0
    onProgress(0, sample.size)

    val matchTimes = mutableListOf<Double>()
    for ((job, excluded) in sample.matching) {
      val started = nanos()
      val answer = answer { filter.check(sample.prompt, job).excluded }
      if (answer != null) matchTimes += (nanos() - started) / NANOS_PER_SECOND
      when (answer) {
        null -> failed++
        excluded -> agreed++
        else -> Unit
      }
      if (answer != excluded) {
        misses +=
          ModelCheckMiss(
            ModelCheckMiss.Kind.MATCHING,
            job.title,
            job.companyName,
            decision(excluded),
            answer?.let(::decision),
          )
      }
      onProgress(++done, sample.size)
    }

    val reviewTimes = mutableListOf<Double>()
    for ((job, overall, shortlisted) in sample.ats) {
      val resume = sample.resume.orEmpty()
      val started = nanos()
      val review = answer {
        val text = "${job.title}\n${job.description.orEmpty()}"
        AtsScoring.review(job.id, text, resume, sample.flags, ats.judge(resume, job), clock())
      }
      if (review != null) reviewTimes += (nanos() - started) / NANOS_PER_SECOND
      val same =
        review != null &&
          review.summary.shortlisted == shortlisted &&
          abs(review.summary.overall - overall) <= ATS_TOLERANCE
      if (review == null) failed++
      if (same) {
        agreed++
      } else {
        misses +=
          ModelCheckMiss(
            ModelCheckMiss.Kind.ATS,
            job.title,
            job.companyName,
            verdict(overall, shortlisted),
            review?.let { verdict(it.summary.overall, it.summary.shortlisted) },
          )
      }
      onProgress(++done, sample.size)
    }

    val memory = loaded(model)
    val state =
      when {
        failed == sample.size -> ModelCheckState.FAILED
        agreed == sample.size -> ModelCheckState.MATCH
        else -> ModelCheckState.PARTIAL
      }
    return ModelCheck(
      model = model,
      state = state,
      percent = agreed * PERCENT / sample.size,
      agreed = agreed,
      checks = sample.size,
      failedAnswers = failed,
      misses = misses,
      secondsPerJob = matchTimes.takeIf { it.isNotEmpty() }?.average(),
      secondsPerReview = reviewTimes.takeIf { it.isNotEmpty() }?.average(),
      memoryGb = memory?.first,
      gpuMemoryGb = memory?.second,
      checkedAt = clock(),
    )
  }

  private inline fun <T> answer(block: () -> T): T? =
    try {
      block()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      null
    }

  /** Size and graphics-card share of [model] while loaded, in GB, from Ollama's `/api/ps`. */
  private suspend fun loaded(model: String): Pair<Double, Double>? = answer {
    val body = Json.parseToJsonElement(http.get("$ollamaUrl/api/ps").bodyAsText()).jsonObject
    val entry =
      body["models"]!!
        .jsonArray
        .map { it.jsonObject }
        .first { it.name() == model || it.name() == "$model:latest" }
    val size = entry["size"]!!.jsonPrimitive.longOrNull!!
    val vram = entry["size_vram"]?.jsonPrimitive?.longOrNull ?: 0L
    size / BYTES_PER_GB to vram / BYTES_PER_GB
  }

  private fun JsonObject.name() = this["name"]?.jsonPrimitive?.contentOrNull

  companion object {
    /** Kept and excluded jobs each; the matching replay showed about 20 jobs tell models apart. */
    const val PER_SIDE = 6
    const val ATS_JOBS = 2
    /** Scores this close (out of 10) with the same verdict count as the same answer. */
    const val ATS_TOLERANCE = 1.0
    private const val PERCENT = 100
    private const val NANOS_PER_SECOND = 1e9
    private const val BYTES_PER_GB = 1e9

    fun decision(excluded: Boolean) = if (excluded) "excluded" else "kept"

    fun verdict(overall: Double, shortlisted: Boolean) =
      "%.1f, %s".format(overall, if (shortlisted) "shortlisted" else "not shortlisted")
  }
}

/** Ollama's own API (not the OpenAI-compatible `/v1`). */
object AiProviderUrls {
  const val OLLAMA_API = "http://localhost:11434"
}
