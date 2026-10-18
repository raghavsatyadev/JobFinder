package io.github.raghavsatyadev.jobfinder.server.service.insights

import io.github.raghavsatyadev.jobfinder.server.db.CompanyRatingsEntity
import io.github.raghavsatyadev.jobfinder.server.db.CompanySalaryEntity
import io.github.raghavsatyadev.jobfinder.server.db.InsightsDao
import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyRating
import io.github.raghavsatyadev.jobfinder.shared.model.InsightKeys
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.SalaryRange
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
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

/** Loads one public page in the app's browser; throws when the site blocks or the load fails. */
fun interface InsightPages {
  suspend fun load(url: String, waitForScripts: Boolean): InsightPage
}

/**
 * Company ratings (AmbitionBox, Glassdoor) and the salary for the job's role (AmbitionBox), read
 * from public pages signed out, one company at a time, and cached: ratings for [RATINGS_TTL], each
 * title's salary as long, a failed read for [FAILED_TTL]. Without [pages] (no browser) it only
 * serves the cache.
 *
 * Besides the jobs someone opens, a background reader works through every job (see
 * [JobsDao.listForInsights] for the order), with a long random [gap] between reads so the sites see
 * a slow visitor rather than a crawler. An opened job always goes first.
 */
class InsightsService(
  private val dao: InsightsDao,
  private val jobs: JobsDao,
  private val pages: InsightPages?,
  private val events: ServerEvents,
  private val clock: () -> Long = System::currentTimeMillis,
  private val gap: () -> Duration = { Random.nextLong(GAP_MIN_MS, GAP_MAX_MS + 1).milliseconds },
) : InsightsRepository {
  private val queue = Channel<Long>(Channel.UNLIMITED)
  private val queued = ConcurrentHashMap.newKeySet<Long>()

  /** One read at a time across the opened jobs and the background reader. */
  private val reading = Mutex()

  /** Jobs whose background read threw, and until when they are passed over. */
  private val passOver = ConcurrentHashMap<Long, Long>()

  /** Starts the worker and the background reader in [scope]. */
  fun start(scope: CoroutineScope) {
    scope.launch {
      for (jobId in queue) {
        try {
          reading.withLock { fetch(jobId) }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Company insights for job {} failed: {}", jobId, e.message)
        } finally {
          queued.remove(jobId)
        }
        events.emit(ServerEvent.InsightsReady(listOf(jobId)))
      }
    }
    if (pages != null) scope.launch { backfill() }
  }

  /**
   * Reads the next job without insights, waits a random gap, and so on; idles when all have them.
   */
  private suspend fun backfill() {
    delay(BACKFILL_START)
    while (true) {
      // Opened jobs first: wait until the queue is empty.
      while (queued.isNotEmpty()) delay(QUEUE_POLL)
      val next =
        try {
          nextMissing()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.warn("Choosing the next job for insights failed: {}", e.message)
          null
        }
      if (next == null) {
        delay(BACKFILL_IDLE)
        continue
      }
      try {
        reading.withLock { fetch(next) }
        events.emit(ServerEvent.InsightsReady(listOf(next)))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.warn("Background insights for job {} failed: {}", next, e.message)
        // So one broken job does not hold up the rest.
        passOver[next] = clock() + FAILED_TTL.inWholeMilliseconds
      }
      delay(gap())
    }
  }

  /**
   * The first job in [JobsDao.listForInsights] order whose company or salary still needs a read;
   * `null` when every job has fresh insights (or a recent failure that waits [FAILED_TTL]).
   */
  internal suspend fun nextMissing(): Long? {
    val now = clock()
    val ratingsByCompany = HashMap<String, CompanyRatingsEntity?>()
    for (job in jobs.listForInsights(now - RECENT.inWholeMilliseconds)) {
      if ((passOver[job.id] ?: 0) > now) continue
      val key = CompanyNames.key(job.companyName)
      if (key.isEmpty()) continue
      val ratings = ratingsByCompany.getOrPut(key) { dao.ratings(key) }
      if (ratings == null || !ratings.fresh(now)) return job.id
      if (ratings.failed) continue
      val salary = dao.salary(key, titleKey(job.title))
      if (salary == null || now - salary.fetchedAt >= RATINGS_TTL.inWholeMilliseconds) {
        return job.id
      }
    }
    return null
  }

  override suspend fun requestInsights(jobId: Long, retry: Boolean): InsightsStatus {
    val job = jobs.get(jobId) ?: notFound("Job")
    val key = CompanyNames.key(job.companyName)
    val now = clock()
    val ratings = dao.ratings(key)
    val salary = dao.salary(key, titleKey(job.title))
    val cached = insights(job.companyName, ratings, salary)
    val ratingsFresh = ratings != null && ratings.fresh(now)
    val salaryFresh = salary != null && now - salary.fetchedAt < RATINGS_TTL.inWholeMilliseconds
    return when {
      ratingsFresh && ratings.failed && !retry -> InsightsStatus(InsightsState.FAILED, cached)
      ratingsFresh && !ratings.failed && salaryFresh -> InsightsStatus(InsightsState.READY, cached)
      pages == null -> InsightsStatus(InsightsState.UNAVAILABLE, cached)
      else -> {
        if (queued.add(jobId)) queue.trySend(jobId)
        InsightsStatus(InsightsState.QUEUED, cached)
      }
    }
  }

  private fun CompanyRatingsEntity.fresh(now: Long) =
    now - fetchedAt < (if (failed) FAILED_TTL else RATINGS_TTL).inWholeMilliseconds

  internal suspend fun fetch(jobId: Long) {
    val pages = pages ?: return
    val job = jobs.get(jobId) ?: return
    val key = CompanyNames.key(job.companyName)
    if (key.isEmpty()) return
    val reader = CompanyReader(pages)
    var ratings = dao.ratings(key)?.takeIf { it.fresh(clock()) && !it.failed }
    if (ratings == null) {
      val read = reader.ratings(job.companyName)
      ratings =
        CompanyRatingsEntity(
          companyKey = key,
          ratings = ApiJson.encodeToString(RATINGS, read.ratings),
          ambitionBoxSlug = read.ambitionBoxSlug,
          failed = read.failed,
          fetchedAt = clock(),
        )
      dao.upsertRatings(ratings)
    }
    // Tried again on the next request, salary included.
    if (ratings.failed) return
    val titleKey = titleKey(job.title)
    val salary = dao.salary(key, titleKey)
    if (salary == null || clock() - salary.fetchedAt >= RATINGS_TTL.inWholeMilliseconds) {
      val range = ratings.ambitionBoxSlug?.let { reader.salary(it, job.title) }
      dao.upsertSalary(
        CompanySalaryEntity(
          companyKey = key,
          titleKey = titleKey,
          salary = range?.let { ApiJson.encodeToString(SalaryRange.serializer(), it) },
          fetchedAt = clock(),
        )
      )
    }
  }

  private fun insights(
    company: String,
    ratings: CompanyRatingsEntity?,
    salary: CompanySalaryEntity?,
  ): CompanyInsights? {
    if (ratings == null) return null
    return CompanyInsights(
      company = company,
      ratings = ApiJson.decodeFromString(RATINGS, ratings.ratings),
      salary = salary?.salary?.let { ApiJson.decodeFromString(SalaryRange.serializer(), it) },
      fetchedAt = ratings.fetchedAt,
    )
  }

  companion object {
    // Ratings and pay change slowly; a long cache keeps the sites from seeing this IP often.
    val RATINGS_TTL = 30.days
    val FAILED_TTL = 6.hours

    /** Jobs found this recently are read first. */
    val RECENT = 6.hours

    /** The background reader starts this long after launch, once the first scan has settled. */
    val BACKFILL_START = 10.minutes

    /** How long the background reader rests once every job has insights. */
    val BACKFILL_IDLE = 1.hours
    private val QUEUE_POLL = 30.seconds

    /** The random rest between two background reads: 3 to 8 minutes. */
    const val GAP_MIN_MS = 3 * 60_000L
    const val GAP_MAX_MS = 8 * 60_000L
    private val RATINGS = ListSerializer(CompanyRating.serializer())
    private val log = LoggerFactory.getLogger("jobfinder.insights")

    /** Titles that differ only in case or punctuation share a salary. */
    fun titleKey(title: String) = InsightKeys.title(title)
  }
}

