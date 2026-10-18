package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RawQuery
import androidx.room3.RoomRawQuery
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert

@Dao
interface SitesDao {
  @Query("SELECT * FROM sites ORDER BY id") suspend fun listAll(): List<SiteEntity>

  @Query("SELECT * FROM sites WHERE id = :id") suspend fun get(id: Long): SiteEntity?

  @Insert suspend fun insertAll(sites: List<SiteEntity>)

  @Insert suspend fun insert(site: SiteEntity): Long

  @Query("UPDATE sites SET name = :name WHERE id = :id")
  suspend fun rename(id: Long, name: String): Int

  @Query("SELECT COUNT(*) FROM sites") suspend fun count(): Long

  @Query("DELETE FROM sites") suspend fun deleteAll()
}

@Dao
interface LinksDao {
  /** Newest first, like the old `listLinks`. */
  @Query("SELECT * FROM links ORDER BY id DESC") suspend fun listAll(): List<LinkEntity>

  @Query("SELECT * FROM links WHERE id = :id") suspend fun get(id: Long): LinkEntity?

  @Insert suspend fun insert(link: LinkEntity): Long

  @Insert suspend fun insertAll(links: List<LinkEntity>)

  @Update suspend fun update(link: LinkEntity): Int

  @Query("DELETE FROM links WHERE id = :id") suspend fun delete(id: Long): Int

  @Query("UPDATE links SET site_id = :siteId WHERE id = :id")
  suspend fun moveToSite(id: Long, siteId: Long): Int

  /** A scan of the link found no job list (old `handleParsingFailureForLink`). */
  @Query("UPDATE links SET scrape_failure_count = scrape_failure_count + 1 WHERE id = :id")
  suspend fun incrementScrapeFailures(id: Long): Int

  /** A scan of the link parsed its job list (old `handleParsingSuccessForLink`). */
  @Query("UPDATE links SET scrape_failure_count = 0, last_scraped_at = :now WHERE id = :id")
  suspend fun markScraped(id: Long, now: Long): Int

  /** Links whose last [threshold] or more scans failed in a row. */
  @Query("SELECT * FROM links WHERE scrape_failure_count >= :threshold ORDER BY id")
  suspend fun listFailing(threshold: Int): List<LinkEntity>

  @Query("SELECT COUNT(*) FROM links") suspend fun count(): Long

  @Query("DELETE FROM links") suspend fun deleteAll()
}

/** A job without its description, for lists. */
data class JobListRow(
  val id: Long,
  @ColumnInfo(name = "external_id") val externalId: String,
  @ColumnInfo(name = "external_url") val externalUrl: String,
  @ColumnInfo(name = "site_id") val siteId: Long,
  @ColumnInfo(name = "link_id") val linkId: Long?,
  val title: String,
  @ColumnInfo(name = "company_name") val companyName: String,
  @ColumnInfo(name = "company_logo") val companyLogo: String?,
  val location: String?,
  val salary: String?,
  @ColumnInfo(name = "job_type") val jobType: String?,
  val tags: List<String>,
  val status: String,
  val labels: List<String>,
  @ColumnInfo(name = "exclude_reason") val excludeReason: String?,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
  /** The ATS review against the current resume, when there is one. */
  @ColumnInfo(name = "ats_overall") val atsOverall: Double? = null,
  @ColumnInfo(name = "ats_match") val atsMatch: Int? = null,
  @ColumnInfo(name = "ats_shortlisted") val atsShortlisted: Boolean? = null,
)

/** One row of a count grouped by status. */
data class StatusCount(val status: String, @ColumnInfo(name = "job_count") val count: Long)

@Dao
interface JobsDao {
  /** Returns the new id, or -1 when a job with the same external id exists. */
  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(job: JobEntity): Long

  @Insert suspend fun insertAll(jobs: List<JobEntity>)

  @Query("SELECT * FROM jobs WHERE id = :id") suspend fun get(id: Long): JobEntity?

  /** The newest new job that has a description, for trying AI instructions on. */
  @Query(
    "SELECT * FROM jobs WHERE status = 'new' AND description IS NOT NULL AND description != '' " +
      "ORDER BY created_at DESC LIMIT 1"
  )
  suspend fun newestWithDescription(): JobEntity?

