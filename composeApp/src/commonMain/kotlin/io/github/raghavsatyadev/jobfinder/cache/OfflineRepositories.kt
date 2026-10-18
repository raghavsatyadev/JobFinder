package io.github.raghavsatyadev.jobfinder.cache

import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.GhostStatus
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobCounts
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobPage
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.PromptEdit
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.TipsState
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import io.github.raghavsatyadev.jobfinder.shared.remote.DesktopUnreachableException
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

/**
 * Jobs from the phone's copy ([PhoneCache]) once it holds every job of the computer, kept in step
 * by [PhoneSync]; until then, and for a job without its description, from the computer. A status or
 * label change made offline shows at once and reaches the computer once it is back.
 */
class OfflineJobsRepository(
  private val remote: JobsRepository,
  private val cache: PhoneCache,
  /** Asks for a sync in the background (at most every so often). */
  private val requestSync: () -> Unit = {},
  /** One sync now; `false` when the computer could not be reached. */
  private val syncNow: suspend () -> Boolean = { false },
) : JobsRepository {
  override suspend fun listJobs(
    status: JobStatus,
    filter: JobFilter,
    limit: Int,
    after: String?,
  ): JobPage {
    val computer = cache.computer() ?: return remote.listJobs(status, filter, limit, after)
    if (cache.listsFromCopy(computer)) {
      requestSync()
      return cache.page(computer, status, filter, limit, after)
    }
    return try {
      cache
        .busyWith { remote.listJobs(status, filter, limit, after) }
        .also { cache.storeJobs(computer, it.jobs) }
    } catch (e: DesktopUnreachableException) {
      if (!cache.hasJobs(computer)) throw e
      cache.page(computer, status, filter, limit, after)
    }
  }

  override suspend fun countJobs(filter: JobFilter, status: JobStatus?): JobCounts {
    val computer = cache.computer() ?: return remote.countJobs(filter, status)
    if (cache.listsFromCopy(computer)) return cache.counts(computer, filter, status)
    return try {
      cache.busyWith { remote.countJobs(filter, status) }
    } catch (e: DesktopUnreachableException) {
      if (!cache.hasJobs(computer)) throw e
      cache.counts(computer, filter, status)
    }
  }

  /** The kept job at once when it has its description (or the computer is away). */
  override suspend fun getJob(id: Long): Job? =
    if (id < 0) cache.computer()?.let { cache.job(it, id) }
    else
      cache.readThrough(
        key = "job:$id",
        fromCache = { computer ->
          cache.job(computer, id)?.takeIf { it.description != null || !cache.online() }
        },
        remote = { remote.getJob(id) },
        save = { computer, job -> job?.let { cache.storeJobs(computer, listOf(it)) } },
        fallback = { computer -> cache.job(computer, id) },
      )

  override suspend fun updateStatus(id: Long, status: JobStatus) {
    val computer = cache.computer() ?: return remote.updateStatus(id, status)
    // An imported job the computer does not have yet: changed on the phone only.
    if (id < 0) {
      cache.setStatus(computer, id, status)
      return
    }
    if (cache.online()) {
      try {
        remote.updateStatus(id, status)
        cache.setStatus(computer, id, status)
        return
      } catch (_: DesktopUnreachableException) {
        // Kept for later below.
      }
    }
    cache.setStatus(computer, id, status) ?: throw DesktopUnreachableException(NoCopy(id))
    cache.queueStatus(computer, id, status)
  }

  override suspend fun updateLabels(id: Long, labels: List<JobLabel>): Job? {
    val computer = cache.computer() ?: return remote.updateLabels(id, labels)
    if (id < 0) return cache.setLabels(computer, id, labels)
    if (cache.online()) {
      try {
        return remote.updateLabels(id, labels)?.also { cache.storeJobs(computer, listOf(it)) }
      } catch (_: DesktopUnreachableException) {
        // Kept for later below.
      }
    }
    val job = cache.setLabels(computer, id, labels) ?: throw DesktopUnreachableException(NoCopy(id))
    cache.queueLabels(computer, id, labels)
    return job
  }

  /** Needs the computer; the copy follows at once. */
  override suspend fun changeAllStatuses(from: JobStatus, to: JobStatus): Int =
    remote.changeAllStatuses(from, to).also { syncNow() }

  private class NoCopy(id: Long) : IllegalStateException("Job $id is not kept on the phone")
}

