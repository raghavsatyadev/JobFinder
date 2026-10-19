package io.github.raghavsatyadev.jobfinder.shared.remote

import io.github.raghavsatyadev.jobfinder.shared.model.MakeResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod

/** Resume PDFs over `/v1/resume/pdf` and `/v1/resume/linkedin`. */
class RemoteResumePdfRepository(private val api: RemoteApi) : ResumePdfRepository {
  override suspend fun status(): ResumePdfStatus =
    api.get("/resume/pdf/status", ResumePdfStatus.serializer())

  override suspend fun savePrefs(prefs: ResumePdfPrefs): ResumePdfStatus =
    api.send(
      HttpMethod.Put,
      "/resume/pdf/prefs",
      prefs,
      ResumePdfPrefs.serializer(),
      ResumePdfStatus.serializer(),
    )

  override suspend fun importLinkedIn(fileName: String, bytes: ByteArray): ResumePdfStatus =
    api.call(HttpMethod.Put, "/resume/linkedin", ResumePdfStatus.serializer()) {
      setBody(
        MultiPartFormDataContent(
          formData {
            append(
              "file",
              bytes,
              Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                append(HttpHeaders.ContentDisposition, "filename=\"${fileName.replace("\"", "")}\"")
              },
            )
          }
        )
      )
    }

  override suspend fun deleteLinkedIn(): ResumePdfStatus =
    api.call(HttpMethod.Delete, "/resume/linkedin", ResumePdfStatus.serializer())

  override suspend fun makePdf(request: MakeResumePdf): ResumePdf =
    api.send(
      HttpMethod.Post,
      "/resume/pdf",
      request,
      MakeResumePdf.serializer(),
      ResumePdf.serializer(),
    )
}
