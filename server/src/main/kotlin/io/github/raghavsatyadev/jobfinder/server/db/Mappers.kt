package io.github.raghavsatyadev.jobfinder.server.db

import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import java.security.SecureRandom
import java.util.Base64

fun JobEntity.toModel(): Job =
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
    status = JobStatus.fromWire(status),
    labels = labels.mapNotNull(JobLabel::fromWireOrNull),
    excludeReason = excludeReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

fun JobListRow.toModel(): Job =
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
    description = null,
    status = JobStatus.fromWire(status),
    labels = labels.mapNotNull(JobLabel::fromWireOrNull),
    excludeReason = excludeReason,
    ats =
      if (atsOverall != null && atsMatch != null)
        AtsSummary(atsOverall, atsMatch, atsShortlisted == true)
      else null,
    createdAt = createdAt,
    updatedAt = updatedAt,
  )

fun LinkEntity.toModel(): Link =
  Link(
    id = id,
    url = url,
    title = title,
    siteId = siteId,
    createdAt = createdAt,
    scrapeFailureCount = scrapeFailureCount,
    lastScrapedAt = lastScrapedAt,
  )

fun SiteEntity.toModel(): Site =
  Site(
    id = id,
    name = name,
    provider = SiteProvider.fromWire(provider),
    urls = urls,
    queryParamsToRemove = queryParamsToRemove,
    blacklistedPaths = blacklistedPaths,
    logoKey = logoKey,
    deprecated = deprecated,
    incognitoSupport = incognitoSupport,
    createdAt = createdAt,
  )

fun NoteEntity.toModel(): Note =
  Note(id = id, jobId = jobId, text = text, files = files, createdAt = createdAt)

fun AdvancedMatchingEntity.toModel(): AdvancedMatchingConfig =
  AdvancedMatchingConfig(prompt = prompt, blacklistedCompanies = blacklistedCompanies)

fun SettingsEntity.toScannerSettings(): ScannerSettings =
  ScannerSettings(
    cronRule = ScannerSettings.limitFrequency(cronRule),
    preventSleep = preventSleep,
    useSound = useSound,
    inAppBrowserEnabled = inAppBrowserEnabled,
    scraperConcurrency = scraperConcurrency,
    excludedKeepDays = excludedKeepDays,
    aiPaused = aiPaused,
  )

fun SettingsEntity.toAiSettings(): AiSettings =
  AiSettings(
    provider =
      AiProvider.entries.firstOrNull { it.name.equals(aiProvider, ignoreCase = true) }
        ?: AiProvider.OLLAMA,
    model = aiModel,
    baseUrl = aiBaseUrl,
  )

/** [settings] in this row; `nimble` and blank models are refused. */
fun SettingsEntity.withAi(settings: AiSettings): SettingsEntity {
  require(settings.model.isNotBlank()) { "The model name is blank" }
  require(!settings.model.trim().lowercase().startsWith("nimble")) {
    "This model is reserved and can't be used by the app"
  }
  require(settings.endpoint.isNotBlank()) { "The address of the AI server is blank" }
  return copy(
    aiProvider = settings.provider.name.lowercase(),
    aiModel = settings.model.trim(),
    aiBaseUrl = settings.baseUrl?.trim()?.takeIf(String::isNotEmpty),
  )
}

fun SettingsEntity.toServerSettings(): ServerSettings =
  ServerSettings(port = serverPort, networkInterface = networkInterface)

/** Settings rows and pairing tokens. */
object SettingsDefaults {
  private val random = SecureRandom()

  /** 256 random bits, base64url without padding. */
  fun newPairingToken(): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  /** A settings row with a fresh pairing token. */
  fun newRow(
    scanner: ScannerSettings = ScannerSettings(),
    server: ServerSettings = ServerSettings(),
  ): SettingsEntity =
    SettingsEntity(
      cronRule = scanner.cronRule,
      preventSleep = scanner.preventSleep,
      useSound = scanner.useSound,
      inAppBrowserEnabled = scanner.inAppBrowserEnabled,
      serverPort = server.port,
      networkInterface = server.networkInterface,
      pairingToken = newPairingToken(),
      scraperConcurrency = scanner.scraperConcurrency,
    )
}