  /** Jobs whose URL matches the LIKE [pattern] (`\` escapes), newest first; for the browser. */
  @Query(
    "SELECT * FROM jobs WHERE external_url LIKE :pattern ESCAPE '\\' ORDER BY id DESC LIMIT :max"
  )
  suspend fun findByUrlLike(pattern: String, max: Int): List<JobEntity>

  @Update suspend fun update(job: JobEntity): Int

  @Query("DELETE FROM jobs WHERE id = :id") suspend fun delete(id: Long): Int

  /** Moves the jobs found by search [linkId] from board [fromSiteId] to [toSiteId]. */
  @Query("UPDATE jobs SET site_id = :toSiteId WHERE link_id = :linkId AND site_id = :fromSiteId")
  suspend fun moveToSite(linkId: Long, fromSiteId: Long, toSiteId: Long): Int

  /** Deletes jobs the AI filtered out that have not changed since [before]. */
  @Query("DELETE FROM jobs WHERE status = 'excluded_by_advanced_matching' AND updated_at < :before")
  suspend fun deleteExcludedBefore(before: Long): Int

  /**
   * Jobs the user has not touched (new, processing or filtered out, no label) whose stored
   * description matches the LIKE [pattern]; for removing postings that turned out to be gone.
   */
  @Query(
    "SELECT id FROM jobs WHERE status IN ('new', 'processing', 'excluded_by_advanced_matching') " +
      "AND labels = '[]' AND description LIKE :pattern"
  )
  suspend fun untouchedWithDescriptionLike(pattern: String): List<Long>

  @Query("UPDATE jobs SET status = :status, updated_at = :updatedAt WHERE id = :id")
  suspend fun updateStatus(id: Long, status: String, updatedAt: Long): Int

  /** [labelsJson] is a JSON array (see [JsonColumnConverters.encodeStringList]). */
  @Query("UPDATE jobs SET labels = :labelsJson WHERE id = :id")
  suspend fun updateLabels(id: Long, labelsJson: String): Int

  /**
   * Stores what the description scan found. Only a job still in `processing` or `new` is changed,
   * so a job the user deleted or archived meanwhile stays put (old `scan-job-description`).
   * [tagsJson] is a JSON array.
   */
  @Query(
    """UPDATE jobs SET description = :description, salary = :salary, tags = :tagsJson,
       status = :status, exclude_reason = :excludeReason, updated_at = :updatedAt
       WHERE id = :id AND status IN ('processing', 'new')"""
  )
  suspend fun updateAfterDescriptionScan(
    id: Long,
    description: String?,
    salary: String?,
    tagsJson: String,
    status: String,
    excludeReason: String?,
    updatedAt: Long,
  ): Int

  /** Jobs waiting for their description scan, oldest first. */
  @Query("SELECT * FROM jobs WHERE status = 'processing' ORDER BY id")
  suspend fun listProcessing(): List<JobEntity>

  @Query("UPDATE jobs SET status = :to, updated_at = :updatedAt WHERE status = :from")
  suspend fun changeAllStatuses(from: String, to: String, updatedAt: Long): Int

  /** Built by [JobQueries.list]. */
  @RawQuery suspend fun listRows(query: RoomRawQuery): List<JobListRow>

  /** Built by [JobQueries.count]. */
  @RawQuery suspend fun countRows(query: RoomRawQuery): List<StatusCount>

  @Query("SELECT COUNT(*) FROM jobs") suspend fun count(): Long

  /** Every job with its description, for a full backup. */
  @Query("SELECT * FROM jobs ORDER BY id") suspend fun listAll(): List<JobEntity>

  /** The latest jobs in [status] that have a description, newest first (accuracy samples). */
  @Query(
    "SELECT * FROM jobs WHERE status = :status AND description IS NOT NULL AND description != '' " +
      "ORDER BY updated_at DESC, id DESC LIMIT :max"
  )
  suspend fun listRecentWithDescription(status: String, max: Int): List<JobEntity>

  @Query("DELETE FROM jobs") suspend fun deleteAll()

