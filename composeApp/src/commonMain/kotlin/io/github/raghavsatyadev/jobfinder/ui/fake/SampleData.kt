package io.github.raghavsatyadev.jobfinder.ui.fake

import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsScores
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.CategoryRating
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyRating
import io.github.raghavsatyadev.jobfinder.shared.model.InsightSource
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordCategory
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.SalaryRange
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode

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
