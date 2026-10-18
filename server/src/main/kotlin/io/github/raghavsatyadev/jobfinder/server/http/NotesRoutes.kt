package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.CreateNoteRequest
import io.github.raghavsatyadev.jobfinder.shared.api.UpdateNoteRequest
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `GET|POST /jobs/{id}/notes`, `PUT|DELETE /notes/{id}`, `POST /notes/{id}/files` (multipart, the
 * first file part is stored) and `GET /notes/{id}/files/{index}`.
 */
fun Route.notesRoutes(deps: ApiDependencies) {
  val notes = deps.notes
  route("/jobs/{id}/notes") {
    get {
      val jobId = call.idParam()
      deps.jobs.getJob(jobId) ?: notFound("Job")
      call.respondOk(notes.listNotes(jobId))
    }
    post {
      val jobId = call.idParam()
      val body = call.receive<CreateNoteRequest>()
      deps.jobs.getJob(jobId) ?: notFound("Job")
      call.respondOk(notes.createNote(jobId, body.text))
    }
  }
  route("/notes/{id}") {
    put {
      val id = call.idParam()
      val body = call.receive<UpdateNoteRequest>()
      call.respondOk(notes.updateNote(id, body.text) ?: notFound("Note"))
    }
    delete {
      val id = call.idParam()
      notes.getNote(id) ?: notFound("Note")
      notes.deleteNote(id)
      withContext(Dispatchers.IO) { deps.noteFiles.deleteAll(id) }
      call.respondOk(Unit)
    }
    post("/files") {
      val id = call.idParam()
      notes.getNote(id) ?: notFound("Note")
      var stored: String? = null
      call.receiveMultipart().forEachPart { part ->
        try {
          if (part is PartData.FileItem && stored == null) {
            val name = part.originalFileName ?: badRequest("The file has no name.")
            stored =
              withContext(Dispatchers.IO) {
                part.provider().toInputStream().use { deps.noteFiles.store(id, name, it) }
              }
          }
        } finally {
          part.dispose()
        }
      }
      val path = stored ?: badRequest("Attach one file.")
      call.respondOk(notes.addFileToNote(id, path) ?: notFound("Note"))
    }
    get("/files/{index}") {
      val note = notes.getNote(call.idParam()) ?: notFound("Note")
      val index = call.parameters["index"]?.toIntOrNull() ?: badRequest("Invalid index.")
      val stored = note.files.getOrNull(index) ?: notFound("File")
      val file = deps.noteFiles.resolve(stored) ?: notFound("File")
      call.response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(
            ContentDisposition.Parameters.FileName,
            file.fileName.toString(),
          )
          .toString(),
      )
      call.respondFile(file.toFile())
    }
  }
}