/** Saved searches; the list is kept, changes need the computer. */
class OfflineLinksRepository(private val remote: LinksRepository, private val cache: PhoneCache) :
  LinksRepository {
  override suspend fun listLinks(): List<Link> =
    cache.answer(PhoneCache.LINKS, LINK_LIST) { remote.listLinks() }

  override suspend fun getLink(id: Long): Link? =
    cache.kept(PhoneCache.LINKS, LINK_LIST)?.firstOrNull { it.id == id }
      ?: cache.busyWith { remote.getLink(id) }

  override suspend fun updateLink(id: Long, title: String, url: String): Link? =
    remote.updateLink(id, title, url).also { cache.drop(PhoneCache.LINKS) }

  override suspend fun deleteLink(id: Long) {
    remote.deleteLink(id)
    cache.drop(PhoneCache.LINKS)
  }

  private companion object {
    val LINK_LIST = ListSerializer(Link.serializer())
  }
}

class OfflineSitesRepository(private val remote: SitesRepository, private val cache: PhoneCache) :
  SitesRepository {
  override suspend fun listSites(): List<Site> =
    cache.answer(PhoneCache.SITES, ListSerializer(Site.serializer())) { remote.listSites() }
}

/** Notes are kept to read offline; writing one needs the computer. */
class OfflineNotesRepository(private val remote: NotesRepository, private val cache: PhoneCache) :
  NotesRepository {
  override suspend fun listNotes(jobId: Long): List<Note> =
    cache.answer("${PhoneCache.NOTES}$jobId", ListSerializer(Note.serializer())) {
      remote.listNotes(jobId)
    }

  override suspend fun createNote(jobId: Long, text: String, files: List<String>): Note =
    remote.createNote(jobId, text, files).also { cache.drop("${PhoneCache.NOTES}$jobId") }

  override suspend fun updateNote(id: Long, text: String): Note? =
    remote.updateNote(id, text).also { dropNotes(it) }

  override suspend fun addFileToNote(id: Long, file: String): Note? =
    remote.addFileToNote(id, file).also { dropNotes(it) }

  override suspend fun deleteNote(id: Long) {
    remote.deleteNote(id)
    cache.dropAll(PhoneCache.NOTES)
  }

  private suspend fun dropNotes(note: Note?) {
    if (note != null) cache.drop("${PhoneCache.NOTES}${note.jobId}")
    else cache.dropAll(PhoneCache.NOTES)
  }
}

class OfflineMatchingRepository(
  private val remote: MatchingRepository,
  private val cache: PhoneCache,
) : MatchingRepository {
  override suspend fun getConfig(): AdvancedMatchingConfig =
    cache.queuedEdit(PhoneCache.MATCHING, CONFIG)
      ?: cache.answer(PhoneCache.MATCHING, CONFIG) { remote.getConfig() }

  /** Offline the change is kept on the phone and sent once the computer is back. */
  override suspend fun updateConfig(config: AdvancedMatchingConfig): AdvancedMatchingConfig {
    if (cache.computer() == null) return remote.updateConfig(config)
    if (cache.online()) {
      try {
        return remote.updateConfig(config).also {
          cache.keep(PhoneCache.MATCHING, CONFIG, it)
          cache.dropEdit(PhoneCache.MATCHING)
        }
      } catch (_: DesktopUnreachableException) {
        // Kept for later below.
      }
    }
    cache.keep(PhoneCache.MATCHING, CONFIG, config)
    cache.queueEdit(PhoneCache.MATCHING, CONFIG, config)
    return config
  }

  private companion object {
    val CONFIG = AdvancedMatchingConfig.serializer()
  }
}

/**
 * The AI instructions from the phone's copy, with the edits made offline on top; offline an edit is
 * kept on the phone and sent once the computer is back.
 */
