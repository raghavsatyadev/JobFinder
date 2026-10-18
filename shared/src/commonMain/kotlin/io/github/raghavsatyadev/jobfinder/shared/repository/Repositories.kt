package io.github.raghavsatyadev.jobfinder.shared.repository

import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
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
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTrial
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeInfo
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsPart
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Jobs. On desktop the server implements it in process; on Android a Ktor client implements it. */
interface JobsRepository {
  /**
   * One page of jobs in [status], newest update first (ties broken by id). [after] is the
   * `nextPageToken` of the previous page (`"<id>!<updatedAtMs>"`). Listed jobs have no description.
   */
  suspend fun listJobs(
    status: JobStatus,
    filter: JobFilter = JobFilter(),
    limit: Int = DEFAULT_PAGE_SIZE,
    after: String? = null,
  ): JobPage

  /** Job counts per status for [filter]; [status] narrows the result to one status. */
  suspend fun countJobs(filter: JobFilter = JobFilter(), status: JobStatus? = null): JobCounts

  /** A job with its description, or `null`. */
  suspend fun getJob(id: Long): Job?

  /** Sets the status and bumps `updatedAt`, so the job moves to the top of its new tab. */
  suspend fun updateStatus(id: Long, status: JobStatus)

  /** Replaces the labels; `updatedAt` stays. Returns the updated job, or `null` if missing. */
  suspend fun updateLabels(id: Long, labels: List<JobLabel>): Job?

  /** Moves every job in [from] to [to] and bumps their `updatedAt`. Returns the number moved. */
  suspend fun changeAllStatuses(from: JobStatus, to: JobStatus): Int

  companion object {
    /** The old app's page size. */
    const val DEFAULT_PAGE_SIZE = 50
  }
}

/** Saved job searches. Creating a link needs a scan of its page, so it lives in the scan API. */
interface LinksRepository {
  suspend fun listLinks(): List<Link>

  suspend fun getLink(id: Long): Link?

  /** Returns the updated link, or `null` if missing. */
  suspend fun updateLink(id: Long, title: String, url: String): Link?

  /** Deletes the link; its jobs stay and lose their link id. */
  suspend fun deleteLink(id: Long)
}

/** The seeded job boards. */
interface SitesRepository {
  suspend fun listSites(): List<Site>
}

/** Notes on jobs. */
interface NotesRepository {
  suspend fun listNotes(jobId: Long): List<Note>

  suspend fun createNote(jobId: Long, text: String, files: List<String> = emptyList()): Note

  /** Returns the updated note, or `null` if missing. */
  suspend fun updateNote(id: Long, text: String): Note?

  /** Returns the updated note, or `null` if missing. */
  suspend fun addFileToNote(id: Long, file: String): Note?

  suspend fun deleteNote(id: Long)
}

/** The instructions the app gives the AI, which the user can edit on the computer. */
interface AiPromptsRepository {
  suspend fun listPrompts(): List<AiPrompt>

  /** Saves [text] as [id]'s instructions; the app's own text clears the edit. */
  suspend fun savePrompt(id: PromptId, text: String): AiPrompt

  /** Back to the app's own text. */
  suspend fun resetPrompt(id: PromptId): AiPrompt

  /** Runs [text] as [id]'s instructions on one saved job; nothing is saved. */
  suspend fun tryPrompt(id: PromptId, text: String): PromptTrial
}

/** The advanced-matching (AI filter) configuration. */
interface MatchingRepository {
  suspend fun getConfig(): AdvancedMatchingConfig

  suspend fun updateConfig(config: AdvancedMatchingConfig): AdvancedMatchingConfig
}

/** The resume and the ATS reviews of jobs against it. */
/** Company ratings and salary, read on the computer from public pages and cached per company. */
interface InsightsRepository {
  /**
   * What is known about [jobId]'s company. Queues a read when nothing fresh is cached; [retry]
   * reads again a company whose last read failed.
   */
  suspend fun requestInsights(jobId: Long, retry: Boolean = false): InsightsStatus
}

interface TipsRepository {
  /** [jobId]'s negotiation tips; [write] asks the AI for new ones (first time or again). */
  suspend fun requestTips(jobId: Long, write: Boolean = false): TipsStatus
}

