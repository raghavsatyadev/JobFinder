package io.github.raghavsatyadev.jobfinder.shared.remote

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.ChangeAllStatusesRequest
import io.github.raghavsatyadev.jobfinder.shared.api.CountResponse
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkRequest
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.CreateNoteRequest
import io.github.raghavsatyadev.jobfinder.shared.api.JobQueryParams
import io.github.raghavsatyadev.jobfinder.shared.api.ScanStartResponse
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateJobLabelsRequest
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateJobStatusRequest
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateLinkRequest
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateNoteRequest
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.ApplyEdits
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.GhostStatus
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.ImportSummary
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovedResume
import io.github.raghavsatyadev.jobfinder.shared.model.ImprovementStatus
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobCounts
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobPage
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.PromptEdit
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** `null` instead of an [ApiFailure] with [ApiErrorCode.NOT_FOUND]. */
internal inline fun <T> orNullIfMissing(block: () -> T): T? =
  try {
    block()
  } catch (e: ApiFailure) {
    if (e.code == ApiErrorCode.NOT_FOUND) null else throw e
  }

/** Jobs over `/v1/jobs`. */
class RemoteJobsRepository(private val api: RemoteApi) : JobsRepository {
  override suspend fun listJobs(
    status: JobStatus,
    filter: JobFilter,
    limit: Int,
    after: String?,
  ): JobPage =
    api.get("/jobs", JobPage.serializer()) {
      parameter(JobQueryParams.STATUS, status.wire)
      parameter(JobQueryParams.LIMIT, limit)
      after?.let { parameter(JobQueryParams.AFTER, it) }
      filterParameters(filter)
    }

  override suspend fun countJobs(filter: JobFilter, status: JobStatus?): JobCounts =
    api.get("/jobs/counts", JobCounts.serializer()) {
      status?.let { parameter(JobQueryParams.STATUS, it.wire) }
      filterParameters(filter)
    }

  override suspend fun getJob(id: Long): Job? = orNullIfMissing {
    api.get("/jobs/$id", Job.serializer())
  }

  override suspend fun updateStatus(id: Long, status: JobStatus) {
    api.send(
      HttpMethod.Put,
      "/jobs/$id/status",
      UpdateJobStatusRequest(status),
      UpdateJobStatusRequest.serializer(),
      Job.serializer(),
    )
  }

  override suspend fun updateLabels(id: Long, labels: List<JobLabel>): Job? = orNullIfMissing {
    api.send(
      HttpMethod.Put,
      "/jobs/$id/labels",
      UpdateJobLabelsRequest(labels),
      UpdateJobLabelsRequest.serializer(),
      Job.serializer(),
    )
  }

  override suspend fun changeAllStatuses(from: JobStatus, to: JobStatus): Int =
    api
      .send(
        HttpMethod.Post,
        "/jobs/change-all-statuses",
        ChangeAllStatusesRequest(from, to),
        ChangeAllStatusesRequest.serializer(),
        CountResponse.serializer(),
      )
      .count

  companion object {
    /** The list filters as repeated query parameters, as the server's `jobFilter()` reads them. */
    fun HttpRequestBuilder.filterParameters(filter: JobFilter) {
      filter.search?.takeIf { it.isNotBlank() }?.let { parameter(JobQueryParams.SEARCH, it) }
      filter.siteIds.forEach { parameter(JobQueryParams.SITE, it) }
      filter.linkIds.forEach { parameter(JobQueryParams.LINK, it) }
      filter.labels.forEach { parameter(JobQueryParams.LABEL, it.wire) }
      filter.workModes.forEach { parameter(JobQueryParams.WORK_MODE, it.wire) }
      filter.applyMethods.forEach { parameter(JobQueryParams.APPLY_METHOD, it.wire) }
      if (filter.sort != JobSort.LATEST) parameter(JobQueryParams.SORT, filter.sort.wire)
      if (filter.starred) parameter(JobQueryParams.STARRED, true)
    }
  }
}

