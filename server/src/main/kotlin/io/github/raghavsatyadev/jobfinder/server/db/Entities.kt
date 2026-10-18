package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Fts5
import androidx.room3.FtsOptions
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings

// Room entities. Timestamps are epoch milliseconds (UTC). List columns are JSON arrays in TEXT
// (see JsonColumnConverters). Ids are AUTOINCREMENT, so a deleted id is never reused, like the old
// Postgres identity columns.

/** A job board. Seeded from `db/sites_rows.csv` when the database is created. */
@Entity(tableName = "sites")
data class SiteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  /** `SiteProvider.wire`. */
  val provider: String,
  val urls: List<String>,
  @ColumnInfo(name = "query_params_to_remove") val queryParamsToRemove: List<String> = emptyList(),
  @ColumnInfo(name = "blacklisted_paths") val blacklistedPaths: List<String> = emptyList(),
  /** See `Site.logoKey`. */
  @ColumnInfo(name = "logo_key") val logoKey: String,
  val deprecated: Boolean = false,
  @ColumnInfo(name = "incognito_support") val incognitoSupport: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** A saved job search. */
@Entity(
  tableName = "links",
  foreignKeys =
    [
      ForeignKey(
        entity = SiteEntity::class,
        parentColumns = ["id"],
        childColumns = ["site_id"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.RESTRICT,
      )
    ],
  indices = [Index("site_id")],
)
data class LinkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val title: String,
  @ColumnInfo(name = "site_id") val siteId: Long,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "scrape_failure_count") val scrapeFailureCount: Int = 0,
  @ColumnInfo(name = "last_scraped_at") val lastScrapedAt: Long,
)

/** A job. Its title and company are indexed in [JobFtsEntity]. */
@Entity(
  tableName = "jobs",
  foreignKeys =
    [
      ForeignKey(
        entity = SiteEntity::class,
        parentColumns = ["id"],
        childColumns = ["site_id"],
        onDelete = ForeignKey.RESTRICT,
        onUpdate = ForeignKey.RESTRICT,
      ),
      ForeignKey(
        entity = LinkEntity::class,
        parentColumns = ["id"],
        childColumns = ["link_id"],
        onDelete = ForeignKey.SET_NULL,
        onUpdate = ForeignKey.CASCADE,
      ),
    ],
  indices =
    [
      Index(value = ["external_id"], unique = true),
      // Serves listJobs: WHERE status = ? ORDER BY updated_at DESC, id DESC, with the cursor.
      Index(
        value = ["status", "updated_at", "id"],
        orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC],
        name = "index_jobs_status_updated_at_id",
      ),
      Index("site_id"),
      Index("link_id"),
    ],
)
data class JobEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(name = "external_id") val externalId: String,
  @ColumnInfo(name = "external_url") val externalUrl: String,
  @ColumnInfo(name = "site_id") val siteId: Long,
  @ColumnInfo(name = "link_id") val linkId: Long? = null,
  val title: String,
  @ColumnInfo(name = "company_name") val companyName: String,
  @ColumnInfo(name = "company_logo") val companyLogo: String? = null,
  val location: String? = null,
  val salary: String? = null,
  /** The old `jobType` (`WorkMode.wire`); kept as text, filtered with `lower()`. */
  @ColumnInfo(name = "job_type") val jobType: String? = null,
  val tags: List<String> = emptyList(),
  val description: String? = null,
  /** `JobStatus.wire`. */
  val status: String,
  /** `JobLabel.wire` values; unknown strings are kept. */
  val labels: List<String> = emptyList(),
  @ColumnInfo(name = "exclude_reason") val excludeReason: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
  /**
   * The AI filter a job in `new` passed ((`AdvancedMatchingFilter.key`)), [MATCHED_BY_USER] when
   * the user put it there, `null` when it was never checked (the check failed). Jobs whose value is
   * not the current filter are checked again in the background.
   */
  @ColumnInfo(name = "matched_with") val matchedWith: String? = null,
) {
  companion object {
    const val MATCHED_BY_USER = "user"
  }
}

/**
 * Full-text index over job title and company (replaces the Postgres `job_search_vector`). External
 * content table: Room creates triggers on `jobs` that keep it in sync.
 */