class OfflineAiPromptsRepository(
  private val remote: AiPromptsRepository,
  private val cache: PhoneCache,
) : AiPromptsRepository {
  override suspend fun listPrompts(): List<AiPrompt> =
    cache.answer(PhoneCache.PROMPTS, PROMPTS) { remote.listPrompts() }.map { it.withQueuedEdit() }

  override suspend fun savePrompt(id: PromptId, text: String): AiPrompt = write(id, text)

  override suspend fun resetPrompt(id: PromptId): AiPrompt = write(id, null)

  /** Saves [text], or resets with `null`. */
  private suspend fun write(id: PromptId, text: String?): AiPrompt {
    val send = suspend { if (text == null) remote.resetPrompt(id) else remote.savePrompt(id, text) }
    if (cache.computer() == null) return send()
    if (cache.online()) {
      try {
        return send().also {
          keepPrompt(it)
          cache.dropEdit(PhoneCache.promptEdit(id))
        }
      } catch (_: DesktopUnreachableException) {
        // Kept for later below.
      }
    }
    val kept =
      cache.kept(PhoneCache.PROMPTS, PROMPTS)?.firstOrNull { it.id == id }
        ?: throw DesktopUnreachableException(IllegalStateException("No AI instructions kept"))
    val saved = kept.copy(text = text ?: kept.defaultText)
    keepPrompt(saved)
    cache.queueEdit(PhoneCache.promptEdit(id), PromptEdit.serializer(), PromptEdit(text))
    return saved
  }

  private suspend fun keepPrompt(prompt: AiPrompt) {
    val kept = cache.kept(PhoneCache.PROMPTS, PROMPTS) ?: return
    cache.keep(PhoneCache.PROMPTS, PROMPTS, kept.map { if (it.id == prompt.id) prompt else it })
  }

  private suspend fun AiPrompt.withQueuedEdit(): AiPrompt =
    cache.queuedEdit(PhoneCache.promptEdit(id), PromptEdit.serializer())?.let {
      copy(text = it.text ?: defaultText)
    } ?: this

  private companion object {
    val PROMPTS = ListSerializer(AiPrompt.serializer())
  }
}

class OfflineSettingsRepository(
  private val remote: SettingsRepository,
  private val cache: PhoneCache,
) : SettingsRepository {
  override suspend fun getScannerSettings(): ScannerSettings =
    cache.answer(PhoneCache.SCANNER, ScannerSettings.serializer()) { remote.getScannerSettings() }

  override suspend fun updateScannerSettings(settings: ScannerSettings): ScannerSettings =
    remote.updateScannerSettings(settings).also {
      cache.keep(PhoneCache.SCANNER, ScannerSettings.serializer(), it)
    }

  override suspend fun getServerSettings(): ServerSettings =
    cache.answer(PhoneCache.SERVER, ServerSettings.serializer()) { remote.getServerSettings() }

  override suspend fun updateServerSettings(settings: ServerSettings): ServerSettings =
    remote.updateServerSettings(settings).also {
      cache.keep(PhoneCache.SERVER, ServerSettings.serializer(), it)
    }

  override suspend fun getDetailLayout(): DetailLayout =
    cache.answer(PhoneCache.DETAIL_LAYOUT, DetailLayout.serializer()) { remote.getDetailLayout() }

  override suspend fun updateDetailLayout(layout: DetailLayout): DetailLayout =
    remote.updateDetailLayout(layout).also {
      cache.keep(PhoneCache.DETAIL_LAYOUT, DetailLayout.serializer(), it)
    }
}

