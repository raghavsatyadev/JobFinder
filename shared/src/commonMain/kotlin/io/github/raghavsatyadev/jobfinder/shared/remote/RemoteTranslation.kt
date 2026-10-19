package io.github.raghavsatyadev.jobfinder.shared.remote

import io.github.raghavsatyadev.jobfinder.shared.model.TranslationStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.TranslationRepository

/** Job translations over `/v1/jobs/{id}/translation`. */
class RemoteTranslationRepository(private val api: RemoteApi) : TranslationRepository {
  override suspend fun requestTranslation(jobId: Long, retry: Boolean): TranslationStatus =
    api.post(
      "/jobs/$jobId/translation" + if (retry) "?retry=true" else "",
      TranslationStatus.serializer(),
    )
}
