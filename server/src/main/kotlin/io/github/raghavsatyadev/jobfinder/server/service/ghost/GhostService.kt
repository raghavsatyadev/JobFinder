package io.github.raghavsatyadev.jobfinder.server.service.ghost

import io.github.raghavsatyadev.jobfinder.server.db.CareersBoardEntity
import io.github.raghavsatyadev.jobfinder.server.db.GhostCheckEntity
import io.github.raghavsatyadev.jobfinder.server.db.GhostDao
import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.AiWorkPause
import io.github.raghavsatyadev.jobfinder.server.service.insights.CompanyNames
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.CareersCheck
import io.github.raghavsatyadev.jobfinder.shared.model.CareersHost
import io.github.raghavsatyadev.jobfinder.shared.model.CareersResult
import io.github.raghavsatyadev.jobfinder.shared.model.GhostCheck
import io.github.raghavsatyadev.jobfinder.shared.model.GhostSignal
import io.github.raghavsatyadev.jobfinder.shared.model.GhostStatus
import io.github.raghavsatyadev.jobfinder.shared.model.InsightKeys
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import org.slf4j.LoggerFactory

/**
 * The ghost-job check: signals from the job and the app's own history (reposts, how long it has
 * been advertised, no salary, a thin description) at once, and whether the company's own careers
 * site lists the job, read from the public job lists of known careers hosts (Greenhouse, Lever,
 * Ashby, SmartRecruiters, Workable, Recruitee). A company's board is read once for all its jobs.
 *
 * Like the insights reader, opened jobs go first and a background reader works through the rest
 * with a random gap between companies. Without [reader] (tests) only the signals show.
 */
