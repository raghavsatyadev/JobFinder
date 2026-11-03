package io.github.raghavsatyadev.jobfinder.ui.fake

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
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
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * In-memory repositories with sample data, for previews, screenshot tests, ViewModel tests and the
 * Android debug build. One [FakeBackend] holds the data, so a status change in one screen shows in
 * the others. [latencyMillis] slows every call (to show loading states); [failWith] makes every
 * call throw (to show error states).
 */
class FakeBackend(
  jobs: List<Job> = SampleData.jobs,
  links: List<Link> = SampleData.links,
  val sites: List<Site> = SampleData.sites,
  notes: List<Note> = SampleData.notes,
  matching: AdvancedMatchingConfig = SampleData.matching,
  resume: ResumeInfo? = SampleData.resume,
  reviews: Map<Long, AtsReview> = mapOf(1L to SampleData.review),
  insights: Map<Long, CompanyInsights> = mapOf(1L to SampleData.insights),
  private val clock: () -> Long = { SampleData.NOW },
) {
  var latencyMillis: Long = 0
  var failWith: Exception? = null

  /** `createLink` without `force` fails with CONFLICT, as for a page with no job list. */
  var createLinkConflict: Boolean = false

  val jobs = MutableStateFlow(jobs)
  val links = MutableStateFlow(links)
  val notes = MutableStateFlow(notes)
  val matching = MutableStateFlow(matching)
  val scanner = MutableStateFlow(ScannerSettings())
  val server = MutableStateFlow(ServerSettings())
  val detailLayout = MutableStateFlow(DetailLayout.DEFAULT)
  val scanState = MutableStateFlow(ScanState(lastFinishedAt = SampleData.NOW - 30 * 60_000))
  val events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 16)
  val resume = MutableStateFlow(resume)
  val reviews = MutableStateFlow(reviews)
  val insights = MutableStateFlow(insights)

  internal suspend fun call() {
    if (latencyMillis > 0) delay(latencyMillis)
    failWith?.let { throw it }
  }

  internal fun now() = clock()

  val jobsRepository: JobsRepository by lazy { FakeJobsRepository(this) }
  val linksRepository: LinksRepository by lazy { FakeLinksRepository(this) }
  val sitesRepository: SitesRepository by lazy { FakeSitesRepository(this) }
  val notesRepository: NotesRepository by lazy { FakeNotesRepository(this) }
  val matchingRepository: MatchingRepository by lazy { FakeMatchingRepository(this) }
  val settingsRepository: SettingsRepository by lazy { FakeSettingsRepository(this) }
  val scanRepository: ScanRepository by lazy { FakeScanRepository(this) }
  val atsRepository: AtsRepository by lazy { FakeAtsRepository(this) }
  val insightsRepository: InsightsRepository by lazy { FakeInsightsRepository(this) }
}

/** Jobs in [FakeBackend.insights] are ready; the rest stay queued. */
internal class FakeInsightsRepository(private val b: FakeBackend) : InsightsRepository {
  override suspend fun requestInsights(jobId: Long, retry: Boolean): InsightsStatus {
    b.call()
    val insights = b.insights.value[jobId] ?: return InsightsStatus(InsightsState.QUEUED)
    return InsightsStatus(InsightsState.READY, insights)
  }
}

/** Uploads keep only the name; reviews come from [FakeBackend.reviews], other jobs stay queued. */
internal class FakeAtsRepository(private val b: FakeBackend) : AtsRepository {
  override suspend fun getResume(): ResumeInfo? {
    b.call()
    return b.resume.value
  }

  override suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeInfo {
    b.call()
    val info = SampleData.resume.copy(fileName = fileName, uploadedAt = b.now())
    b.resume.value = info
    b.reviews.value = emptyMap()
    b.events.tryEmit(ServerEvent.ResumeChanged)
    return info
  }

  override suspend fun deleteResume() {
    b.call()
    b.resume.value = null
    b.reviews.value = emptyMap()
    b.events.tryEmit(ServerEvent.ResumeChanged)
  }