/** The resume and finished reviews are kept; a review still to do needs the computer. */
class OfflineAtsRepository(private val remote: AtsRepository, private val cache: PhoneCache) :
  AtsRepository {
  override suspend fun getResume(): ResumeInfo? =
    cache.answer(PhoneCache.RESUME, ResumeInfo.serializer().nullable) { remote.getResume() }

  override suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeInfo =
    remote.uploadResume(fileName, bytes).also {
      cache.keep(PhoneCache.RESUME, ResumeInfo.serializer().nullable, it)
      cache.dropAll(PhoneCache.REVIEWS)
    }

  override suspend fun deleteResume() {
    remote.deleteResume()
    cache.keep(PhoneCache.RESUME, ResumeInfo.serializer().nullable, null)
    cache.dropAll(PhoneCache.REVIEWS)
  }

  override suspend fun requestReview(jobId: Long): AtsStatus {
    // An imported job the computer doesn't have yet: only the review from the backup.
    if (jobId < 0) {
      return cache.kept("${PhoneCache.REVIEWS}$jobId", AtsStatus.serializer())
        ?: AtsStatus(AtsState.QUEUED)
    }
    return cache.answerIf(
      "${PhoneCache.REVIEWS}$jobId",
      AtsStatus.serializer(),
      { it.state == AtsState.READY },
    ) {
      remote.requestReview(jobId)
    }
  }
}

/** Company ratings and salaries already read are kept. */
class OfflineInsightsRepository(
  private val remote: InsightsRepository,
  private val cache: PhoneCache,
) : InsightsRepository {
  override suspend fun requestInsights(jobId: Long, retry: Boolean): InsightsStatus {
    val key = "${PhoneCache.INSIGHTS}$jobId"
    // An imported job the computer doesn't have yet: only what the backup had.
    if (jobId < 0) {
      return cache.kept(key, InsightsStatus.serializer())
        ?: InsightsStatus(InsightsState.UNAVAILABLE)
    }
    // Trying again asks the computer, not the copy.
    if (retry) cache.drop(key)
    return cache.answerIf(key, InsightsStatus.serializer(), { it.state == InsightsState.READY }) {
      remote.requestInsights(jobId, retry)
    }
  }
}

class OfflineTipsRepository(private val remote: TipsRepository, private val cache: PhoneCache) :
  TipsRepository {
  override suspend fun requestTips(jobId: Long, write: Boolean): TipsStatus {
    val key = "${PhoneCache.TIPS}$jobId"
    if (jobId < 0)
      return cache.kept(key, TipsStatus.serializer()) ?: TipsStatus(TipsState.UNAVAILABLE)
    if (write) cache.drop(key)
    return cache.answerIf(key, TipsStatus.serializer(), { it.state == TipsState.READY }) {
      remote.requestTips(jobId, write)
    }
  }
}

class OfflineGhostRepository(private val remote: GhostRepository, private val cache: PhoneCache) :
  GhostRepository {
  override suspend fun requestGhostCheck(jobId: Long, retry: Boolean): GhostStatus {
    val key = "${PhoneCache.GHOST}$jobId"
    if (jobId < 0) {
      return cache.kept(key, GhostStatus.serializer()) ?: GhostStatus(InsightsState.UNAVAILABLE)
    }
    if (retry) cache.drop(key)
    return cache.answerIf(key, GhostStatus.serializer(), { it.state == InsightsState.READY }) {
      remote.requestGhostCheck(jobId, retry)
    }
  }
}

/**
 * The computer's scans and events. Each event first drops what it says changed from the copy; after
 * each sync comes a quiet [ServerEvent.NewJobs] with no ids, so open lists reload, and
 * [ServerEvent.CopyRefreshed] when something shown from the copy had changed.
 */
class SyncedScanRepository(
  private val remote: ScanRepository,
  sync: PhoneSync,
  private val cache: PhoneCache,
) : ScanRepository by remote {
  override val events: Flow<ServerEvent> =
    merge(
      remote.events.onEach { cache.forget(it) },
      sync.synced,
      cache.refreshed.map { ServerEvent.CopyRefreshed },
    )

  override suspend fun createLink(
    title: String,
    url: String,
    force: Boolean,
    siteName: String?,
  ): CreateLinkResponse =
    remote.createLink(title, url, force, siteName).also { cache.drop(PhoneCache.LINKS) }

  override suspend fun scanJob(jobId: Long): Job =
    remote.scanJob(jobId).also { job -> cache.computer()?.let { cache.storeJobs(it, listOf(job)) } }
}