/** What [CompanyReader.ratings] found; [failed] when no site could be read at all. */
data class RatingsRead(
  val ratings: List<CompanyRating>,
  val ambitionBoxSlug: String?,
  val failed: Boolean,
)

/** Finds a company on each site and reads its pages. */
class CompanyReader(private val pages: InsightPages) {
  private val log = LoggerFactory.getLogger(CompanyReader::class.java)

  suspend fun ratings(company: String): RatingsRead {
    var failures = 0
    var slug: String? = null
    val ratings = mutableListOf<CompanyRating>()
    try {
      slug = ambitionBoxSlug(company)
      slug
        ?.let { pages.load(AmbitionBox.reviewsUrl(it), false) }
        ?.let(AmbitionBox::reviews)
        ?.let(ratings::add)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      failures++
      log.info("AmbitionBox for {} failed: {}", company, e.message)
    }
    try {
      glassdoor(company)?.let(ratings::add)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      failures++
      log.info("Glassdoor for {} failed: {}", company, e.message)
    }
    return RatingsRead(ratings, slug, failed = failures == SITES)
  }

  /** The salary for [title]'s role, or for the nearest role that has one. */
  suspend fun salary(slug: String, title: String): SalaryRange? {
    for (role in Roles.forTitle(title).take(MAX_ROLE_PAGES)) {
      val page = pages.load(AmbitionBox.salaryUrl(slug, role.slug), false)
      AmbitionBox.salary(page, role.exact)?.let {
        return it
      }
    }
    return null
  }