  /**
   * Every job worth company insights, in the order to read them: jobs found since [recentSince]
   * (newest first), then the best ATS scores, then the rest (newest first). Deleted jobs and jobs
   * still being processed are left out.
   */
  @Query(
    "SELECT j.id AS id, j.title AS title, j.company_name AS companyName FROM jobs j " +
      "LEFT JOIN ats_reviews a ON a.job_id = j.id AND a.failed = 0 " +
      "WHERE j.status NOT IN ('deleted', 'processing') " +
      "ORDER BY CASE WHEN j.created_at >= :recentSince THEN 0 ELSE 1 END, " +
      "CASE WHEN j.created_at >= :recentSince THEN -j.created_at ELSE 0 END, " +
      "CASE WHEN a.job_id IS NULL THEN 1 ELSE 0 END, a.overall DESC, a.ats_match DESC, " +
      "j.created_at DESC, j.id DESC"
  )
  suspend fun listForInsights(recentSince: Long): List<InsightCandidate>

  /** Jobs from [companyName] (any case), for spotting reposts of the same title. */
  @Query(
    "SELECT id, title, created_at AS createdAt FROM jobs WHERE lower(company_name) = lower(:companyName)"
  )
  suspend fun listByCompany(companyName: String): List<CompanyJob>
}

data class CompanyJob(val id: Long, val title: String, val createdAt: Long)

@Dao
interface NotesDao {
  /** Newest first, like the old `listNotes`. */
  @Query("SELECT * FROM notes WHERE job_id = :jobId ORDER BY created_at DESC, id DESC")
  suspend fun listForJob(jobId: Long): List<NoteEntity>

  @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: Long): NoteEntity?

  @Insert suspend fun insert(note: NoteEntity): Long

  @Insert suspend fun insertAll(notes: List<NoteEntity>)

  @Update suspend fun update(note: NoteEntity): Int

  @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long): Int

  @Query("SELECT COUNT(*) FROM notes") suspend fun count(): Long

  @Query("SELECT * FROM notes ORDER BY id") suspend fun listAll(): List<NoteEntity>

  @Query("DELETE FROM notes") suspend fun deleteAll()
}

@Dao
interface AdvancedMatchingDao {
  @Query("SELECT * FROM advanced_matching WHERE id = $SINGLETON_ID")
  suspend fun get(): AdvancedMatchingEntity?

  @Upsert suspend fun upsert(config: AdvancedMatchingEntity)

  @Query("SELECT COUNT(*) FROM advanced_matching") suspend fun count(): Long

  @Query("DELETE FROM advanced_matching") suspend fun deleteAll()
}

@Dao
interface SettingsDao {
  @Query("SELECT * FROM settings WHERE id = $SINGLETON_ID") suspend fun get(): SettingsEntity?

  @Upsert suspend fun upsert(settings: SettingsEntity)

  @Query("DELETE FROM settings") suspend fun deleteAll()
}

@Dao
interface HtmlDumpsDao {
  @Insert suspend fun insert(dump: HtmlDumpEntity): Long

  @Query("SELECT COUNT(*) FROM html_dumps") suspend fun count(): Long

  @Query("DELETE FROM html_dumps") suspend fun deleteAll()
}

@Dao
interface AiUsageDao {
  @Query("SELECT * FROM ai_usage_daily WHERE usage_date = :date")
  suspend fun get(date: String): AiUsageDailyEntity?

  @Query("SELECT * FROM ai_usage_daily ORDER BY usage_date")
  suspend fun listAll(): List<AiUsageDailyEntity>

  @Upsert suspend fun upsert(usage: AiUsageDailyEntity)

  @Insert suspend fun insertAll(usage: List<AiUsageDailyEntity>)

  /** Adds tokens to the day's row (the old `log_ai_usage`). */
  @Transaction
  suspend fun add(date: String, inputTokens: Long, outputTokens: Long, now: Long) {
    val current = get(date)
    upsert(
      AiUsageDailyEntity(
        usageDate = date,
        inputTokens = (current?.inputTokens ?: 0) + inputTokens,
        outputTokens = (current?.outputTokens ?: 0) + outputTokens,
        updatedAt = now,
      )
    )
  }

  @Query("SELECT COUNT(*) FROM ai_usage_daily") suspend fun count(): Long

  @Query("DELETE FROM ai_usage_daily") suspend fun deleteAll()
}

@Dao
interface ResumeDao {
  @Query("SELECT * FROM resume WHERE id = $SINGLETON_ID") suspend fun get(): ResumeEntity?

  @Upsert suspend fun upsert(resume: ResumeEntity)

  @Query("DELETE FROM resume") suspend fun delete()
}

@Dao
interface AtsReviewsDao {
  @Query("SELECT * FROM ats_reviews WHERE job_id = :jobId AND resume_hash = :hash")
  suspend fun get(jobId: Long, hash: String): AtsReviewEntity?