class GhostService(
  private val dao: GhostDao,
  private val jobs: JobsDao,
  private val reader: CareersReader?,
  private val matcher: CareersMatcher,
  private val events: ServerEvents,
  private val clock: () -> Long = System::currentTimeMillis,
  private val gap: () -> Duration = { Random.nextLong(GAP_MIN_MS, GAP_MAX_MS + 1).milliseconds },
  /** The pause between two requests to the careers hosts. */
  private val pause: () -> Duration = { Random.nextLong(1_500, 4_001).milliseconds },
  private val aiPause: AiWorkPause = AiWorkPause.NEVER,
) : GhostRepository {
  private val queue = Channel<Long>(Channel.UNLIMITED)
  private val queued = ConcurrentHashMap.newKeySet<Long>()
  private val reading = Mutex()

  /** Jobs whose last read failed, and when. */
  private val failedAt = ConcurrentHashMap<Long, Long>()

  fun start(scope: CoroutineScope) {
    scope.launch {
      for (jobId in queue) {
        try {
          reading.withLock { check(jobId) }
          failedAt.remove(jobId)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          failedAt[jobId] = clock()
          log.warn("Careers check for job {} failed: {}", jobId, e.message)
        } finally {
          queued.remove(jobId)
        }
        events.emit(ServerEvent.GhostChecked(listOf(jobId)))
      }
    }
    if (reader != null) scope.launch { backfill() }
  }

  private suspend fun backfill() {
    delay(BACKFILL_START)
    while (true) {
      aiPause.awaitResumed()
      while (queued.isNotEmpty()) delay(QUEUE_POLL)
      val next =
        try {
          nextUnchecked()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Choosing the next job for a careers check failed: {}", e.message)
          null
        }
      if (next == null) {
        delay(BACKFILL_IDLE)
        continue
      }
      try {
        reading.withLock { check(next) }
        failedAt.remove(next)
        events.emit(ServerEvent.GhostChecked(listOf(next)))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        failedAt[next] = clock()
        log.warn("Background careers check for job {} failed: {}", next, e.message)
      }
      delay(gap())
    }
  }

  /** The first job (insights order) without a fresh careers check, skipping recent failures. */
  private suspend fun nextUnchecked(): Long? {
    val now = clock()
    for (job in jobs.listForInsights(now - RECENT.inWholeMilliseconds)) {
      if (now - (failedAt[job.id] ?: 0) < FAILED_TTL.inWholeMilliseconds) continue
      if (CompanyNames.key(job.companyName).isEmpty()) continue
      val check = dao.check(job.id)
      if (check == null || !fresh(check, now)) return job.id
    }
    return null
  }

  override suspend fun requestGhostCheck(jobId: Long, retry: Boolean): GhostStatus {
    val job = jobs.get(jobId) ?: notFound("Job")
    val now = clock()
    val stored = dao.check(jobId)
    val careers = stored?.let { ApiJson.decodeFromString(CareersCheck.serializer(), it.careers) }
    val check = GhostCheck(signals(job, careers), careers)
    val failed = now - (failedAt[jobId] ?: 0) < FAILED_TTL.inWholeMilliseconds
    return when {
      stored != null && fresh(stored, now) -> GhostStatus(InsightsState.READY, check)
      reader == null -> GhostStatus(InsightsState.UNAVAILABLE, check)
      failed && !retry && jobId !in queued -> GhostStatus(InsightsState.FAILED, check)
      else -> {
        if (queued.add(jobId)) queue.trySend(jobId)
        GhostStatus(InsightsState.QUEUED, check)
      }
    }
  }

  private fun fresh(check: GhostCheckEntity, now: Long): Boolean {
    val ttl =
      when (check.result) {
        CareersResult.FOUND.wire -> FOUND_TTL
        CareersResult.NOT_FOUND.wire -> NOT_FOUND_TTL
        else -> UNKNOWN_TTL
      }
    return now - check.checkedAt < ttl.inWholeMilliseconds
  }

  /** What the job itself and the app's history say. */
  internal suspend fun signals(job: JobEntity, careers: CareersCheck?): List<GhostSignal> =
    buildList {
      val now = clock()
      val title = InsightKeys.title(job.title)
      val same = jobs.listByCompany(job.companyName).filter { InsightKeys.title(it.title) == title }
      val firstSeen = same.minOfOrNull { it.createdAt } ?: job.createdAt
      val span = now - firstSeen
      if (same.size >= 2 && span >= REPOST_SPAN.inWholeMilliseconds) {
        add(GhostSignal.Reposted(same.size, firstSeen))
      }
      if (same.size >= 2 && span >= LONG_ADVERTISED.inWholeMilliseconds) {
        add(GhostSignal.OldPosting((span / 1.days.inWholeMilliseconds).toInt()))
      }
      val description = job.description.orEmpty()
      if (job.salary.isNullOrBlank() && !SALARY.containsMatchIn(description)) {
        add(GhostSignal.NoSalary)
      }
      if (description.isNotBlank() && plainLength(description) < VAGUE_CHARS) {
        add(GhostSignal.VagueText)
      }
      if (careers?.result == CareersResult.NOT_FOUND) add(GhostSignal.NotOnCareersSite)
      val key = CompanyNames.key(job.companyName)
      if (key.isNotEmpty()) {
        val counts = dao.companyResults(key).associate { it.result to it.jobs }
        val missing = counts[CareersResult.NOT_FOUND.wire] ?: 0
        if (missing >= NEVER_ON_SITE && (counts[CareersResult.FOUND.wire] ?: 0) == 0) {
          add(GhostSignal.CompanyNeverOnOwnSite(missing))
        }
      }
    }

  /** Reads the company's careers site and stores whether it lists [jobId]. */
  internal suspend fun check(jobId: Long) {
    val reader = reader ?: return
    val job = jobs.get(jobId) ?: return
    val key = CompanyNames.key(job.companyName)
    val careers =
      if (key.isEmpty()) CareersCheck(CareersResult.UNKNOWN, checkedAt = clock())
      else careersCheck(reader, job, key)
    dao.upsertCheck(
      GhostCheckEntity(
        jobId = jobId,
        companyKey = key,
        result = careers.result.wire,
        careers = ApiJson.encodeToString(CareersCheck.serializer(), careers),
        checkedAt = careers.checkedAt,
      )
    )
    log.info("Careers check for job {}: {} on {}", jobId, careers.result, careers.host)
  }

  private suspend fun careersCheck(
    reader: CareersReader,
    job: JobEntity,
    key: String,
  ): CareersCheck {
    val board =
      board(reader, job, key) ?: return CareersCheck(CareersResult.UNKNOWN, checkedAt = clock())
    val (host, slug, list) = board
    val url = boardUrl(host, slug)
    // The board post is itself on the company's careers host.
    if (CareersSites.linkedBoards(job.externalUrl).contains(host to slug)) {
      return CareersCheck(CareersResult.FOUND, host, url, job.externalUrl, null, list.size, clock())
    }
    val match = matcher.match(job.title, job.location, list)
    return CareersCheck(
      result = if (match != null) CareersResult.FOUND else CareersResult.NOT_FOUND,
      host = host,
      boardUrl = url,
      jobUrl = match?.url,
      matchedTitle = match?.title?.takeIf { !it.equals(job.title, ignoreCase = true) },
      openJobs = list.size,
      checkedAt = clock(),
    )
  }

  private data class Board(val host: CareersHost, val slug: String, val jobs: List<CareersJob>)

  /** The company's careers board, from the cache or found now; `null` when it has none. */
  private suspend fun board(reader: CareersReader, job: JobEntity, key: String): Board? {
    val now = clock()
    dao.board(key)?.let { cached ->
      val ttl = if (cached.host == null) NO_BOARD_TTL else BOARD_TTL
      if (now - cached.fetchedAt < ttl.inWholeMilliseconds) {
        val host = cached.host?.let { h -> CareersHost.entries.firstOrNull { it.wire == h } }
        return if (host == null || cached.slug == null) null
        else Board(host, cached.slug, ApiJson.decodeFromString(JOBS, cached.jobs))
      }
    }
    val found = discover(reader, job)
    dao.upsertBoard(
      CareersBoardEntity(
        companyKey = key,
        host = found?.host?.wire,
        slug = found?.slug,
        jobs = ApiJson.encodeToString(JOBS, found?.jobs.orEmpty()),
        fetchedAt = clock(),
      )
    )
    return found?.let { Board(it.host, it.slug, it.jobs) }
  }

  /**
   * Boards the job links to first, then the company's name on each host. A guessed board counts
   * only when the host's name for it is the company's (hosts without names: exact slug only).
   */
  private suspend fun discover(reader: CareersReader, job: JobEntity): CareersBoard? {
    val linked = CareersSites.linkedBoards(job.externalUrl + "\n" + job.description.orEmpty())
    val guessed =
      CareersSites.guessSlugs(job.companyName).flatMap { slug ->
        CareersHost.entries.map { it to slug }.filter { it !in linked }
      }
    var tried = 0
    var failed = 0
    var lastError: Exception? = null
    for ((host, slug) in linked + guessed) {
      tried++
      val board =
        try {
          reader.read(host, slug)
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          // One host down (or a guessed name with no host behind it) must not stop the rest.
          failed++
          lastError = e
          null
        }
      delay(pause())
      if (board == null) continue
      val ownName = (host to slug) in linked
      if (ownName || board.name == null || CompanyNames.matches(job.companyName, board.name)) {
        return board
      }
    }
    // Every host failed: offline, not "no careers site".
    if (tried > 0 && failed == tried) throw lastError ?: CareersReadException("No host answered")
    return null
  }

  companion object {
    val FOUND_TTL = 7.days
    /** A job missing from the careers site may be added later: checked again sooner. */
    val NOT_FOUND_TTL = 2.days
    val UNKNOWN_TTL = 14.days
    val BOARD_TTL = 1.days
    val NO_BOARD_TTL = 14.days
    val FAILED_TTL = 6.hours
    val RECENT = 6.hours
    val BACKFILL_START = 15.minutes
    val BACKFILL_IDLE = 1.hours
    private val QUEUE_POLL = 30.seconds
    const val GAP_MIN_MS = 2 * 60_000L
    const val GAP_MAX_MS = 6 * 60_000L

    /** The same title seen again this much later is a repost, not a second location. */
    val REPOST_SPAN = 14.days
    val LONG_ADVERTISED = 45.days
    const val VAGUE_CHARS = 600
    const val NEVER_ON_SITE = 3

    private val SALARY =
      Regex(
        """(\$|₹|€|£|\b(usd|inr|eur|gbp|lpa|ctc)\b|per annum|salary|compensation|pay range|\b\d{2,3}\s?k\b)""",
        RegexOption.IGNORE_CASE,
      )

    private val JOBS = ListSerializer(CareersJob.serializer())
    private val log = LoggerFactory.getLogger("jobfinder.ghost")

    /** Description length without Markdown or HTML marks. */
    fun plainLength(text: String): Int =
      text
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("[*_#>`\\[\\]()-]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .length
  }
}

private val CareersResult.wire: String
  get() =
    when (this) {
      CareersResult.FOUND -> "found"
      CareersResult.NOT_FOUND -> "not_found"
      CareersResult.UNKNOWN -> "unknown"
    }

private val CareersHost.wire: String
  get() = name.lowercase()
