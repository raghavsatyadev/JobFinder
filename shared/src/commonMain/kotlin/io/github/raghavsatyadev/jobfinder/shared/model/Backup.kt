package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The parts of a settings backup. Each can be left out on export and on import. The pairing token
 * and site sign-ins are never part of any backup; online API keys only travel beside it, in a
 * password-locked file from the computer ([BackupApiKey]).
 */
@Serializable
enum class SettingsPart {
  @SerialName("searches") SEARCHES,
  @SerialName("matching") MATCHING,
  @SerialName("scanner") SCANNER,
  @SerialName("resume") RESUME,
  /** The port and network: another PC may need others, so it is off by default. */
  @SerialName("server") SERVER,
  /** The AI provider and model; never the API key, which is typed again on the new computer. */
  @SerialName("ai") AI,
  /** The AI instructions the user edited. */
  @SerialName("prompts") PROMPTS,
  /** The job detail's section order and hidden sections. */
  @SerialName("detail_layout") DETAIL_LAYOUT;

  companion object {
    /** What an export ticks before the user changes it. */
    val DEFAULT: Set<SettingsPart> = setOf(SEARCHES, MATCHING, SCANNER, AI, PROMPTS, DETAIL_LAYOUT)
  }
}

/** A saved search in a backup; [site] is the job board's name, the same on every install. */
@Serializable data class BackupSearch(val title: String, val url: String, val site: String)

/** The resume's text as the computer read it; the file itself is not kept. */
@Serializable
data class BackupResume(
  val fileName: String,
  val text: String,
  val pages: Int? = null,
  val words: Int,
  val flags: List<String> = emptyList(),
  val uploadedAt: Long,
)

/**
 * A settings backup: only settings, never jobs. A part left out is `null`. Written as
 * `jobfinder-settings-<date>.json`, and inside every full backup.
 */
@Serializable
data class SettingsBackup(
  val formatVersion: Int = BackupFormatVersion.CURRENT,
  val kind: String = KIND,
  val appVersion: String = "",
  val exportedAt: Long = 0,
  val note: String = NO_SECRETS,
  val searches: List<BackupSearch>? = null,
  val matching: AdvancedMatchingConfig? = null,
  val scanner: ScannerSettings? = null,
  val resume: BackupResume? = null,
  val server: ServerSettings? = null,
  val ai: AiSettings? = null,
  /** The edited AI instructions; a prompt not listed uses the app's own text. */
  val prompts: Map<PromptId, String>? = null,
  val detailLayout: DetailLayout? = null,
) {
  /** The parts this backup holds. */
  val parts: Set<SettingsPart>
    get() = buildSet {
      if (searches != null) add(SettingsPart.SEARCHES)
      if (matching != null) add(SettingsPart.MATCHING)
      if (scanner != null) add(SettingsPart.SCANNER)
      if (resume != null) add(SettingsPart.RESUME)
      if (server != null) add(SettingsPart.SERVER)
      if (ai != null) add(SettingsPart.AI)
      if (prompts != null) add(SettingsPart.PROMPTS)
      if (detailLayout != null) add(SettingsPart.DETAIL_LAYOUT)
    }

  /** Only [keep] of this backup's parts. */
  fun only(keep: Set<SettingsPart>): SettingsBackup =
    copy(
      searches = searches.takeIf { SettingsPart.SEARCHES in keep },
      matching = matching.takeIf { SettingsPart.MATCHING in keep },
      scanner = scanner.takeIf { SettingsPart.SCANNER in keep },
      resume = resume.takeIf { SettingsPart.RESUME in keep },
      server = server.takeIf { SettingsPart.SERVER in keep },
      ai = ai.takeIf { SettingsPart.AI in keep },
      prompts = prompts.takeIf { SettingsPart.PROMPTS in keep },
      detailLayout = detailLayout.takeIf { SettingsPart.DETAIL_LAYOUT in keep },
    )

  companion object {
    const val KIND = "settings"
    const val NO_SECRETS =
      "JobFinder settings. No API keys, pairing tokens or site sign-ins are ever exported."
  }
}

/** The backup format; a backup from a newer app is refused, an older one is read. */
object BackupFormatVersion {
  const val CURRENT = 1
}

/** What a full backup holds, read before anything is imported. */
@Serializable
data class BackupManifest(
  val formatVersion: Int = BackupFormatVersion.CURRENT,
  val kind: String = KIND,
  val appVersion: String = "",
  val exportedAt: Long = 0,
  val searches: Int = 0,
  val jobs: Int = 0,
  val notes: Int = 0,
  val atsReviews: Int = 0,
  val companies: Int = 0,
  val files: Int = 0,
  /**
   * Only the phone's copy, saved while the computer was out of reach: no notes, reviews or
   * insights. It can be added to a computer, never replace one.
   */
  val partial: Boolean = false,
) {
  companion object {
    const val KIND = "full"
  }
}

/** Everything the computer keeps, by its own ids; [sites] maps those ids to board names. */
@Serializable
data class BackupData(
  val sites: List<BackupSite> = emptyList(),
  val links: List<BackupLink> = emptyList(),
  val jobs: List<BackupJob> = emptyList(),
  val notes: List<BackupNote> = emptyList(),
  val atsReviews: List<BackupAtsReview> = emptyList(),
  val companyRatings: List<BackupCompanyRatings> = emptyList(),
  val companySalaries: List<BackupCompanySalary> = emptyList(),
  val aiUsage: List<BackupAiUsage> = emptyList(),
  /** Only in the computer's own exports; a backup the phone downloads leaves them out. */
  val formAnswers: List<BackupFormAnswer> = emptyList(),
)