  @Upsert suspend fun upsert(review: AtsReviewEntity)

  @Query("DELETE FROM ats_reviews WHERE job_id = :jobId") suspend fun delete(jobId: Long)

  @Query("DELETE FROM ats_reviews") suspend fun deleteAll()

  @Query("SELECT * FROM ats_reviews ORDER BY job_id") suspend fun listAll(): List<AtsReviewEntity>

  /** The latest usable reviews made for the resume with [hash] (accuracy samples). */
  @Query(
    "SELECT * FROM ats_reviews WHERE resume_hash = :hash AND failed = 0 " +
      "ORDER BY created_at DESC LIMIT :max"
  )
  suspend fun listRecent(hash: String, max: Int): List<AtsReviewEntity>

  /** Ignored when the job already has a review. */
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertAll(reviews: List<AtsReviewEntity>)

  /**
   * The newest job in [status] with a description and no review for [hash]: what the background
   * scorer does next.
   */
  @Query(
    """SELECT j.id FROM jobs j
       WHERE j.status = :status AND j.description IS NOT NULL AND trim(j.description) != ''
       AND NOT EXISTS (SELECT 1 FROM ats_reviews a WHERE a.job_id = j.id AND a.resume_hash = :hash)
       ORDER BY j.created_at DESC, j.id DESC LIMIT 1"""
  )
  suspend fun nextUnscored(status: String, hash: String): Long?
}

@Dao
interface InsightsDao {
  @Query("SELECT * FROM company_ratings WHERE company_key = :companyKey")
  suspend fun ratings(companyKey: String): CompanyRatingsEntity?

  @Upsert suspend fun upsertRatings(ratings: CompanyRatingsEntity)

  @Query("SELECT * FROM company_salaries WHERE company_key = :companyKey AND title_key = :titleKey")
  suspend fun salary(companyKey: String, titleKey: String): CompanySalaryEntity?

  @Upsert suspend fun upsertSalary(salary: CompanySalaryEntity)

  @Query("SELECT * FROM company_ratings ORDER BY company_key")
  suspend fun allRatings(): List<CompanyRatingsEntity>

  @Query("SELECT * FROM company_salaries ORDER BY company_key, title_key")
  suspend fun allSalaries(): List<CompanySalaryEntity>

  @Query("DELETE FROM company_ratings") suspend fun deleteRatings()

  @Query("DELETE FROM company_salaries") suspend fun deleteSalaries()
}

@Dao
interface GhostDao {
  @Query("SELECT * FROM ghost_checks WHERE job_id = :jobId")
  suspend fun check(jobId: Long): GhostCheckEntity?

  @Upsert suspend fun upsertCheck(check: GhostCheckEntity)

  /** How many of [companyKey]'s checked jobs have each careers result. */
  @Query(
    "SELECT result, COUNT(*) AS jobs FROM ghost_checks WHERE company_key = :companyKey GROUP BY result"
  )
  suspend fun companyResults(companyKey: String): List<CareersResultCount>

  @Query("SELECT * FROM careers_boards WHERE company_key = :companyKey")
  suspend fun board(companyKey: String): CareersBoardEntity?

  @Upsert suspend fun upsertBoard(board: CareersBoardEntity)
}

@Dao
interface TipsDao {
  @Query("SELECT * FROM negotiation_tips WHERE job_id = :jobId")
  suspend fun get(jobId: Long): NegotiationTipsEntity?

  @Upsert suspend fun upsert(tips: NegotiationTipsEntity)
}

data class CareersResultCount(val result: String, val jobs: Int)

@Dao
interface AiPromptsDao {
  @Query("SELECT * FROM ai_prompts ORDER BY id") suspend fun all(): List<AiPromptEntity>

  @Query("SELECT * FROM ai_prompts WHERE id = :id") suspend fun get(id: String): AiPromptEntity?

  @Upsert suspend fun upsert(prompt: AiPromptEntity)

  @Query("DELETE FROM ai_prompts WHERE id = :id") suspend fun delete(id: String)

  @Query("DELETE FROM ai_prompts") suspend fun deleteAll()
}

/** A job [JobsDao.listForInsights] offers: just what the insights read needs. */
data class InsightCandidate(val id: Long, val title: String, val companyName: String)
