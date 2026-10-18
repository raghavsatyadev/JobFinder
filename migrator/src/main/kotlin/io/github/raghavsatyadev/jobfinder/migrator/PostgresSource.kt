package io.github.raghavsatyadev.jobfinder.migrator

import io.github.raghavsatyadev.jobfinder.server.db.AdvancedMatchingEntity
import io.github.raghavsatyadev.jobfinder.server.db.AiUsageDailyEntity
import io.github.raghavsatyadev.jobfinder.server.db.JobEntity
import io.github.raghavsatyadev.jobfinder.server.db.LinkEntity
import io.github.raghavsatyadev.jobfinder.server.db.NoteEntity
import io.github.raghavsatyadev.jobfinder.server.db.SiteEntity
import io.github.raghavsatyadev.jobfinder.server.db.SiteLogos
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.Properties

/** Everything the migrator imports, read from the old Supabase Postgres. */
class PostgresSnapshot(
  val sites: List<SiteEntity>,
  val links: List<LinkEntity>,
  val jobs: List<JobEntity>,
  val notes: List<NoteEntity>,
  val advancedMatching: List<AdvancedMatchingEntity>,
  val aiUsage: List<AiUsageDailyEntity>,
  /** Jobs whose old status was `downloaded` (imported as `archived`). */
  val downloadedJobs: Int,
  val userCount: Int,
)

/** Reads the old Supabase Postgres database (one user; `user_id` is dropped). */
class PostgresSource(url: String) : AutoCloseable {
  private val connection: Connection = connect(url)

  fun read(): PostgresSnapshot {
    val jobsRaw = jobs()
    return PostgresSnapshot(
      sites = sites(),
      links = links(),
      jobs = jobsRaw.map { it.first },
      notes = notes(),
      advancedMatching = advancedMatching(),
      aiUsage = aiUsage(),
      downloadedJobs = jobsRaw.count { it.second == JobStatus.LEGACY_DOWNLOADED },
      userCount = scalar("SELECT COUNT(*) FROM auth.users").toInt(),
    )
  }

  fun scalar(sql: String): Long =
    connection.createStatement().use { st ->
      st.executeQuery(sql).use { rs ->
        rs.next()
        rs.getLong(1)
      }
    }

  /** Row count per raw status, `downloaded` included. */
  fun statusCounts(): Map<String, Long> =
    query("SELECT status::text AS status, COUNT(*) AS n FROM jobs GROUP BY status") {
        it.getString("status") to it.getLong("n")
      }
      .toMap()

  fun ids(table: String): List<Long> = query("SELECT id FROM $table ORDER BY id") { it.getLong(1) }

  /** SHA-256 of `advanced_matching.chatgpt_prompt`, computed by Postgres over its UTF-8 bytes. */
  fun promptSha256(): List<String> =
    query(
      "SELECT encode(sha256(convert_to(chatgpt_prompt, 'UTF8')), 'hex') FROM advanced_matching " +
        "ORDER BY id"
    ) {
      it.getString(1)
    }

  private fun sites(): List<SiteEntity> =
    query("SELECT * FROM sites ORDER BY id") {
      SiteEntity(
        id = it.getLong("id"),
        name = it.getString("name"),
        provider = it.getString("provider"),
        urls = it.textArray("urls"),
        queryParamsToRemove = it.textArray("queryParamsToRemove"),
        blacklistedPaths = it.textArray("blacklisted_paths"),
        logoKey = SiteLogos.keyFromLogoUrl(it.getString("logo_url")),
        deprecated = it.getBoolean("deprecated"),
        incognitoSupport = it.getBoolean("incognito_support"),
        createdAt = it.epochMs("created_at"),
      )
    }

  private fun links(): List<LinkEntity> =
    query("SELECT * FROM links ORDER BY id") {
      LinkEntity(
        id = it.getLong("id"),
        url = it.getString("url"),
        title = it.getString("title"),
        siteId = it.getLong("site_id"),
        createdAt = it.epochMs("created_at"),
        scrapeFailureCount = it.getInt("scrape_failure_count"),
        lastScrapedAt = it.epochMs("last_scraped_at"),
      )
    }

