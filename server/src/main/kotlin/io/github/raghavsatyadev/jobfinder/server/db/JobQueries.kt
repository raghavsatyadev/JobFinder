package io.github.raghavsatyadev.jobfinder.server.db

import androidx.room3.RoomRawQuery
import io.github.raghavsatyadev.jobfinder.shared.model.ApplyMethod
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode

/**
 * Cursor of a job page: the last job's id, `updated_at` and, for orders other than latest, its sort
 * key. Encoded as `"<id>!<updatedAtMs>"` or `"<id>!<updatedAtMs>!<key>"`.
 */
data class PageToken(val id: Long, val updatedAtMs: Long, val key: Long? = null) {
  fun encode(): String = "$id$SEPARATOR$updatedAtMs" + (key?.let { "$SEPARATOR$it" } ?: "")

  companion object {
    private const val SEPARATOR = '!'

    /** Throws [IllegalArgumentException] for a malformed token. */
    fun decode(token: String): PageToken {
      val parts = token.split(SEPARATOR)
      val id = parts.getOrNull(0)?.toLongOrNull()
      val updatedAt = parts.getOrNull(1)?.toLongOrNull()
      val key = parts.getOrNull(2)?.toLongOrNull()
      require(
        (parts.size == 2 || (parts.size == 3 && key != null)) && id != null && updatedAt != null
      ) {
        "Invalid page token: $token"
      }
      return PageToken(id, updatedAt, key)
    }
  }
}

/**
 * Turns user search text into an FTS5 query that approximates Postgres `plainto_tsquery('english',
 * text)`: split into words, drop English stop words, then AND the remaining words as quoted terms.
 * The `porter` tokenizer stems both sides, so "developers" matches "developer".
 */
object FtsSearch {
  sealed interface Query {
    /** Blank text: no search filter. */
    data object None : Query

    /** Only stop words: Postgres matches nothing for such a query, and so do we. */
    data object MatchNothing : Query

    data class Match(val expression: String) : Query
  }

  /**
   * The Postgres `english` stop-word list (`tsearch_data/english.stop`, the Snowball list), so the
   * same words are ignored as before.
   */
  val ENGLISH_STOP_WORDS: Set<String> =
    ("i me my myself we our ours ourselves you your yours yourself yourselves he him his himself " +
        "she her hers herself it its itself they them their theirs themselves what which who whom " +
        "this that these those am is are was were be been being have has had having do does did " +
        "doing a an the and but if or because as until while of at by for with about against " +
        "between into through during before after above below to from up down in out on off over " +
        "under again further then once here there when where why how all any both each few more " +
        "most other some such no nor not only own same so than too very s t can will just don " +
        "should now")
      .split(' ')
      .toSet()

  private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

  fun parse(text: String?): Query {
    if (text.isNullOrBlank()) return Query.None
    val words =
      text
        .lowercase()
        .split(NON_WORD)
        .filter { it.isNotEmpty() && it !in ENGLISH_STOP_WORDS }
        .distinct()
    if (words.isEmpty()) return Query.MatchNothing
    // Words hold only letters and digits, so quoting them is safe (no FTS5 syntax inside).
    return Query.Match(words.joinToString(" ") { "\"$it\"" })
  }
}

/**
 * Builds the raw SQL behind `listJobs` and `countJobs` (the old `list_jobs` and `count_jobs`
 * Postgres functions).
 */
object JobQueries {
  /** Every `jobs` column except `description`, which lists don't load, and the ATS summary. */
  private const val LIST_COLUMNS =
    "j.id, j.external_id, j.external_url, j.site_id, j.link_id, j.title, j.company_name, " +
      "j.company_logo, j.location, j.salary, j.job_type, j.tags, j.status, j.labels, " +
      "j.exclude_reason, j.created_at, j.updated_at, a.overall AS ats_overall, " +
      "a.ats_match AS ats_match, a.shortlisted AS ats_shortlisted"

  /** The job's review against the current resume; failed reviews have no score to show. */
  private const val ATS_JOIN =
    "LEFT JOIN ats_reviews a ON a.job_id = j.id AND a.failed = 0 " +
      "AND a.resume_hash = (SELECT hash FROM resume WHERE id = $SINGLETON_ID)"

  /**
   * The SQL of [JobSort.key]; `null` for [JobSort.LATEST]. Must give the same numbers as the Kotlin
   * key, which makes the next page's token.
   */
  fun sortKey(sort: JobSort): String? =
    when (sort) {
      JobSort.LATEST -> null
      JobSort.ATS ->
        "COALESCE(CAST(a.overall * ${JobSort.ATS_SCALE} AS INTEGER), ${JobSort.NO_SCORE})"
      JobSort.ATS_LATEST ->
        "COALESCE(MIN(CAST(a.overall AS INTEGER), ${JobSort.TOP_BAND}), ${JobSort.NO_SCORE})"
      JobSort.REMOTE_FIRST ->
        "(CASE WHEN lower(j.job_type) = '${WorkMode.REMOTE.wire}' THEN 1 ELSE 0 END)"
      JobSort.ONSITE_FIRST ->
        "(CASE WHEN lower(j.job_type) = '${WorkMode.ONSITE.wire}' THEN 1 ELSE 0 END)"
    }

