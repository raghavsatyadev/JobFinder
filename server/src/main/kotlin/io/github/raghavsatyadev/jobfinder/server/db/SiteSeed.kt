package io.github.raghavsatyadev.jobfinder.server.db

import androidx.sqlite.SQLiteConnection
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * The job boards, from `db/sites_rows.csv` (a copy of the old `supabase/sites_rows.csv` export).
 * Ids are kept, so the migrated links and jobs point at the same boards.
 */
object SiteSeed {
  private const val RESOURCE = "/db/sites_rows.csv"

  fun load(): List<SiteEntity> {
    val text =
      SiteSeed::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
        ?: error("Missing resource $RESOURCE")
    val rows = Csv.parse(text)
    val header = rows.first()
    return rows.drop(1).map { values ->
      val row = header.zip(values).toMap()
      fun col(name: String) = row[name] ?: error("Column $name missing in $RESOURCE")
      SiteEntity(
        id = col("id").toLong(),
        name = col("name"),
        provider = col("provider"),
        urls = jsonListOrEmpty(col("urls")),
        queryParamsToRemove = jsonListOrEmpty(col("queryParamsToRemove")),
        blacklistedPaths = jsonListOrEmpty(col("blacklisted_paths")),
        logoKey = SiteLogos.keyFromLogoUrl(col("logo_url")),
        deprecated = col("deprecated").toBoolean(),
        incognitoSupport = col("incognito_support").toBoolean(),
        createdAt = PostgresTimestamps.parseToEpochMs(col("created_at")),
      )
    }
  }

  /** Inserts [sites] with plain SQL; Room calls this from `onCreate`, before any DAO exists. */
  fun insert(connection: SQLiteConnection, sites: List<SiteEntity>) {
    val sql =
      "INSERT INTO sites (id, name, provider, urls, query_params_to_remove, blacklisted_paths, " +
        "logo_key, deprecated, incognito_support, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    for (site in sites) {
      connection.prepare(sql).use { st ->
        st.bindLong(1, site.id)
        st.bindText(2, site.name)
        st.bindText(3, site.provider)
        st.bindText(4, JsonColumnConverters.encodeStringList(site.urls))
        st.bindText(5, JsonColumnConverters.encodeStringList(site.queryParamsToRemove))
        st.bindText(6, JsonColumnConverters.encodeStringList(site.blacklistedPaths))
        st.bindText(7, site.logoKey)
        st.bindLong(8, if (site.deprecated) 1 else 0)
        st.bindLong(9, if (site.incognitoSupport) 1 else 0)
        st.bindLong(10, site.createdAt)
        st.step()
      }
    }
  }

  private fun jsonListOrEmpty(value: String): List<String> =
    if (value.isBlank()) emptyList() else JsonColumnConverters.decodeStringList(value)
}

/**
 * Site logos ship in `composeApp/src/commonMain/composeResources/drawable/site_<key>.*` (copied
 * from the old `other/assets/sitesIcons`). Nothing is downloaded.
 */
object SiteLogos {
  const val FALLBACK_KEY = "custom"

  /** Every bundled logo key. */
  val BUNDLED_KEYS =
    setOf(
      "bestjobs",
      "builtin",
      "custom",
      "dice",
      "echojobs",
      "flexjobs",
      "glassdoor",
      "hiringcafe",
      "indeed",
      "linkedin",
      "remoteio",
      "remoteok",
      "remotive",
      "usajobs",
      "weworkremotely",
      "ziprecruiter",
    )

  /**
   * The logo key for an old Supabase storage URL: the file name without extension or query, for
   * example `.../linkedin.png` is `linkedin`. Logos that are not bundled (`robert-half`, `naukri`)
   * fall back to [FALLBACK_KEY].
   */
  fun keyFromLogoUrl(url: String): String {
    val key = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').lowercase()
    return if (key in BUNDLED_KEYS) key else FALLBACK_KEY
  }
}

/** Parses Postgres text timestamps, such as `2024-01-20 15:02:02.723394+00`. */
object PostgresTimestamps {
  private val FORMAT: DateTimeFormatter =
    DateTimeFormatterBuilder()
      .appendPattern("yyyy-MM-dd HH:mm:ss")
      .optionalStart()
      .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
      .optionalEnd()
      .appendOffset("+HH:mm", "+00")
      .toFormatter()

  fun parseToEpochMs(value: String): Long =
    OffsetDateTime.parse(value.trim(), FORMAT).toInstant().toEpochMilli()
}

/** Minimal RFC 4180 CSV reader: quoted fields, doubled quotes, commas and newlines in quotes. */
internal object Csv {
  fun parse(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    fun endField() {
      row.add(field.toString())
      field.clear()
    }
    fun endRow() {
      endField()
      if (row.size > 1 || row[0].isNotEmpty()) rows.add(row)
      row = mutableListOf()
    }
    while (i < text.length) {
      val c = text[i]
      when {
        inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
          field.append('"')
          i++
        }
        c == '"' -> inQuotes = !inQuotes
        !inQuotes && c == ',' -> endField()
        !inQuotes && c == '\n' -> endRow()
        !inQuotes && c == '\r' -> Unit
        else -> field.append(c)
      }
      i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
  }
}