  private suspend fun ambitionBoxSlug(company: String): String? {
    val page = pages.load(AmbitionBox.searchUrl(company), waitForScripts = true)
    return AmbitionBox.searchSlugs(page.html).firstOrNull { CompanyNames.matches(company, it) }
  }

  private suspend fun glassdoor(company: String): CompanyRating? {
    val search = pages.load(Glassdoor.searchUrl(company), waitForScripts = true)
    val (_, url) =
      Glassdoor.searchResults(search).firstOrNull { CompanyNames.matches(company, it.first) }
        ?: return null
    val page = if (search.finalUrl == url) search else pages.load(url, false)
    return Glassdoor.overview(page)
  }

  private companion object {
    const val SITES = 2

    /** Role pages tried per title before giving up on a salary. */
    const val MAX_ROLE_PAGES = 6
  }
}

/** [InsightPages] in the app's hidden browsers, with the scan's pause after each page. */
class ScraperInsightPages(
  private val scraper: io.github.raghavsatyadev.jobfinder.server.scan.PageScraper,
  private val pacing: io.github.raghavsatyadev.jobfinder.server.scan.Pacing =
    io.github.raghavsatyadev.jobfinder.server.scan.Pacing(),
) : InsightPages {
  override suspend fun load(url: String, waitForScripts: Boolean): InsightPage {
    // One scroll waits for search results that scripts fill in after the load.
    val page =
      scraper.scrape(url, scrollTimes = if (waitForScripts) 1 else 0) {
        InsightPage(url, it.finalUrl, it.html, it.httpStatus)
      }
    kotlinx.coroutines.delay(pacing.betweenLinks())
    return page
  }
}