@Fts5(
  contentEntity = JobEntity::class,
  tokenizer = FtsOptions.TOKENIZER_PORTER,
  tokenizerArgs = [FtsOptions.TOKENIZER_UNICODE61],
)
@Entity(tableName = "jobs_fts")
data class JobFtsEntity(
  @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
  val title: String,
  @ColumnInfo(name = "company_name") val companyName: String,
)

/** A note on a job; deleted with the job. */
@Entity(
  tableName = "notes",
  foreignKeys =
    [
      ForeignKey(
        entity = JobEntity::class,
        parentColumns = ["id"],
        childColumns = ["job_id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
      )
    ],
  indices = [Index("job_id")],
)
data class NoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  @ColumnInfo(name = "job_id") val jobId: Long,
  val text: String,
  /** Attachment paths under `%LOCALAPPDATA%\jobfinder-data\notes\`. */
  val files: List<String> = emptyList(),
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** The advanced-matching config. One row, [SINGLETON_ID]. */
@Entity(tableName = "advanced_matching")
data class AdvancedMatchingEntity(
  @PrimaryKey val id: Long = SINGLETON_ID,
  /** The old `chatgpt_prompt`, byte for byte. */
  val prompt: String = "",
  @ColumnInfo(name = "blacklisted_companies") val blacklistedCompanies: List<String> = emptyList(),
)

/** App settings (replaces `profiles` and the Electron `settings.json`). One row, [SINGLETON_ID]. */
@Entity(tableName = "settings")
data class SettingsEntity(
  @PrimaryKey val id: Long = SINGLETON_ID,
  /** `null` means "Never". */
  @ColumnInfo(name = "cron_rule") val cronRule: String?,
  @ColumnInfo(name = "prevent_sleep") val preventSleep: Boolean,
  @ColumnInfo(name = "use_sound") val useSound: Boolean,
  @ColumnInfo(name = "in_app_browser_enabled") val inAppBrowserEnabled: Boolean,
  @ColumnInfo(name = "server_port") val serverPort: Int,
  /** `null` picks the default-route interface. */
  @ColumnInfo(name = "network_interface") val networkInterface: String?,
  /** Bearer token that paired clients send; 256 random bits, base64url. */
  @ColumnInfo(name = "pairing_token") val pairingToken: String,
  @ColumnInfo(name = "scraper_concurrency") val scraperConcurrency: Int,
  /** `AiProvider`'s serial name. The API key is never here: it stays in the protected store. */
  @ColumnInfo(name = "ai_provider", defaultValue = "ollama") val aiProvider: String = "ollama",
  @ColumnInfo(name = "ai_model", defaultValue = "jobfinder-qwen:latest")
  val aiModel: String = "jobfinder-qwen:latest",
  /** `null` uses the provider's own address. */
  @ColumnInfo(name = "ai_base_url") val aiBaseUrl: String? = null,
  /** `DetailLayout` as JSON; `null` is the app's order. */
  @ColumnInfo(name = "detail_layout") val detailLayout: String? = null,
  /**
   * `AiRouting` as JSON; `null` uses the defaults (online keys for public text, local for resume).
   */
  @ColumnInfo(name = "ai_routing") val aiRouting: String? = null,
  /** Days filtered-out jobs are kept; `null` keeps them forever. */
  @ColumnInfo(name = "excluded_keep_days", defaultValue = "30")
  val excludedKeepDays: Int? = ScannerSettings.DEFAULT_EXCLUDED_KEEP_DAYS,
  @ColumnInfo(name = "ai_paused", defaultValue = "0") val aiPaused: Boolean = false,
)

/** Debug copies of pages that failed to parse. */
@Entity(tableName = "html_dumps")
data class HtmlDumpEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val html: String,
  /** JSON, for example the LinkedIn rehydration data. */
  @ColumnInfo(name = "webpage_runtime_data") val webpageRuntimeData: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Local LLM token usage per day. Token counts only, no cost. */
@Entity(tableName = "ai_usage_daily")
data class AiUsageDailyEntity(
  /** ISO date, `yyyy-MM-dd`. */
  @PrimaryKey @ColumnInfo(name = "usage_date") val usageDate: String,
  @ColumnInfo(name = "input_tokens") val inputTokens: Long = 0,
  @ColumnInfo(name = "output_tokens") val outputTokens: Long = 0,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** The resume's text, for ATS reviews. One row, [SINGLETON_ID]; the file itself is not kept. */
@Entity(tableName = "resume")
data class ResumeEntity(
  @PrimaryKey val id: Long = SINGLETON_ID,
  @ColumnInfo(name = "file_name") val fileName: String,
  val text: String,
  val pages: Int? = null,
  val words: Int,
  /** `ResumeFlag` serial names. */
  val flags: List<String> = emptyList(),
  /** SHA-256 of [text]; a review counts only for the resume it was made with. */
  val hash: String,
  @ColumnInfo(name = "uploaded_at") val uploadedAt: Long,
)

/** One job's ATS review against the resume with [resumeHash]; deleted with the job. */
@Entity(
  tableName = "ats_reviews",
  foreignKeys =
    [
      ForeignKey(
        entity = JobEntity::class,
        parentColumns = ["id"],
        childColumns = ["job_id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE,
      )
    ],
)
data class AtsReviewEntity(
  @PrimaryKey @ColumnInfo(name = "job_id") val jobId: Long,
  @ColumnInfo(name = "resume_hash") val resumeHash: String,
  val overall: Double = 0.0,
  @ColumnInfo(name = "ats_match") val atsMatch: Int = 0,
  val shortlisted: Boolean = false,
  /** The model gave no usable answer; the job is not tried again until asked. */
  val failed: Boolean = false,
  /** `AtsReview` as JSON; `null` when [failed]. */
  val review: String? = null,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * A company's ratings, read from public pages and kept for days. [companyKey] is the name in
 * lowercase letters and digits only, so "Capital One" and "CapitalOne" share a row.
 */
@Entity(tableName = "company_ratings")
data class CompanyRatingsEntity(
  @PrimaryKey @ColumnInfo(name = "company_key") val companyKey: String,
  /** `List<CompanyRating>` as JSON. */
  val ratings: String,
  /** The company's AmbitionBox page name (`swiggy` in `/overview/swiggy-overview`), when found. */
  @ColumnInfo(name = "ambitionbox_slug") val ambitionBoxSlug: String? = null,
  /** The sites could not be read; tried again sooner than a good row. */
  val failed: Boolean = false,
  @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** The salary for one job title at one company; [salary] is `null` when no near role had one. */
@Entity(tableName = "company_salaries", primaryKeys = ["company_key", "title_key"])
data class CompanySalaryEntity(
  @ColumnInfo(name = "company_key") val companyKey: String,
  @ColumnInfo(name = "title_key") val titleKey: String,
  /** `SalaryRange` as JSON. */
  val salary: String? = null,
  @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** A job's careers-site check; the signals from the job itself are worked out on each read. */
@Entity(tableName = "ghost_checks", indices = [Index("company_key")])
data class GhostCheckEntity(
  @PrimaryKey @ColumnInfo(name = "job_id") val jobId: Long,
  @ColumnInfo(name = "company_key") val companyKey: String,
  /** `CareersResult` serial name, for counting a company's results. */
  val result: String,
  /** `CareersCheck` as JSON. */
  val careers: String,
  @ColumnInfo(name = "checked_at") val checkedAt: Long,
)

/**
 * A company's careers site and its open jobs, read once for all its postings. [host] and [slug] are
 * `null` when none was found.
 */
@Entity(tableName = "careers_boards")
data class CareersBoardEntity(
  @PrimaryKey @ColumnInfo(name = "company_key") val companyKey: String,
  val host: String? = null,
  val slug: String? = null,
  /** `List<CareersJob>` as JSON. */
  val jobs: String = "[]",
  @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** A job's negotiation tips, written when asked; [failed] when the model gave no usable answer. */
@Entity(tableName = "negotiation_tips")
data class NegotiationTipsEntity(
  @PrimaryKey @ColumnInfo(name = "job_id") val jobId: Long,
  /** `NegotiationTips` as JSON; `null` when [failed]. */
  val tips: String? = null,
  val failed: Boolean = false,
  @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Primary key of the one-row tables. */
const val SINGLETON_ID = 1L

/** An AI prompt the user edited; a prompt without a row uses the app's own text. */
@Entity(tableName = "ai_prompts")
data class AiPromptEntity(
  /** `PromptId`'s serial name. */
  @PrimaryKey val id: String,
  val text: String,
  @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