/** A fix plan for the resume against one job's ATS review, written on the computer when asked. */
interface ResumeImproveRepository {
  /** [jobId]'s plan; [write] asks the AI for a new one (first time or again). */
  suspend fun requestImprovement(jobId: Long, write: Boolean = false): ImprovementStatus

  /** The resume's text with [edits] (ids from the plan) applied. */
  suspend fun improvedResume(jobId: Long, edits: List<Int>): ImprovedResume
}

interface GhostRepository {
  /**
   * The ghost-job check of [jobId]: its signals at once, and a queued read of the company's careers
   * site when none is fresh; [retry] reads again after a failure.
   */
  suspend fun requestGhostCheck(jobId: Long, retry: Boolean = false): GhostStatus
}

interface AtsRepository {
  /** The current resume, or `null` when none is uploaded. */
  suspend fun getResume(): ResumeInfo?

  /**
   * Reads a PDF, DOCX or text resume and keeps its text; every job is scored again. Throws when the
   * file can't be read.
   */
  suspend fun uploadResume(fileName: String, bytes: ByteArray): ResumeInfo

  suspend fun deleteResume()

  /** Where [jobId]'s review stands. Queues it first, and retries a failed one. */
  suspend fun requestReview(jobId: Long): AtsStatus

  companion object {
    /** Largest resume file accepted. */
    const val MAX_RESUME_BYTES = 5 * 1024 * 1024
  }
}

/** App settings. The pairing token is not exposed here; only the desktop app reads it. */
interface SettingsRepository {
  suspend fun getScannerSettings(): ScannerSettings

  suspend fun updateScannerSettings(settings: ScannerSettings): ScannerSettings

  suspend fun getServerSettings(): ServerSettings

  suspend fun updateServerSettings(settings: ServerSettings): ServerSettings

  /** The job detail's section order and hidden sections. */
  suspend fun getDetailLayout(): DetailLayout

  /** Saves [layout]; open job details follow it through `DetailLayoutChanged`. */
  suspend fun updateDetailLayout(layout: DetailLayout): DetailLayout
}

/**
 * Backups (see `BackupFiles`): settings only, or everything. Never holds API keys, the pairing
 * token or site sign-ins.
 */
interface BackupRepository {
  /** The chosen [parts] of the settings; never jobs. */
  suspend fun exportSettings(parts: Set<SettingsPart>): SettingsBackup

  /** Applies [parts] of [backup]; searches are added by URL, the rest replaces the current. */
  suspend fun importSettings(backup: SettingsBackup, parts: Set<SettingsPart>): ImportSummary

  /** A full backup: every setting, search, job, note, review and their attachments, as a zip. */
  suspend fun exportFull(): ByteArray

  /**
   * Imports a full backup made by [exportFull]. Throws `BackupFormatException` (as a bad request on
   * the phone) when it is no backup or comes from a newer app.
   */
  suspend fun importFull(bytes: ByteArray, mode: ImportMode): ImportSummary

  /** Every job as a CSV spreadsheet (UTF-8): plain job data, never settings or keys. */
  suspend fun exportJobsCsv(): ByteArray
}

/**
 * Scans and the live events around them. On desktop the server implements it in process; on Android
 * a Ktor client over `/v1/scan`, `/v1/links` and `/v1/events` does.
 */
interface ScanRepository {
  val state: StateFlow<ScanState>

  /** New jobs, scan state changes and broken links, as they happen. */
  val events: Flow<ServerEvent>

  /** Starts a scan of every link; `false` when one is already running. */
  suspend fun startScanAll(): Boolean

  /** Scans one link now; returns the ids of the new jobs. */
  suspend fun scanLink(linkId: Long): List<Long>

  /** Reads one job's description again; returns the updated job. */
  suspend fun scanJob(jobId: Long): Job

  /**
   * Saves a search after loading its page. Throws when the page shows no job list, unless [force]
   * is set (see `CreateLinkRequest`).
   */
  /**
   * Saves a search. [siteName] names a board the app does not know (a custom board); `null` takes
   * the name from the web address.
   */
  suspend fun createLink(
    title: String,
    url: String,
    force: Boolean = false,
    siteName: String? = null,
  ): CreateLinkResponse

  /** Links whose last three or more scans failed. */
  suspend fun brokenLinks(): List<Link>
}
