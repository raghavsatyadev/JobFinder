@file:OptIn(ExperimentalAtomicApi::class)

package io.github.raghavsatyadev.jobfinder.cache

import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobCounts
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobPage
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.remote.DesktopUnreachableException
import io.github.raghavsatyadev.jobfinder.ui.platform.PhoneCopy
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.time.Clock
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * The phone's copy of the active computer's jobs and answers ([PhoneCacheDao]), and the changes
 * waiting for it. [computer] is the active computer's key, or `null` before one is paired; [online]
 * says whether its event stream is open.
 *
 * A read answers from the copy at once when the copy has the answer, and asks the computer again in
 * [scope] while it is online; [refreshed] fires when that brought something new. Only an answer the
 * copy lacks waits for the computer. [busy] is set while any of that runs.
 */
class PhoneCache(
  private val dao: PhoneCacheDao,
  val computer: () -> String?,
  val online: () -> Boolean,
  private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
  /** Runs the reads in the background; `null` (tests) skips them. */
  private val scope: CoroutineScope? = null,
) : PhoneCopy {
  private val running = AtomicInt(0)
  private val _busy = MutableStateFlow(false)
  override val busy: StateFlow<Boolean> = _busy.asStateFlow()

  private val _refreshed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** A background read brought an answer that differs from the one shown. */
  val refreshed: Flow<Unit> = _refreshed.asSharedFlow()

  /** When each key was last asked in the background: at most once per [REFRESH_GAP_MS]. */
  private val asked = AtomicReference(persistentHashMapOf<String, Long>())

  /** Runs [block] with [busy] set. */
  suspend fun <T> busyWith(block: suspend () -> T): T {
    _busy.value = running.incrementAndFetch() > 0
    try {
      return block()
    } finally {
      _busy.value = running.decrementAndFetch() > 0
    }
  }

  /**
   * The answer kept under [key] at once, asked again in the background; [remote]'s answer when none
   * is kept. Throws when the computer can't be reached and nothing is kept.
   */
  suspend fun <T> answer(key: String, serializer: KSerializer<T>, remote: suspend () -> T): T =
    readThrough(
      key = key,
      fromCache = { computer -> load(computer, key, serializer) },
      remote = remote,
      save = { computer, value -> save(computer, key, serializer, value) },
    )

  /** Like [answer], but only answers [keep] accepts are kept (a review still queued is not). */
  suspend fun <T> answerIf(
    key: String,
    serializer: KSerializer<T>,
    keep: (T) -> Boolean,
    remote: suspend () -> T,
  ): T =
    readThrough(
      key = key,
      fromCache = { computer -> load(computer, key, serializer) },
      remote = remote,
      save = { computer, value -> if (keep(value)) save(computer, key, serializer, value) },
    )

  /** Keeps [value] under [key] for the active computer, as if it had answered it. */
  suspend fun <T> keep(key: String, serializer: KSerializer<T>, value: T) {
    computer()?.let { save(it, key, serializer, value) }
  }

  /** The kept answer under [key], or `null`. */
  suspend fun <T> kept(key: String, serializer: KSerializer<T>): T? =
    computer()?.let { load(it, key, serializer) }

  /** Keeps [value] under [key] unless an answer is kept there already. */
  suspend fun <T> keepIfMissing(key: String, serializer: KSerializer<T>, value: T) {
    val computer = computer() ?: return
    if (dao.answer(computer, key) == null) save(computer, key, serializer, value)
  }

  /** Drops the answer kept under [key]: it changed, so the next read asks the computer. */
  suspend fun drop(key: String) {
    computer()?.let { dao.deleteAnswer(it, key) }
  }

  /** Drops every answer kept under [prefix] (`notes:`), but not those of imported jobs. */
  suspend fun dropAll(prefix: String) {
    computer()?.let { dao.deleteAnswers(it, prefix) }
  }

  /** Drops what [event] says changed on the computer, before open screens read it again. */
  suspend fun forget(event: ServerEvent) {
    when (event) {
      is ServerEvent.AtsScored -> drop("$REVIEWS${event.jobId}")
      ServerEvent.ResumeChanged -> {
        drop(RESUME)
        dropAll(REVIEWS)
      }
      is ServerEvent.InsightsReady -> event.jobIds.forEach { drop("$INSIGHTS$it") }
      is ServerEvent.GhostChecked -> event.jobIds.forEach { drop("$GHOST$it") }
      is ServerEvent.TipsReady -> drop("$TIPS${event.jobId}")
      is ServerEvent.BrokenLinks -> drop(LINKS)
      ServerEvent.DetailLayoutChanged -> drop(DETAIL_LAYOUT)
      else -> Unit
    }
  }

  /** Drops the settings, the searches and the resume: a backup changed them on the computer. */
  suspend fun dropSettings() {
    for (key in listOf(SCANNER, SERVER, MATCHING, LINKS, RESUME, DETAIL_LAYOUT)) drop(key)
  }

  /**
   * [fromCache] at once, asked again in the background while online; else [remote], kept with
   * [save]. When the computer can't be reached: [fallback] (by default [fromCache]), or the error.
   */
  internal suspend fun <T> readThrough(
    key: String,
    fromCache: suspend (computer: String) -> T?,
    remote: suspend () -> T,
    save: suspend (computer: String, T) -> Unit,
    fallback: suspend (computer: String) -> T? = fromCache,
  ): T {
    val computer = computer() ?: return remote()
    val kept = fromCache(computer)
    if (kept != null) {
      if (online()) refreshLater(computer, key, kept, remote, save)
      return kept
    }
    return try {
      busyWith { remote() }.also { save(computer, it) }
    } catch (e: DesktopUnreachableException) {
      fallback(computer) ?: throw e
    }
  }

  private fun <T> refreshLater(
    computer: String,
    key: String,
    shown: T,
    remote: suspend () -> T,
    save: suspend (computer: String, T) -> Unit,
  ) {
    val scope = scope ?: return
    val id = "$computer/$key"
    val now = clock()
    while (true) {
      val seen = asked.load()
      val last = seen[id]
      if (last != null && now - last < REFRESH_GAP_MS) return
      if (asked.compareAndSet(seen, seen.put(id, now))) break
    }
    scope.launch {
      try {
        val fresh = busyWith { remote() }
        // The user may have switched to another computer meanwhile.
        if (computer() == computer) {
          save(computer, fresh)
          if (fresh != shown) _refreshed.tryEmit(Unit)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // The kept answer stays; a later read asks again.
      }
    }
  }

  /** Marks a full copy of [computer]'s jobs as made: job lists read the copy from now on. */
  suspend fun markSynced(computer: String) {
    dao.upsertAnswer(CachedAnswer(computer, SYNCED, "true", clock()))
  }

  /**
   * `true` when job lists can come from the copy: a full copy was made, or the computer is out of
   * reach and the copy has jobs (an imported backup).
   */
  suspend fun listsFromCopy(computer: String): Boolean =
    hasJobs(computer) && (!online() || dao.answer(computer, SYNCED) != null)

  private suspend fun <T> load(computer: String, key: String, serializer: KSerializer<T>): T? =
    dao.answer(computer, key)?.let { ApiJson.decodeFromString(serializer, it.json) }

  private suspend fun <T> save(
    computer: String,
    key: String,
    serializer: KSerializer<T>,
    value: T,
  ) {
    dao.upsertAnswer(
      CachedAnswer(computer, key, ApiJson.encodeToString(serializer, value), clock())
    )
  }

  // Jobs

  /** Keeps [jobs]; a job without a description keeps the one read before. */
  suspend fun storeJobs(computer: String, jobs: List<Job>) {
    if (jobs.isEmpty()) return
    dao.upsertJobs(
      jobs.map { job ->
        CachedJob(
          computer = computer,
          id = job.id,
          status = job.status.wire,
          updatedAt = job.updatedAt,
          job = ApiJson.encodeToString(Job.serializer(), job.copy(description = null)),
          description = job.description ?: dao.job(computer, job.id)?.description,
        )
      }
    )
  }

  /**
   * Replaces the kept jobs in [status] with [jobs], the whole list from the computer. Jobs with a
   * change still waiting stay as the phone has them.
   */
  suspend fun replaceStatus(computer: String, status: JobStatus, jobs: List<Job>) {
    val waiting = dao.pending(computer).map { it.jobId }.toSet()
    storeJobs(computer, jobs.filter { it.id !in waiting })
    val keep = jobs.map { it.id } + waiting
    if (keep.isEmpty()) dao.deleteStatus(computer, status.wire)
    else dao.deleteOthers(computer, status.wire, keep)
  }

  suspend fun job(computer: String, id: Long): Job? = dao.job(computer, id)?.toJob()

  /** A page of the kept jobs, as the computer would page them. */
  suspend fun page(
    computer: String,
    status: JobStatus,
    filter: JobFilter,
    limit: Int,
    after: String?,
  ): JobPage {
    val cursor = after?.let(::parseCursor)
    val sort = filter.sort
    val statuses = if (filter.starred) JobFilter.STARRED_STATUSES else listOf(status)
    val matching =
      statuses
        .flatMap { dao.jobs(computer, it.wire) }
        .asSequence()
        // Lists carry no description, as from the computer.
        .map { it.toJob().copy(description = null) }
        .filter { filter.matches(it) }
        .filter { job -> cursor == null || cursor.isBefore(sort.key(job), job) }
        .sortedWith(sort.comparator)
        .take(limit + 1)
        .toList()
    val page = matching.take(limit)
    val next =
      if (matching.size > limit)
        page.last().let {
          "${it.id}!${it.updatedAt}" + if (sort == JobSort.LATEST) "" else "!${sort.key(it)}"
        }
      else null
    return JobPage(page, next)
  }

  /** Counts of the kept jobs per status, for [filter]. */
  suspend fun counts(computer: String, filter: JobFilter, status: JobStatus?): JobCounts {
    val statuses = status?.let(::listOf) ?: JobStatus.entries
    if (filter.copy(sort = JobSort.LATEST) == JobFilter()) {
      val all = dao.statusCounts(computer).associate { it.status to it.count }
      return JobCounts(statuses.associateWith { all[it.wire] ?: 0 })
    }
    return JobCounts(
      statuses.associateWith { s ->
        dao.jobs(computer, s.wire).count { filter.matches(it.toJob()) }
      }
    )
  }

  /** Up to [limit] kept jobs in [statuses] with no description yet, newest first. */
  suspend fun withoutDescription(
    computer: String,
    statuses: List<JobStatus>,
    limit: Int,
  ): List<Long> = dao.withoutDescription(computer, statuses.map { it.wire }, limit)

  /** `true` once a full list of jobs was kept for [computer]. */
  suspend fun hasJobs(computer: String): Boolean = dao.jobCount(computer) > 0

  /** Moves a kept job to [status] now, as the computer will once the change reaches it. */
  suspend fun setStatus(computer: String, id: Long, status: JobStatus): Job? =
    edit(computer, id) { it.copy(status = status, updatedAt = clock()) }

  /** Sets a kept job's labels; `updatedAt` stays, as on the computer. */
  suspend fun setLabels(computer: String, id: Long, labels: List<JobLabel>): Job? =
    edit(computer, id) { it.copy(labels = labels) }

  private suspend fun edit(computer: String, id: Long, change: (Job) -> Job): Job? {
    val row = dao.job(computer, id) ?: return null
    val job = change(row.toJob())
    dao.upsertJobs(
      listOf(
        row.copy(
          status = job.status.wire,
          updatedAt = job.updatedAt,
          job = ApiJson.encodeToString(Job.serializer(), job.copy(description = null)),
        )
      )
    )
    return job
  }

  // Changes made offline

  suspend fun queueStatus(computer: String, id: Long, status: JobStatus) {
    dao.addPending(PendingChange(0, computer, id, PendingChange.STATUS, status.wire, clock()))
  }

  suspend fun queueLabels(computer: String, id: Long, labels: List<JobLabel>) {
    val json = ApiJson.encodeToString(ListSerializer(JobLabel.serializer()), labels)
    dao.addPending(PendingChange(0, computer, id, PendingChange.LABELS, json, clock()))
  }

  suspend fun pending(computer: String): List<PendingChange> = dao.pending(computer)

  suspend fun done(change: PendingChange) = dao.deletePending(change.id)

  // Backups imported on the phone

  /**
   * Keeps [jobs] from an imported backup under negative ids, skipping those the computer's copy
   * already has (by their id on the job board), and records [file] to send to the computer. A job
   * imported before is refreshed under its id (keeping its status and labels), so importing again
   * brings what an older app left out. Returns the jobs kept, with their negative ids.
   */
  suspend fun keepImport(computer: String, file: String, jobs: List<Job>): List<Job> {
    val known =
      dao.allJobs(computer).associate {
        ApiJson.decodeFromString(Job.serializer(), it).let { job -> job.externalId to job }
      }
    val seen = mutableSetOf<String>()
    val added = jobs.mapNotNull { job ->
      if (!seen.add(job.externalId)) return@mapNotNull null
      val kept = known[job.externalId]
      when {
        kept == null -> job.copy(id = -job.id)
        // What the user changed here stays.
        kept.id < 0 -> job.copy(id = kept.id, status = kept.status, labels = kept.labels)
        else -> null
      }
    }
    storeJobs(computer, added)
    dao.addImport(PendingImport(0, computer, file, added.size, clock()))
    return added
  }

  suspend fun imports(computer: String): List<PendingImport> = dao.imports(computer)

  /** The ids of the jobs kept from [computer] itself (imported ones left out), by external id. */
  suspend fun computerJobIds(computer: String): Map<String, Long> =
    dao
      .allJobs(computer)
      .map { ApiJson.decodeFromString(Job.serializer(), it) }
      .filter { it.id > 0 }
      .associate { it.externalId to it.id }

  /** Every job the copy keeps from [computer], with descriptions; imported ones left out. */
  suspend fun computerJobs(computer: String): List<Job> =
    dao.computerJobs(computer).map { it.toJob() }

  /** [pending] reached the computer: its jobs come from the computer from now on. */
  suspend fun importSent(pending: PendingImport) {
    dao.deleteImport(pending.id)
    if (dao.imports(pending.computer).isEmpty()) dao.deleteImportedJobs(pending.computer)
  }

  /** Everything kept for [computer]. */
  suspend fun forget(computer: String) = dao.forget(computer)

  private fun CachedJob.toJob(): Job =
    ApiJson.decodeFromString(Job.serializer(), job).copy(description = description)

  companion object {
    const val LINKS = "links"
    const val SITES = "sites"
    const val SCANNER = "scanner"
    const val SERVER = "server"
    const val MATCHING = "matching"
    const val DETAIL_LAYOUT = "detail_layout"
    const val RESUME = "resume"
    const val REVIEWS = "ats:"
    const val INSIGHTS = "insights:"
    const val GHOST = "ghost:"
    const val TIPS = "tips:"
    const val NOTES = "notes:"
    private const val SYNCED = "synced"

    /** A screen read again within this time shows the copy without asking the computer. */
    const val REFRESH_GAP_MS = 30_000L

    /** `"<id>!<updatedAtMs>"` as `id to updatedAt`, or `null`. */
    fun parseCursor(token: String): Cursor? {
      val parts = token.split('!').takeIf { it.size == 2 || it.size == 3 } ?: return null
      return Cursor(
        id = parts[0].toLongOrNull() ?: return null,
        updatedAt = parts[1].toLongOrNull() ?: return null,
        key = if (parts.size == 3) parts[2].toLongOrNull() ?: return null else null,
      )
    }
  }

  /** The last job of a page, as the computer's page token names it. */
  data class Cursor(val id: Long, val updatedAt: Long, val key: Long?) {
    /** This cursor comes before [job] (whose sort key is [jobKey]) in the list's order. */
    fun isBefore(jobKey: Long, job: Job): Boolean {
      val k = key ?: Long.MAX_VALUE
      return jobKey < k ||
        (jobKey == k && (job.updatedAt < updatedAt || (job.updatedAt == updatedAt && job.id < id)))
    }
  }
}

/**
 * The filter as the computer applies it, near enough offline: every search word in the title or the
 * company (no stemming), and any of each chosen list.
 */
fun JobFilter.matches(job: Job): Boolean {
  val words = search?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
  val text = "${job.title} ${job.companyName}".lowercase()
  return words.all { it in text } &&
    matchesSource(job.siteId, job.linkId) &&
    (labels.isEmpty() || job.labels.any { it in labels }) &&
    (workModes.isEmpty() || job.workMode?.let { it in workModes } == true) &&
    (applyMethods.isEmpty() || job.applyMethod in applyMethods) &&
    (!starred || job.starred)
}