/** Saved searches over `/v1/links`. */
class RemoteLinksRepository(private val api: RemoteApi) : LinksRepository {
  override suspend fun listLinks(): List<Link> =
    api.get("/links", ListSerializer(Link.serializer()))

  override suspend fun getLink(id: Long): Link? = orNullIfMissing {
    api.get("/links/$id", Link.serializer())
  }

  override suspend fun updateLink(id: Long, title: String, url: String): Link? = orNullIfMissing {
    api.send(
      HttpMethod.Put,
      "/links/$id",
      UpdateLinkRequest(title, url),
      UpdateLinkRequest.serializer(),
      Link.serializer(),
    )
  }

  override suspend fun deleteLink(id: Long) = api.delete("/links/$id")

  /** `POST /v1/links`: the desktop loads the page in its browser before saving. */
  suspend fun createLink(
    title: String,
    url: String,
    force: Boolean,
    siteName: String? = null,
  ): CreateLinkResponse =
    api.send(
      HttpMethod.Post,
      "/links",
      CreateLinkRequest(title, url, force, siteName),
      CreateLinkRequest.serializer(),
      CreateLinkResponse.serializer(),
    )

  suspend fun brokenLinks(): List<Link> =
    api.get("/links/broken", ListSerializer(Link.serializer()))
}

/** The job boards over `/v1/sites`. */
class RemoteSitesRepository(private val api: RemoteApi) : SitesRepository {
  override suspend fun listSites(): List<Site> =
    api.get("/sites", ListSerializer(Site.serializer()))
}

/**
 * Notes over `/v1/jobs/{id}/notes` and `/v1/notes/{id}`. Files can only be added on the desktop.
 */
class RemoteNotesRepository(private val api: RemoteApi) : NotesRepository {
  override suspend fun listNotes(jobId: Long): List<Note> =
    api.get("/jobs/$jobId/notes", ListSerializer(Note.serializer()))

  override suspend fun createNote(jobId: Long, text: String, files: List<String>): Note {
    if (files.isNotEmpty()) throw ApiFailure(ApiErrorCode.UNAVAILABLE, FILES_ON_DESKTOP)
    return api.send(
      HttpMethod.Post,
      "/jobs/$jobId/notes",
      CreateNoteRequest(text),
      CreateNoteRequest.serializer(),
      Note.serializer(),
    )
  }

  override suspend fun updateNote(id: Long, text: String): Note? = orNullIfMissing {
    api.send(
      HttpMethod.Put,
      "/notes/$id",
      UpdateNoteRequest(text),
      UpdateNoteRequest.serializer(),
      Note.serializer(),
    )
  }

  override suspend fun addFileToNote(id: Long, file: String): Note? =
    throw ApiFailure(ApiErrorCode.UNAVAILABLE, FILES_ON_DESKTOP)

  override suspend fun deleteNote(id: Long) = api.delete("/notes/$id")

  private companion object {
    const val FILES_ON_DESKTOP = "Add files to notes on the desktop app."
  }
}

/** The AI filter over `/v1/matching`. */
class RemoteMatchingRepository(private val api: RemoteApi) : MatchingRepository {
  override suspend fun getConfig(): AdvancedMatchingConfig =
    api.get("/matching", AdvancedMatchingConfig.serializer())

  override suspend fun updateConfig(config: AdvancedMatchingConfig): AdvancedMatchingConfig =
    api.send(
      HttpMethod.Put,
      "/matching",
      config,
      AdvancedMatchingConfig.serializer(),
      AdvancedMatchingConfig.serializer(),
    )
}

/** The AI instructions over `/v1/prompts`. */
class RemoteAiPromptsRepository(private val api: RemoteApi) : AiPromptsRepository {
  override suspend fun listPrompts(): List<AiPrompt> =
    api.get("/prompts", ListSerializer(AiPrompt.serializer()))