  /** Each job with its raw Postgres status. */
  private fun jobs(): List<Pair<JobEntity, String>> =
    query("SELECT *, status::text AS status_text FROM jobs ORDER BY id") {
      val rawStatus = it.getString("status_text")
      JobEntity(
        id = it.getLong("id"),
        externalId = it.getString("externalId"),
        externalUrl = it.getString("externalUrl"),
        siteId = it.getLong("siteId"),
        linkId = it.getLong("link_id").takeUnless { _ -> it.wasNull() },
        title = it.getString("title"),
        companyName = it.getString("companyName"),
        companyLogo = it.getString("companyLogo"),
        location = it.getString("location"),
        salary = it.getString("salary"),
        jobType = it.getString("jobType"),
        tags = it.textArray("tags"),
        description = it.getString("description"),
        status = JobStatus.fromWire(rawStatus).wire,
        labels = it.textArray("labels"),
        excludeReason = it.getString("exclude_reason"),
        createdAt = it.epochMs("created_at"),
        updatedAt = it.epochMs("updated_at"),
      ) to rawStatus
    }

  private fun notes(): List<NoteEntity> =
    query("SELECT * FROM notes ORDER BY id") {
      NoteEntity(
        id = it.getLong("id"),
        jobId = it.getLong("job_id"),
        text = it.getString("text"),
        files = it.textArray("files"),
        createdAt = it.epochMs("created_at"),
      )
    }

  private fun advancedMatching(): List<AdvancedMatchingEntity> =
    query("SELECT * FROM advanced_matching ORDER BY id") {
      AdvancedMatchingEntity(
        prompt = it.getString("chatgpt_prompt"),
        blacklistedCompanies = it.textArray("blacklisted_companies"),
      )
    }

  private fun aiUsage(): List<AiUsageDailyEntity> =
    query("SELECT usage_date::text AS day, * FROM ai_usage_daily ORDER BY usage_date") {
      AiUsageDailyEntity(
        usageDate = it.getString("day"),
        inputTokens = it.getLong("input_tokens"),
        outputTokens = it.getLong("output_tokens"),
        updatedAt = it.epochMs("updated_at"),
      )
    }

  private fun <T> query(sql: String, map: (ResultSet) -> T): List<T> =
    connection.createStatement().use { st ->
      st.executeQuery(sql).use { rs ->
        buildList {
          while (rs.next()) add(map(rs))
        }
      }
    }

  override fun close() = connection.close()

  companion object {
    /**
     * Accepts a JDBC URL (`jdbc:postgresql://…`) or a libpq URL
     * (`postgresql://user:password@host:port/db`).
     */
    fun connect(url: String): Connection {
      if (url.startsWith("jdbc:")) return DriverManager.getConnection(url)
      val uri = URI(url.replaceFirst("postgres://", "postgresql://"))
      require(uri.scheme == "postgresql") { "Unsupported Postgres URL: $url" }
      val props = Properties()
      uri.rawUserInfo?.split(':', limit = 2)?.let { parts ->
        props["user"] = URLDecoder.decode(parts[0], Charsets.UTF_8)
        parts.getOrNull(1)?.let { props["password"] = URLDecoder.decode(it, Charsets.UTF_8) }
      }
      val port = if (uri.port == -1) 5432 else uri.port
      val query = uri.rawQuery?.let { "?$it" } ?: ""
      return DriverManager.getConnection(
        "jdbc:postgresql://${uri.host}:$port${uri.path}$query",
        props,
      )
    }
  }
}

private fun ResultSet.epochMs(column: String): Long =
  getObject(column, OffsetDateTime::class.java).toInstant().toEpochMilli()

/** A Postgres `text[]` as a list; SQL NULL becomes an empty list. */
@Suppress("UNCHECKED_CAST")
private fun ResultSet.textArray(column: String): List<String> =
  (getArray(column)?.array as Array<String>?)?.toList() ?: emptyList()
