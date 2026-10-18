package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.repository.LocalNotesRepository
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.Scanner
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.JobsCsvExporter
import io.github.raghavsatyadev.jobfinder.server.service.LinkService
import io.github.raghavsatyadev.jobfinder.server.service.NoteFiles
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.BaseResponse
import io.github.raghavsatyadev.jobfinder.shared.api.HealthResponse
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobReportRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/** What the API routes work on. The desktop builds it from Koin; tests build it by hand. */
class ApiDependencies(
  val jobs: JobsRepository,
  val links: LinksRepository,
  val sites: SitesRepository,
  val notes: LocalNotesRepository,
  val matching: MatchingRepository,
  val settings: LocalSettingsRepository,
  val linkService: LinkService,
  val scanner: Scanner,
  val events: ServerEvents,
  val csv: JobsCsvExporter,
  val noteFiles: NoteFiles,
  val identity: ServerIdentity = ServerIdentity(TEST_ID, "test"),
  val pairingRequests: PairingRequests = PairingRequests(token = { settings.pairingToken() }),
  val phones: PairedPhones = PairedPhones(),
  /** `null` answers the resume and ATS routes with "unavailable". */
  val ats: AtsRepository? = null,
  val insights: InsightsRepository? = null,
  /** `null` answers the AI instruction routes with "unavailable". */
  val prompts: AiPromptsRepository? = null,
  val ghost: GhostRepository? = null,
  val tips: TipsRepository? = null,
  val improve: ResumeImproveRepository? = null,
  /** `null` answers the backup routes with "unavailable". */
  val backup: BackupRepository? = null,
  /** `null` answers job reports with "unavailable". */
  val reports: JobReportRepository? = null,
  /** A phone's request to replace everything waits here for the user's answer. */
  val restoreRequests: RestoreRequests = RestoreRequests(),
)

private const val TEST_ID = "00000000-0000-0000-0000-000000000000"

private val log = LoggerFactory.getLogger("jobfinder.api")

/**
 * The embedded API: `/health` and pairing requests are open, everything under `/v1` needs the
 * pairing token. Errors are answered with [BaseResponse]; only [ApiException] messages reach the
 * client, anything else is logged and gets a generic message. No CORS: only the paired app calls
 * it.
 */
fun Application.jobFinderApi(deps: ApiDependencies) {
  install(ContentNegotiation) { json(ApiJson) }
  install(CallLogging) {
    level = Level.INFO
    disableDefaultColors()
    // Method, path (no query string) and status only: no bodies, no headers, no tokens.
    format { call ->
      "${call.response.status()?.value} ${call.request.httpMethod.value} ${call.request.path()}"
    }
  }
  install(StatusPages) {
    exception<ApiException> { call, e ->
      call.respondError(e.code, e.message ?: e.code.defaultMessage)
    }
    exception<BadRequestException> { call, e ->
      log.info(
        "Bad request {} {}: {}",
        call.request.httpMethod.value,
        call.request.path(),
        e.message,
      )
      call.respondError(ApiErrorCode.BAD_REQUEST)
    }
    exception<IllegalArgumentException> { call, e ->
      log.info(
        "Invalid request {} {}: {}",
        call.request.httpMethod.value,
        call.request.path(),
        e.message,
      )
      call.respondError(ApiErrorCode.BAD_REQUEST)
    }
    exception<NotFoundException> { call, _ -> call.respondError(ApiErrorCode.NOT_FOUND) }
    exception<Throwable> { call, e ->
      log.error("Request failed: {} {}", call.request.httpMethod.value, call.request.path(), e)
      call.respondError(ApiErrorCode.INTERNAL)
    }
    status(HttpStatusCode.NotFound) { call, _ -> call.respondError(ApiErrorCode.NOT_FOUND) }
  }
  install(SSE)
  install(Authentication) { pairing({ deps.settings.pairingToken() }, deps.phones::seen) }

  routing {
    get("/health") {
      call.respond(HealthResponse(id = deps.identity.id, name = deps.identity.name))
    }
    pairingRoutes(deps.pairingRequests)
    authenticate(PairingAuthProvider.NAME) {
      route("/v1") {
        jobsRoutes(deps)
        csvRoutes(deps)
        linksRoutes(deps)
        sitesRoutes(deps)
        notesRoutes(deps)
        matchingRoutes(deps)
        promptRoutes(deps)
        reportRoutes(deps)
        ghostRoutes(deps)
        tipsRoutes(deps)
        improveRoutes(deps)
        settingsRoutes(deps)
        scanRoutes(deps)
        eventsRoutes(deps)
        atsRoutes(deps)
        insightsRoutes(deps)
        backupRoutes(deps)
      }
    }
  }
}

internal suspend fun ApplicationCall.respondError(
  code: ApiErrorCode,
  message: String = code.defaultMessage,
) {
  respond(HttpStatusCode.fromValue(code.httpStatus), BaseResponse.error(code, message))
}

internal suspend inline fun <reified T> ApplicationCall.respondOk(data: T) {
  respond(BaseResponse.ok(data))
}