/** An application form answer; [jobId] is the backup's job id. */
@Serializable
data class BackupFormAnswer(
  val jobId: Long? = null,
  val site: String,
  val url: String,
  val label: String? = null,
  val name: String? = null,
  val elementId: String? = null,
  val placeholder: String? = null,
  val ariaLabel: String? = null,
  val inputType: String,
  val questionKey: String,
  val value: String,
  val createdAt: Long,
  val updatedAt: Long,
)

@Serializable data class BackupSite(val id: Long, val name: String)

@Serializable
data class BackupLink(
  val id: Long,
  val url: String,
  val title: String,
  val siteId: Long,
  val createdAt: Long,
  val scrapeFailureCount: Int = 0,
  val lastScrapedAt: Long,
)

/** A job as the computer stores it; [status] and [labels] are wire names. */
@Serializable
data class BackupJob(
  val id: Long,
  val externalId: String,
  val externalUrl: String,
  val siteId: Long,
  val linkId: Long? = null,
  val title: String,
  val companyName: String,
  val companyLogo: String? = null,
  val location: String? = null,
  val salary: String? = null,
  val jobType: String? = null,
  val tags: List<String> = emptyList(),
  val description: String? = null,
  val status: String,
  val labels: List<String> = emptyList(),
  val excludeReason: String? = null,
  val createdAt: Long,
  val updatedAt: Long,
) {
  /** As the app shows it, with [id] and [siteId] given. */
  fun toJob(id: Long = this.id, siteId: Long = this.siteId, linkId: Long? = this.linkId): Job =
    Job(
      id = id,
      externalId = externalId,
      externalUrl = externalUrl,
      siteId = siteId,
      linkId = linkId,
      title = title,
      companyName = companyName,
      companyLogo = companyLogo,
      location = location,
      salary = salary,
      workMode = WorkMode.fromWireOrNull(jobType),
      tags = tags,
      description = description,
      status = JobStatus.entries.firstOrNull { it.wire == status } ?: JobStatus.NEW,
      labels = labels.mapNotNull(JobLabel::fromWireOrNull),
      excludeReason = excludeReason,
      createdAt = createdAt,
      updatedAt = updatedAt,
    )
}

/** [this] as a backup keeps it. */
fun Job.toBackup(): BackupJob =
  BackupJob(
    id = id,
    externalId = externalId,
    externalUrl = externalUrl,
    siteId = siteId,
    linkId = linkId,
    title = title,
    companyName = companyName,
    companyLogo = companyLogo,
    location = location,
    salary = salary,
    jobType = workMode?.wire,
    tags = tags,
    description = description,
    status = status.wire,
    labels = labels.map { it.wire },
    excludeReason = excludeReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

/** [this] as a backup keeps it. */
fun Link.toBackup(): BackupLink =
  BackupLink(id, url, title, siteId, createdAt, scrapeFailureCount, lastScrapedAt)

/** A note; [files] are paths inside the backup's `files/` folder. */
@Serializable
data class BackupNote(
  val id: Long,
  val jobId: Long,
  val text: String,
  val files: List<String> = emptyList(),
  val createdAt: Long,
)

@Serializable
data class BackupAtsReview(
  val jobId: Long,
  val resumeHash: String,
  val overall: Double,
  val atsMatch: Int,
  val shortlisted: Boolean,
  val failed: Boolean,
  val review: String? = null,
  val createdAt: Long,
)

@Serializable
data class BackupCompanyRatings(
  val companyKey: String,
  val ratings: String,
  val ambitionBoxSlug: String? = null,
  val failed: Boolean = false,
  val fetchedAt: Long,
)

@Serializable
data class BackupCompanySalary(
  val companyKey: String,
  val titleKey: String,
  val salary: String? = null,
  val fetchedAt: Long,
)

@Serializable
data class BackupAiUsage(
  val date: String,
  val inputTokens: Long,
  val outputTokens: Long,
  val updatedAt: Long,
)

/** How a full backup goes into a database already in use. */
@Serializable
enum class ImportMode {
  /** Adds what is missing: searches by URL, jobs by their id on the job board. */
  @SerialName("merge") MERGE,
  /** Replaces every search and job; the current ones are saved to a backup file first. */
  @SerialName("replace") REPLACE,
}

/** What an import changed. */
@Serializable
data class ImportSummary(
  val searchesAdded: Int = 0,
  val jobsAdded: Int = 0,
  val notesAdded: Int = 0,
  val settings: List<SettingsPart> = emptyList(),
  val replaced: Boolean = false,
  /** Where the data from before a replace was saved. */
  val savedBefore: String? = null,
  /** The phone kept the backup and sends it once the computer is back. */
  val waitingForComputer: Boolean = false,
)

/**
 * An online API key with its secret, as a password-locked export from the computer carries it
 * (never a plain backup, never the phone). [toString] leaves the secret out, so it never reaches a
 * log.
 */
@Serializable
data class BackupApiKey(val key: AiKey, val secret: String) {
  override fun toString(): String = "BackupApiKey(key=$key, secret=***)"
}
