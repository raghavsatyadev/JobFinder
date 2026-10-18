package io.github.raghavsatyadev.jobfinder.desktop.dev

import io.github.raghavsatyadev.jobfinder.desktop.secrets.DpapiSecretStore
import io.github.raghavsatyadev.jobfinder.server.ai.AccuracyCheck
import io.github.raghavsatyadev.jobfinder.server.ai.AgyClient
import io.github.raghavsatyadev.jobfinder.server.ai.AiKeyPool
import io.github.raghavsatyadev.jobfinder.server.ai.AiUsageRecorder
import io.github.raghavsatyadev.jobfinder.server.ai.LlmClient
import io.github.raghavsatyadev.jobfinder.server.ai.LlmConfig
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeClient
import io.github.raghavsatyadev.jobfinder.server.ai.OpenCodeServer
import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabaseFactory
import io.github.raghavsatyadev.jobfinder.server.service.JobAiRider
import io.github.raghavsatyadev.jobfinder.server.service.MergedJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.OllamaJobCheck
import io.github.raghavsatyadev.jobfinder.server.service.Ride
import io.github.raghavsatyadev.jobfinder.server.service.ats.AtsJudgement
import io.github.raghavsatyadev.jobfinder.server.service.ats.AtsScoring
import io.github.raghavsatyadev.jobfinder.server.service.ats.OllamaAtsModel
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dev tool, not part of the app: replays the AI filter and the ATS review on each online model the
 * user set up, first one task per request and then merged ([MergedJobCheck]), and writes a Markdown
 * report with requests per job and agreement. Reads a copy of the app's database (the running app
 * is left alone) and the keys from the app's protected store.
 *
 * `./gradlew :desktopCMP:aiReplay --args="<report.md> [jobs] [only]"`; [only] keeps the models
 * whose name has that text (case ignored), and then the OpenCode and agy CLIs come in even when
 * they are off in the app.
 */
fun main(args: Array<String>) {
  val report = File(args.getOrNull(0) ?: "ai-replay.md")
  val jobLimit = args.getOrNull(1)?.toIntOrNull() ?: DEFAULT_JOBS
  val only = args.getOrNull(2)?.ifBlank { null }
  val data = File(System.getenv("LOCALAPPDATA"), "jobfinder")
  val copy = Files.createTempDirectory("f2a-replay").toFile()
  for (suffix in listOf("", "-wal", "-shm")) {
    val from = File(data, "jobfinder.db$suffix")
    if (from.exists()) from.copyTo(File(copy, from.name))
  }
  val code = runBlocking { replay(data, copy, report, jobLimit, only) }
  copy.deleteRecursively()
  exitProcess(code)
}