  override suspend fun requestReview(jobId: Long): AtsStatus {
    b.call()
    if (b.resume.value == null) return AtsStatus(AtsState.NO_RESUME)
    val job = b.jobs.value.firstOrNull { it.id == jobId }
    if (job?.description.isNullOrBlank()) return AtsStatus(AtsState.NO_DESCRIPTION)
    val review = b.reviews.value[jobId] ?: return AtsStatus(AtsState.QUEUED)
    return AtsStatus(AtsState.READY, review)
  }
}

internal class FakeJobsRepository(private val b: FakeBackend) : JobsRepository {
  override suspend fun listJobs(
    status: JobStatus,
    filter: JobFilter,
    limit: Int,
    after: String?,
  ): JobPage {
    b.call()
    val all =
      b.jobs.value
        .filter {
          (if (filter.starred) it.status in JobFilter.STARRED_STATUSES else it.status == status) &&
            filter.matches(it)
        }
        .sortedWith(filter.sort.comparator)
    val start = after?.toIntOrNull() ?: 0
    val page = all.drop(start).take(limit).map { it.copy(description = null) }
    val next = if (start + limit < all.size) "${start + limit}" else null
    return JobPage(page, next)
  }

  override suspend fun countJobs(filter: JobFilter, status: JobStatus?): JobCounts {
    b.call()
    return JobCounts(
      b.jobs.value
        .filter { (status == null || it.status == status) && filter.matches(it) }
        .groupingBy { it.status }
        .eachCount()
    )
  }

  override suspend fun getJob(id: Long): Job? {
    b.call()
    return b.jobs.value.firstOrNull { it.id == id }
  }

  override suspend fun updateStatus(id: Long, status: JobStatus) {
    b.call()
    b.jobs.value =
      b.jobs.value.map { if (it.id == id) it.copy(status = status, updatedAt = b.now()) else it }
  }

  override suspend fun updateLabels(id: Long, labels: List<JobLabel>): Job? {
    b.call()
    b.jobs.value = b.jobs.value.map { if (it.id == id) it.copy(labels = labels) else it }
    return b.jobs.value.firstOrNull { it.id == id }
  }

  override suspend fun changeAllStatuses(from: JobStatus, to: JobStatus): Int {
    b.call()
    val count = b.jobs.value.count { it.status == from }
    b.jobs.value =
      b.jobs.value.map { if (it.status == from) it.copy(status = to, updatedAt = b.now()) else it }
    return count
  }

  private fun JobFilter.matches(job: Job): Boolean {
    val words = search?.lowercase()?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
    val text = "${job.title} ${job.companyName}".lowercase()
    return words.all { it in text } &&
      matchesSource(job.siteId, job.linkId) &&
      (labels.isEmpty() || job.labels.any { it in labels }) &&
      (workModes.isEmpty() || job.workMode in workModes) &&
      (applyMethods.isEmpty() || job.applyMethod in applyMethods) &&
      (!starred || job.starred)
  }
}

internal class FakeLinksRepository(private val b: FakeBackend) : LinksRepository {
  override suspend fun listLinks(): List<Link> {
    b.call()
    return b.links.value.sortedByDescending { it.id }
  }

  override suspend fun getLink(id: Long): Link? {
    b.call()
    return b.links.value.firstOrNull { it.id == id }
  }

  override suspend fun updateLink(id: Long, title: String, url: String): Link? {
    b.call()
    b.links.value = b.links.value.map { if (it.id == id) it.copy(title = title, url = url) else it }
    return b.links.value.firstOrNull { it.id == id }
  }

  override suspend fun deleteLink(id: Long) {
    b.call()
    b.links.value = b.links.value.filterNot { it.id == id }
  }
}

internal class FakeSitesRepository(private val b: FakeBackend) : SitesRepository {
  override suspend fun listSites(): List<Site> {
    b.call()
    return b.sites
  }
}

