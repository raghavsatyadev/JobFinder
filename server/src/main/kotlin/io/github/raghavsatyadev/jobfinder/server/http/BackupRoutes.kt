package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.BackupService
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFiles
import io.github.raghavsatyadev.jobfinder.shared.backup.BackupFormatException
import io.github.raghavsatyadev.jobfinder.shared.model.ImportMode
import io.github.raghavsatyadev.jobfinder.shared.model.SettingsBackup
import io.github.raghavsatyadev.jobfinder.shared.remote.BackupParams
import io.github.raghavsatyadev.jobfinder.shared.repository.BackupRepository
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `GET|POST /backup/settings?parts=searches,matching,…` (a [SettingsBackup] as JSON) and `GET|POST
 * /backup/full` (a zip; `?mode=merge|replace` on import; a replace waits for [RestoreRequests]).
 */
fun Route.backupRoutes(deps: ApiDependencies) {
  fun backup(): BackupRepository = deps.backup ?: throw ApiException(ApiErrorCode.UNAVAILABLE)
  route("/backup") {
    get("/settings") {
      call.respondOk(
        backup().exportSettings(parts(call.request.queryParameters[BackupParams.PARTS]))
      )
    }
    post("/settings") {
      val body = call.receive<SettingsBackup>()
      val parts = parts(call.request.queryParameters[BackupParams.PARTS])
      call.respondOk(backup().importSettings(body, parts))
    }
    get("/full") {
      // Only a phone calls this: application form answers stay on the computer.
      val bytes = (backup() as? BackupService)?.exportFullForPhone() ?: backup().exportFull()
      call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName,
            "jobfinder-backup.zip",
          )
          .toString(),
      )
      call.respondBytes(bytes, ContentType.Application.Zip)
    }
    post("/full") {
      val mode =
        BackupParams.parseMode(call.request.queryParameters[BackupParams.MODE])
          ?: badRequest("mode must be merge or replace.")
      val bytes =
        withContext(Dispatchers.IO) {
          call.receiveChannel().toInputStream().use { it.readNBytes(MAX_UPLOAD + 1) }
        }
      if (bytes.size > MAX_UPLOAD) {
        throw ApiException(ApiErrorCode.PAYLOAD_TOO_LARGE, "The backup must be 512 MB or less.")
      }
      val summary =
        try {
          // Only a phone calls this; replacing everything needs the user's yes on the computer.
          if (mode == ImportMode.REPLACE) {
            val manifest = BackupFiles.readManifest(bytes)
            BackupService.checkReplace(manifest)
            if (!deps.restoreRequests.ask(manifest)) throw ApiException(ApiErrorCode.DENIED)
          }
          backup().importFull(bytes, mode)
        } catch (e: BackupFormatException) {
          BackupService.badFile(e)
        }
      call.respondOk(summary)
    }
  }
}

private fun parts(value: String?) =
  BackupParams.parseParts(value).ifEmpty { badRequest("Name the parts to export or import.") }

private const val MAX_UPLOAD = 512 * 1024 * 1024