private suspend fun replay(
  data: File,
  copy: File,
  report: File,
  jobLimit: Int,
  only: String?,
): Int {
  val db = ServerDatabaseFactory.create(File(copy, "jobfinder.db").toPath())
  val http = LlmClient.defaultHttpClient()
  val pool =
    AiKeyPool(
      db.aiKeysDao(),
      db.settingsDao(),
      DpapiSecretStore.forThisComputer(File(data, "secrets").toPath()),
      http,
    )
  val openCodeServer = OpenCodeServer(copy.resolve("opencode").toPath(), http)
  val openCode = OpenCodeClient(openCodeServer, http)
  val agy = AgyClient()
  val requests = AtomicInteger()
  val counter = AiUsageRecorder { _, _ -> requests.incrementAndGet() }
  val client = LlmClient(http, Semaphore(1), pool, openCode, agy) { LlmConfig() }

  val samples = AccuracyCheck(db, http).samples()
  val resume = samples.resume
  val jobs = samples.matching.take(jobLimit)
  if (jobs.isEmpty() || resume == null || samples.prompt.isBlank()) {
    println("REPLAY needs a matching prompt, a resume and decided jobs")
    return 1
  }
  val stored = routingOf(pool)
  // A filter brings the CLIs in, so they can be tried before the user turns them on.
  val saved =
    if (only == null) stored
    else
      stored.copy(
        openCode = stored.openCode.copy(enabled = true),
        agy = stored.agy.copy(enabled = true),
      )
  val routes =
    pool.list().map { key ->
      val model =
        saved.routes[AiTask.MATCHING]?.firstOrNull { it.keyId == key.id }?.model?.ifBlank { null }
          ?: FreeAiCatalog.of(key.provider)?.model
          ?: key.provider.suggestedModel
      "${key.label ?: key.provider.name} / $model" to AiRoute(key.id, model)
    } +
      listOfNotNull(
        ("OpenCode CLI / ${saved.openCode.model}" to AiRoute.OPEN_CODE).takeIf {
          saved.openCode.enabled
        },
        ("agy / ${saved.agy.model}" to AiRoute.AGY).takeIf { saved.agy.enabled },
      )
  val chosen = routes.filter { (name, _) -> only == null || name.contains(only, ignoreCase = true) }

  val out = StringBuilder()
  out.appendLine("# AI replay: one request per task vs merged")
  out.appendLine()
  out.appendLine(
    "${jobs.size} jobs with saved AI filter decisions; the ATS review against the current resume " +
      "goes with each. Requests count answered calls (a failed call is in Errors)."
  )
  out.appendLine()
  out.appendLine(
    "| Model | Mode | Requests/job | Filter agrees | ATS same as alone (±1) | Errors | s/job |"
  )
  out.appendLine("|---|---|---|---|---|---|---|")

  for ((name, route) in chosen) {
    pool.setRouting(
      saved.copy(routes = mapOf(AiTask.MATCHING to listOf(route), AiTask.ATS to listOf(route)))
    )
    val filter = OllamaJobCheck(client, counter)
    val ats = OllamaAtsModel(client, counter)
    val alone = mutableMapOf<Long, Double>()

    // A key that is not answering would cost minutes per call; try one job first.
    val (probe, _) = jobs.first()
    val answers =
      withTimeoutOrNull(PROBE_MS) { runCatching { filter.check(samples.prompt, probe) }.isSuccess }
    if (answers != true) {
      println("$name: no answer to a first job in ${PROBE_MS / 1000} s, skipped")
      out.appendLine("| $name | skipped | - | - | - | no answer in ${PROBE_MS / 1000} s | - |")
      continue
    }

    // One request per task.
    requests.set(0)
    var agree = 0
    var errors = 0
    var started = TimeSource.Monotonic.markNow()
    for ((job, excluded) in jobs) {
      val answer = patient { filter.check(samples.prompt, job) }
      if (answer.getOrNull()?.excluded == excluded) agree++
      if (answer.isFailure)
        errors++.also { println("$name filter ${job.id}: ${answer.exceptionOrNull()?.message}") }
      val review = patient { ats.judge(resume, job) }
      review.getOrNull()?.let { alone[job.id] = overall(job, resume, samples.flags, it) }
      if (review.isFailure)
        errors++.also { println("$name ats ${job.id}: ${review.exceptionOrNull()?.message}") }
      println("$name alone ${job.id}: ${requests.get()} requests so far")
    }
    out.row(name, "alone", requests.get(), jobs.size, agree, "-", errors, started)

    // Merged.
    requests.set(0)
    agree = 0
    errors = 0
    var same = 0
    var compared = 0
    started = TimeSource.Monotonic.markNow()
    for ((job, excluded) in jobs) {
      var merged: AtsJudgement? = null
      val rider =
        object : JobAiRider {
          override val task = AiTask.ATS

          override suspend fun ride(job: Job): Ride<AtsJudgement> =
            Ride(ats.part(resume, job)) { merged = it }
        }
      val check = MergedJobCheck(client, filter, { listOf(rider) }, counter)
      val answer = patient { check.check(samples.prompt, job) }
      if (answer.getOrNull()?.excluded == excluded) agree++
      if (answer.isFailure)
        errors++.also { println("$name merged ${job.id}: ${answer.exceptionOrNull()?.message}") }
      val judgement = merged
      if (judgement == null) {
        errors++
        println("$name merged ${job.id}: no ATS part")
      } else {
        val score = overall(job, resume, samples.flags, judgement)
        alone[job.id]?.let { before ->
          compared++
          if (kotlin.math.abs(before - score) <= 1.0) same++
        }
      }
      println("$name merged ${job.id}: ${requests.get()} requests so far")
    }
    out.row(name, "merged", requests.get(), jobs.size, agree, "$same/$compared", errors, started)
  }
  pool.setRouting(stored)
  openCodeServer.close()
  report.writeText(out.toString())
  println(out)
  println("REPLAY report -> ${report.absolutePath}")
  return 0
}

/**
 * Free keys allow a few thousand tokens a minute, so back-to-back jobs run out of quota; waits out
 * the minute instead of counting that as a wrong answer.
 */
private suspend fun <T> patient(block: suspend () -> T): Result<T> {
  var result = runCatching { block() }
  repeat(QUOTA_RETRIES) {
    if (result.exceptionOrNull()?.message?.contains("busy or out of quota") != true) return result
    println("out of quota, waiting ${QUOTA_WAIT_MS / 1000} s")
    delay(QUOTA_WAIT_MS)
    result = runCatching { block() }
  }
  return result
}

private suspend fun routingOf(pool: AiKeyPool) = pool.routing()

private fun overall(
  job: Job,
  resume: String,
  flags: List<io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag>,
  judgement: AtsJudgement,
): Double =
  AtsScoring.review(
      job.id,
      "${job.title}\n${job.description.orEmpty()}",
      resume,
      flags,
      judgement,
      0,
    )
    .summary
    .overall

private fun StringBuilder.row(
  name: String,
  mode: String,
  requests: Int,
  jobs: Int,
  agree: Int,
  ats: String,
  errors: Int,
  started: TimeSource.Monotonic.ValueTimeMark,
) {
  appendLine(
    "| $name | $mode | ${"%.2f".format(requests.toDouble() / jobs)} | $agree/$jobs | $ats | " +
      "$errors | ${started.elapsedNow().inWholeSeconds / jobs} |"
  )
}

private const val DEFAULT_JOBS = 6

private const val PROBE_MS = 60_000L

private const val QUOTA_RETRIES = 3

private const val QUOTA_WAIT_MS = 65_000L