internal class FakeNotesRepository(private val b: FakeBackend) : NotesRepository {
  override suspend fun listNotes(jobId: Long): List<Note> {
    b.call()
    return b.notes.value.filter { it.jobId == jobId }.sortedByDescending { it.createdAt }
  }

  override suspend fun createNote(jobId: Long, text: String, files: List<String>): Note {
    b.call()
    val note = Note((b.notes.value.maxOfOrNull { it.id } ?: 0) + 1, jobId, text, files, b.now())
    b.notes.value = b.notes.value + note
    return note
  }

  override suspend fun updateNote(id: Long, text: String): Note? {
    b.call()
    b.notes.value = b.notes.value.map { if (it.id == id) it.copy(text = text) else it }
    return b.notes.value.firstOrNull { it.id == id }
  }

  override suspend fun addFileToNote(id: Long, file: String): Note? {
    b.call()
    b.notes.value = b.notes.value.map { if (it.id == id) it.copy(files = it.files + file) else it }
    return b.notes.value.firstOrNull { it.id == id }
  }

  override suspend fun deleteNote(id: Long) {
    b.call()
    b.notes.value = b.notes.value.filterNot { it.id == id }
  }
}

internal class FakeMatchingRepository(private val b: FakeBackend) : MatchingRepository {
  override suspend fun getConfig(): AdvancedMatchingConfig {
    b.call()
    return b.matching.value
  }

  override suspend fun updateConfig(config: AdvancedMatchingConfig): AdvancedMatchingConfig {
    b.call()
    b.matching.value = config
    return config
  }
}

internal class FakeSettingsRepository(private val b: FakeBackend) : SettingsRepository {
  override suspend fun getScannerSettings(): ScannerSettings {
    b.call()
    return b.scanner.value
  }

  override suspend fun updateScannerSettings(settings: ScannerSettings): ScannerSettings {
    b.call()
    b.scanner.value = settings
    return settings
  }

  override suspend fun getServerSettings(): ServerSettings {
    b.call()
    return b.server.value
  }

  override suspend fun updateServerSettings(settings: ServerSettings): ServerSettings {
    b.call()
    b.server.value = settings
    return settings
  }

  override suspend fun getDetailLayout(): DetailLayout {
    b.call()
    return b.detailLayout.value
  }

  override suspend fun updateDetailLayout(layout: DetailLayout): DetailLayout {
    b.call()
    b.detailLayout.value = layout.normalized()
    return b.detailLayout.value
  }
}

internal class FakeScanRepository(private val b: FakeBackend) : ScanRepository {
  override val state: StateFlow<ScanState> = b.scanState
  override val events: Flow<ServerEvent> = b.events

  override suspend fun startScanAll(): Boolean {
    b.call()
    if (b.scanState.value.scanning) return false
    b.scanState.value = b.scanState.value.copy(scanning = true, startedAt = b.now())
    return true
  }

  override suspend fun scanLink(linkId: Long): List<Long> {
    b.call()
    return emptyList()
  }

  override suspend fun scanJob(jobId: Long): Job {
    b.call()
    return b.jobs.value.first { it.id == jobId }
  }

  override suspend fun createLink(
    title: String,
    url: String,
    force: Boolean,
    siteName: String?,
  ): CreateLinkResponse {
    b.call()
    if (b.createLinkConflict && !force) {
      throw ApiFailure(ApiErrorCode.CONFLICT, "No jobs were found on this page.")
    }
    val site = b.sites.firstOrNull { s -> s.urls.any { url.startsWith(it) } } ?: b.sites.last()
    val link =
      Link(
        id = (b.links.value.maxOfOrNull { it.id } ?: 0) + 1,
        url = url,
        title = title,
        siteId = site.id,
        createdAt = b.now(),
        lastScrapedAt = b.now(),
      )
    b.links.value = b.links.value + link
    return CreateLinkResponse(link, emptyList(), parseFailed = false)
  }

  override suspend fun brokenLinks(): List<Link> {
    b.call()
    return b.links.value.filter { it.scrapeFailureCount >= ServerEvent.BROKEN_LINK_THRESHOLD }
  }
}
