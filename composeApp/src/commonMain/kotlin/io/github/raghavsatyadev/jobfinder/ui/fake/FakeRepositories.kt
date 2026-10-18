package io.github.raghavsatyadev.jobfinder.ui.fake

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsScores
import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.CategoryRating
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyRating
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.InsightSource
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobCounts
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobPage
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordCategory
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.SalaryRange
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
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
private class FakeInsightsRepository(private val b: FakeBackend) : InsightsRepository {
  override suspend fun requestInsights(jobId: Long, retry: Boolean): InsightsStatus {
    b.call()
    val insights = b.insights.value[jobId] ?: return InsightsStatus(InsightsState.QUEUED)
    return InsightsStatus(InsightsState.READY, insights)
  }
}

/** Uploads keep only the name; reviews come from [FakeBackend.reviews], other jobs stay queued. */
private class FakeAtsRepository(private val b: FakeBackend) : AtsRepository {
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

private class FakeJobsRepository(private val b: FakeBackend) : JobsRepository {
  override suspend fun listJobs(
    status: JobStatus,
    filter: JobFilter,
    limit: Int,
    after: String?,
  ): JobPage {
    b.call()
    val all =
      b.jobs.value
        .filter { it.status == status && filter.matches(it) }
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
      (applyMethods.isEmpty() || job.applyMethod in applyMethods)
  }
}

private class FakeLinksRepository(private val b: FakeBackend) : LinksRepository {
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

private class FakeSitesRepository(private val b: FakeBackend) : SitesRepository {
  override suspend fun listSites(): List<Site> {
    b.call()
    return b.sites
  }
}

private class FakeNotesRepository(private val b: FakeBackend) : NotesRepository {
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

private class FakeMatchingRepository(private val b: FakeBackend) : MatchingRepository {
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

private class FakeSettingsRepository(private val b: FakeBackend) : SettingsRepository {
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

private class FakeScanRepository(private val b: FakeBackend) : ScanRepository {
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

/** Sample jobs, searches and boards. Fixed timestamps, so screenshots never change. */
object SampleData {
  /** 2026-10-14 09:00 UTC. */
  const val NOW = 1_791_968_400_000L
  private const val HOUR = 3_600_000L

  val sites: List<Site> =
    listOf(
      site(1, "LinkedIn", "linkedin", "https://www.linkedin.com"),
      site(2, "Indeed", "indeed", "https://www.indeed.com"),
      site(3, "Remote OK", "remoteok", "https://remoteok.com"),
      site(4, "We Work Remotely", "weworkremotely", "https://weworkremotely.com"),
      site(5, "Dice", "dice", "https://www.dice.com"),
      site(6, "Custom", "custom", "https://"),
    )

  val links: List<Link> =
    listOf(
      Link(
        1,
        "https://www.linkedin.com/jobs/search/?keywords=android",
        "Android, remote",
        1,
        NOW - 90 * 24 * HOUR,
        0,
        NOW - HOUR,
      ),
      Link(
        2,
        "https://www.indeed.com/jobs?q=kotlin",
        "Kotlin on Indeed",
        2,
        NOW - 40 * 24 * HOUR,
        0,
        NOW - HOUR,
      ),
      Link(
        3,
        "https://remoteok.com/remote-kotlin-jobs",
        "Remote OK Kotlin",
        3,
        NOW - 10 * 24 * HOUR,
        4,
        NOW - 30 * 24 * HOUR,
      ),
    )

  val jobs: List<Job> =
    listOf(
      job(
        1,
        "Senior Android Engineer",
        "Northwind",
        1,
        1,
        JobStatus.NEW,
        "Remote, India",
        "₹40L – ₹55L",
        WorkMode.REMOTE,
        listOf("Easy Apply", "Kotlin"),
        ats = AtsSummary(overall = 7.6, atsMatch = 82, shortlisted = true),
      ),
      job(
        2,
        "Kotlin Multiplatform Developer",
        "Contoso",
        1,
        1,
        JobStatus.NEW,
        "Bengaluru",
        null,
        WorkMode.HYBRID,
        listOf("Compose"),
        ats = AtsSummary(overall = 5.4, atsMatch = 58, shortlisted = false),
      ),
      job(
        3,
        "Mobile Platform Lead",
        "Fabrikam",
        2,
        2,
        JobStatus.NEW,
        "Pune",
        "$120k",
        WorkMode.ONSITE,
        emptyList(),
      ),
      job(
        4,
        "Staff Engineer, Apps",
        "Tailspin Toys",
        3,
        3,
        JobStatus.APPLIED,
        "Remote",
        null,
        WorkMode.REMOTE,
        emptyList(),
        listOf(JobLabel.INTERVIEWING),
      ),
      job(
        5,
        "Android Developer",
        "Litware",
        1,
        1,
        JobStatus.ARCHIVED,
        "Hyderabad",
        null,
        null,
        emptyList(),
      ),
      job(
        6,
        "Crypto Wallet Engineer",
        "Coinly",
        4,
        null,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
        "Remote",
        null,
        WorkMode.REMOTE,
        emptyList(),
        excludeReason = "The prompt excludes crypto companies.",
      ),
    )

  val resume =
    ResumeInfo(
      fileName = "Resume_Android.pdf",
      uploadedAt = NOW - 3 * 24 * HOUR,
      pages = 2,
      words = 812,
      flags = listOf(ResumeFlag.TABLES),
    )

