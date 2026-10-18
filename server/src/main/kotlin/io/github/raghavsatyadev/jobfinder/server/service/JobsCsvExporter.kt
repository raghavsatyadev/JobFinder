package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import java.time.Instant
import java.time.ZoneId

/**
 * The jobs as a spreadsheet: one row per job with its plain data (no settings, searches' setup or
 * keys). A header row, comma, `\n`, quotes only where needed, and a UTF-8 byte order mark so Excel
 * reads accents right.
 */
class JobsCsvExporter(
  private val jobs: JobsRepository,
  private val db: ServerDatabase,
  private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
  /** Every job in [statuses] (by default all but deleted ones and those still being read). */
  suspend fun export(statuses: List<JobStatus> = EXPORTED): String {
    val sites = db.sitesDao().listAll().associate { it.id to it.name }
    val links = db.linksDao().listAll().associate { it.id to it.title }
    val out = StringBuilder(BOM)
    out.append(HEADER.joinToString(",")).append('\n')
    for (status in statuses) {
      var after: String? = null
      do {
        val page = jobs.listJobs(status, limit = BATCH_SIZE, after = after)
        for (job in page.jobs) {
          out.append(row(job, sites, links).joinToString(",") { escape(it) }).append('\n')
        }
        after = page.nextPageToken
      } while (after != null)
    }
    return out.toString()
  }

  private fun row(job: Job, sites: Map<Long, String>, links: Map<Long, String>) =
    listOf(
      job.title,
      job.companyName,
      job.location,
      job.salary,
      job.workMode?.wire,
      job.status.wire,
      job.labels.joinToString("; ") { it.wire },
      job.ats?.atsMatch?.toString(),
      job.ats?.overall?.toString(),
      job.excludeReason,
      sites[job.siteId],
      job.linkId?.let(links::get),
      date(job.createdAt),
      date(job.updatedAt),
      job.externalUrl,
      job.description?.take(MAX_CELL_CHARS),
    )

  private fun date(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(zone()).toLocalDate().toString()

  companion object {
    val HEADER =
      listOf(
        "title",
        "company",
        "location",
        "salary",
        "work_mode",
        "status",
        "labels",
        "ats_match_percent",
        "ats_overall_of_10",
        "filtered_out_because",
        "site",
        "search",
        "found_on",
        "updated_on",
        "url",
        "description",
      )

    val EXPORTED =
      listOf(
        JobStatus.NEW,
        JobStatus.APPLIED,
        JobStatus.ARCHIVED,
        JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
      )

    private const val BOM = "﻿"
    private const val BATCH_SIZE = 300

    /** Excel's limit for one cell. */
    private const val MAX_CELL_CHARS = 32_000

    /** First characters that make Excel and LibreOffice read a cell as a formula. */
    private const val FORMULA_START = "=+-@\t\r"

    fun escape(raw: String?): String {
      if (raw == null) return ""
      val value = if (raw.isNotEmpty() && raw[0] in FORMULA_START) "'$raw" else raw
      val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
      return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
  }
}