  override suspend fun savePrompt(id: PromptId, text: String): AiPrompt = edit(id, text)

  override suspend fun resetPrompt(id: PromptId): AiPrompt = edit(id, null)

  private suspend fun edit(id: PromptId, text: String?): AiPrompt =
    api.send(
      HttpMethod.Put,
      "/prompts/${id.wire}",
      PromptEdit(text),
      PromptEdit.serializer(),
      AiPrompt.serializer(),
    )
}

/** Settings over `/v1/settings`. */
class RemoteSettingsRepository(private val api: RemoteApi) : SettingsRepository {
  override suspend fun getScannerSettings(): ScannerSettings =
    api.get("/settings/scanner", ScannerSettings.serializer())

  override suspend fun updateScannerSettings(settings: ScannerSettings): ScannerSettings =
    api.send(
      HttpMethod.Put,
      "/settings/scanner",
      settings,
      ScannerSettings.serializer(),
      ScannerSettings.serializer(),
    )

  override suspend fun getServerSettings(): ServerSettings =
    api.get("/settings/server", ServerSettings.serializer())

  override suspend fun updateServerSettings(settings: ServerSettings): ServerSettings =
    api.send(
      HttpMethod.Put,
      "/settings/server",
      settings,
      ServerSettings.serializer(),
      ServerSettings.serializer(),
    )

  override suspend fun getDetailLayout(): DetailLayout =
    api.get("/settings/detail-layout", DetailLayout.serializer())

  override suspend fun updateDetailLayout(layout: DetailLayout): DetailLayout =
    api.send(
      HttpMethod.Put,
      "/settings/detail-layout",
      layout,
      DetailLayout.serializer(),
      DetailLayout.serializer(),
    )
}

/** The resume and ATS reviews over `/v1/resume` and `/v1/jobs/{id}/ats`. */
class RemoteAtsRepository(private val api: RemoteApi) : AtsRepository {
  override suspend fun getResume(): ResumeInfo? = orNullIfMissing {
    api.get("/resume", ResumeInfo.serializer())
  }

  override suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeInfo =
    api.call(HttpMethod.Put, "/resume", ResumeInfo.serializer()) {
      setBody(
        MultiPartFormDataContent(
          formData {
            append(
              "file",
              bytes,
              Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
              },
            )
          }
        )
      )
    }

  override suspend fun deleteResume() = api.delete("/resume")

  override suspend fun requestReview(jobId: Long): AtsStatus =
    api.post("/jobs/$jobId/ats", AtsStatus.serializer())
}

/** Company ratings and salary over `/v1/jobs/{id}/insights`. */
class RemoteInsightsRepository(private val api: RemoteApi) : InsightsRepository {
  override suspend fun requestInsights(jobId: Long, retry: Boolean): InsightsStatus =
    api.post(
      "/jobs/$jobId/insights" + if (retry) "?retry=true" else "",
      InsightsStatus.serializer(),
    )
}

/** Negotiation tips over `/v1/jobs/{id}/tips`. */
class RemoteTipsRepository(private val api: RemoteApi) : TipsRepository {
  override suspend fun requestTips(jobId: Long, write: Boolean): TipsStatus =
    api.post("/jobs/$jobId/tips" + if (write) "?write=true" else "", TipsStatus.serializer())
}

/** Resume fix plans over `/v1/jobs/{id}/improve`. */
class RemoteResumeImproveRepository(private val api: RemoteApi) : ResumeImproveRepository {
  override suspend fun requestImprovement(jobId: Long, write: Boolean): ImprovementStatus =
    api.post(
      "/jobs/$jobId/improve" + if (write) "?write=true" else "",
      ImprovementStatus.serializer(),
    )