  /** Job 1 against [resume]. */
  val review =
    AtsReview(
      jobId = 1,
      summary = AtsSummary(overall = 7.6, atsMatch = 82, shortlisted = true),
      scores =
        AtsScores(
          keywords = 8.2,
          experience = 8.0,
          achievements = 7.0,
          formatting = 8.5,
          education = 10.0,
          trajectory = 8.0,
        ),
      keywords =
        listOf(
          AtsKeyword("Kotlin", KeywordCategory.HARD_SKILL, KeywordMatch.EXACT, count = 6),
          AtsKeyword("Jetpack Compose", KeywordCategory.HARD_SKILL, KeywordMatch.EXACT, count = 3),
          AtsKeyword(
            "Kotlin Multiplatform",
            KeywordCategory.HARD_SKILL,
            KeywordMatch.PARTIAL,
            matchedAs = "KMP",
            count = 1,
          ),
          AtsKeyword("GraphQL", KeywordCategory.HARD_SKILL, KeywordMatch.MISSING),
          AtsKeyword("Mentoring", KeywordCategory.SOFT_SKILL, KeywordMatch.EXACT, count = 1),
          AtsKeyword("Stakeholders", KeywordCategory.SOFT_SKILL, KeywordMatch.MISSING),
          AtsKeyword("6+ years", KeywordCategory.EXPERIENCE, KeywordMatch.EXACT, count = 1),
          AtsKeyword(
            "Senior Android Engineer",
            KeywordCategory.ROLE,
            KeywordMatch.EXACT,
            count = 1,
          ),
        ),
      assessment =
        "A strong Android match: the current title and stack line up with the role. GraphQL is " +
          "missing and two bullets list duties rather than results.",
      fixes =
        listOf(
          "Write \"Kotlin Multiplatform\" out in full in the Summary, not only \"KMP\".",
          "Add the GraphQL client work to the latest role, if you did it.",
        ),
      formatting = listOf(ResumeFlag.TABLES),
      createdAt = NOW - HOUR,
    )

  val notes: List<Note> =
    listOf(
      Note(
        1,
        4,
        "Recruiter call on Thursday. Ask about the on-call rota.",
        emptyList(),
        NOW - 2 * HOUR,
      )
    )

  val matching =
    AdvancedMatchingConfig(
      prompt =
        "Skip jobs at crypto companies and jobs that need more than 2 days a week in an office.",
      blacklistedCompanies = listOf("Initech"),
    )

  private fun site(id: Long, name: String, key: String, url: String) =
    Site(id, name, SiteProvider.fromWire(key), listOf(url), logoKey = key, createdAt = 0)

  private fun job(
    id: Long,
    title: String,
    company: String,
    siteId: Long,
    linkId: Long?,
    status: JobStatus,
    location: String?,
    salary: String?,
    workMode: WorkMode?,
    tags: List<String>,
    labels: List<JobLabel> = emptyList(),
    excludeReason: String? = null,
    ats: AtsSummary? = null,
  ) =
    Job(
      id = id,
      externalId = "ext-$id",
      externalUrl = "https://example.com/jobs/$id",
      siteId = siteId,
      linkId = linkId,
      title = title,
      companyName = company,
      location = location,
      salary = salary,
      workMode = workMode,
      tags = tags,
      description =
        "## About the role\n\nYou will build the **$title** experience.\n\n- Kotlin and Compose\n- " +
          "Shipping to millions of users\n\n## Benefits\n\nRemote-first, learning budget.",
      status = status,
      labels = labels,
      excludeReason = excludeReason,
      ats = ats,
      createdAt = NOW - id * HOUR,
      updatedAt = NOW - id * HOUR,
    )

  /** Job 1's company. */
  val insights =
    CompanyInsights(
      company = "Northwind",
      ratings =
        listOf(
          CompanyRating(
            source = InsightSource.AMBITIONBOX,
            rating = 3.9,
            reviews = 1_284,
            categories =
              listOf(
                CategoryRating("Work-life balance", 3.6),
                CategoryRating("Salary and benefits", 3.8),
                CategoryRating("Career growth", 3.4),
                CategoryRating("Job security", 3.7),
              ),
            likes = listOf("Good learning and friendly seniors.", "Flexible hours."),
            dislikes = listOf("Slow appraisals."),
            url = "https://www.ambitionbox.com/reviews/northwind-reviews",
          ),
          CompanyRating(
            source = InsightSource.GLASSDOOR,
            rating = 4.1,
            reviews = 862,
            recommendPercent = 78,
            categories =
              listOf(CategoryRating("Work-life balance", 3.9), CategoryRating("Culture", 4.0)),
            url = "https://www.glassdoor.co.in/Overview/Working-at-Northwind-EI_IE1.htm",
          ),
        ),
      salary =
        SalaryRange(
          source = InsightSource.AMBITIONBOX,
          role = "Android Developer",
          exactRole = false,
          currency = "INR",
          low = 1_600_000,
          median = 1_850_000,
          high = 2_200_000,
          minExperience = 4,
          maxExperience = 8,
          samples = 21,
          url = "https://www.ambitionbox.com/salaries/northwind-salaries/android-developer",
        ),
      fetchedAt = NOW - 2 * HOUR,
    )
}