  /**
   * One page of [status] jobs in `filter.sort` order: the sort key highest first, then newest
   * `updated_at`, ties by id. Rows strictly after [after] (row-value compare), at most [limit]
   * rows.
   */
  fun list(status: JobStatus, filter: JobFilter, limit: Int, after: PageToken?): RoomRawQuery {
    val where = Where()
    where.add("j.status = ?", status.wire)
    val key = sortKey(filter.sort)
    if (after != null) {
      if (key == null) where.add("(j.updated_at, j.id) < (?, ?)", after.updatedAtMs, after.id)
      else
        where.add(
          "($key, j.updated_at, j.id) < (?, ?, ?)",
          after.key ?: Long.MAX_VALUE,
          after.updatedAtMs,
          after.id,
        )
    }
    where.addFilter(filter)
    val order = listOfNotNull(key?.let { "$it DESC" }, "j.updated_at DESC", "j.id DESC")
    val sql =
      "SELECT $LIST_COLUMNS FROM jobs j $ATS_JOIN WHERE ${where.sql()} " +
        "ORDER BY ${order.joinToString(", ")} LIMIT ?"
    return where.build(sql, limit.toLong())
  }

  /** Counts per status for [filter], optionally only [status]. */
  fun count(filter: JobFilter, status: JobStatus?): RoomRawQuery {
    val where = Where()
    if (status != null) where.add("j.status = ?", status.wire)
    where.addFilter(filter)
    val sql =
      "SELECT j.status AS status, COUNT(*) AS job_count FROM jobs j WHERE ${where.sql()} " +
        "GROUP BY j.status ORDER BY j.status"
    return where.build(sql)
  }

  private class Where {
    private val clauses = mutableListOf<String>()
    private val args = mutableListOf<Any>()

    fun add(clause: String, vararg values: Any) {
      clauses += clause
      args.addAll(values)
    }

    fun addFilter(filter: JobFilter) {
      // Sources: the job's board or its search, either one (JobFilter.matchesSource).
      val sources =
        listOfNotNull(
          filter.siteIds.takeIf { it.isNotEmpty() }?.let { "j.site_id IN (${marks(it)})" },
          filter.linkIds.takeIf { it.isNotEmpty() }?.let { "j.link_id IN (${marks(it)})" },
        )
      if (sources.isNotEmpty()) {
        add(
          sources.joinToString(" OR ", prefix = "(", postfix = ")"),
          *ids(filter.siteIds),
          *ids(filter.linkIds),
        )
      }
      if (filter.labels.isNotEmpty()) {
        // Old: labels && jobs_labels (any overlap).
        add(
          "EXISTS (SELECT 1 FROM json_each(j.labels) l WHERE l.value IN (${marks(filter.labels)}))",
          *filter.labels.map { it.wire }.toTypedArray(),
        )
      }
      if (filter.workModes.isNotEmpty()) {
        // Old: lower("jobType") = any(jobs_work_modes). A null job type never matches.
        add(
          "lower(j.job_type) IN (${marks(filter.workModes)})",
          *filter.workModes.map { it.wire }.toTypedArray(),
        )
      }
      if (filter.applyMethods.isNotEmpty()) {
        // Old: easy_apply when any tag lower-cases to 'easy apply', else external.
        add(
          "(CASE WHEN EXISTS (SELECT 1 FROM json_each(j.tags) t WHERE lower(t.value) = ?) " +
            "THEN ? ELSE ? END) IN (${marks(filter.applyMethods)})",
          ApplyMethod.EASY_APPLY_TAG,
          ApplyMethod.EASY_APPLY.wire,
          ApplyMethod.EXTERNAL.wire,
          *filter.applyMethods.map { it.wire }.toTypedArray(),
        )
      }
      when (val search = FtsSearch.parse(filter.search)) {
        FtsSearch.Query.None -> Unit
        FtsSearch.Query.MatchNothing -> add("0")
        is FtsSearch.Query.Match ->
          add("j.id IN (SELECT rowid FROM jobs_fts WHERE jobs_fts MATCH ?)", search.expression)
      }
    }

    fun sql(): String = if (clauses.isEmpty()) "1" else clauses.joinToString(" AND ")

    fun build(sql: String, vararg trailing: Any): RoomRawQuery {
      val bound = args + trailing
      return RoomRawQuery(sql) { st ->
        bound.forEachIndexed { index, value ->
          when (value) {
            is Long -> st.bindLong(index + 1, value)
            is Int -> st.bindLong(index + 1, value.toLong())
            is String -> st.bindText(index + 1, value)
            else -> error("Unsupported bind type ${value::class}")
          }
        }
      }
    }

    private fun marks(values: List<*>) = values.joinToString(", ") { "?" }

    private fun ids(values: List<Long>): Array<Any> = values.map { it as Any }.toTypedArray()
  }
}