  override suspend fun improvedResume(jobId: Long, edits: List<Int>): ImprovedResume =
    api.send(
      HttpMethod.Post,
      "/jobs/$jobId/improve/text",
      ApplyEdits(edits),
      ApplyEdits.serializer(),
      ImprovedResume.serializer(),
    )
}

/** The ghost-job check over `/v1/jobs/{id}/ghost`. */
class RemoteGhostRepository(private val api: RemoteApi) : GhostRepository {
  override suspend fun requestGhostCheck(jobId: Long, retry: Boolean): GhostStatus =
    api.post("/jobs/$jobId/ghost" + if (retry) "?retry=true" else "", GhostStatus.serializer())
}

/** `POST /v1/scan`. */
internal suspend fun RemoteApi.startScan(): ScanStartResponse =
  post("/scan", ScanStartResponse.serializer())

/** `POST /v1/scan/links/{id}`: the ids of the new jobs. */
internal suspend fun RemoteApi.scanLink(id: Long): List<Long> =
  post("/scan/links/$id", ListSerializer(Long.serializer()))

/** `POST /v1/scan/jobs/{id}`: the job with its description read again. */
internal suspend fun RemoteApi.scanJob(id: Long): Job = post("/scan/jobs/$id", Job.serializer())

/**
 * Backups over `/v1/backup`: `GET|POST /settings?parts=…` and `GET|POST /full` (a zip;
 * `?mode=merge|replace` on import).
 */
class RemoteBackupRepository(private val api: RemoteApi) : BackupRepository {
  override suspend fun exportSettings(parts: Set<SettingsPart>): SettingsBackup =
    api.get("/backup/settings", SettingsBackup.serializer()) {
      parameter(BackupParams.PARTS, BackupParams.parts(parts))
    }

  override suspend fun importSettings(
    backup: SettingsBackup,
    parts: Set<SettingsPart>,
  ): ImportSummary =
    api.call(HttpMethod.Post, "/backup/settings", ImportSummary.serializer()) {
      parameter(BackupParams.PARTS, BackupParams.parts(parts))
      contentType(ContentType.Application.Json)
      setBody(ApiJson.encodeToString(SettingsBackup.serializer(), backup))
    }

  override suspend fun exportFull(): ByteArray = api.download("/backup/full")

  override suspend fun exportJobsCsv(): ByteArray = api.download("/jobs/export.csv")

  override suspend fun importFull(bytes: ByteArray, mode: ImportMode): ImportSummary =
    api.call(HttpMethod.Post, "/backup/full", ImportSummary.serializer()) {
      parameter(BackupParams.MODE, ApiJson.encodeToString(ImportMode.serializer(), mode).trim('"'))
      contentType(ContentType.Application.Zip)
      setBody(bytes)
      // A replace waits for the user to allow it on the computer.
      if (mode == ImportMode.REPLACE) {
        timeout {
          requestTimeoutMillis = REPLACE_TIMEOUT_MS
          socketTimeoutMillis = REPLACE_TIMEOUT_MS
        }
      }
    }

  private companion object {
    const val REPLACE_TIMEOUT_MS = 10L * 60 * 1000
  }
}

/** Query parameters of `/v1/backup`. */
object BackupParams {
  const val PARTS = "parts"
  const val MODE = "mode"

  /** `searches,matching` from the parts' wire names. */
  fun parts(parts: Set<SettingsPart>): String =
    parts.joinToString(",") { ApiJson.encodeToString(SettingsPart.serializer(), it).trim('"') }

  /** The parts named in [value]; unknown names are skipped. */
  fun parseParts(value: String?): Set<SettingsPart> =
    value
      .orEmpty()
      .split(',')
      .mapNotNull { name -> SettingsPart.entries.firstOrNull { parts(setOf(it)) == name.trim() } }
      .toSet()

  fun parseMode(value: String?): ImportMode? =
    ImportMode.entries.firstOrNull {
      ApiJson.encodeToString(ImportMode.serializer(), it).trim('"') == value
    }
}
