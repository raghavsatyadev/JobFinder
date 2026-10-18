package io.github.raghavsatyadev.jobfinder.shared.repository

import io.github.raghavsatyadev.jobfinder.shared.model.FormAnswer

/**
 * The application form answers the browser extension recorded. Only the desktop binds it: the
 * answers stay on the computer and never reach the phone.
 */
interface FormAnswersRepository {
  /** Every saved answer, newest first. */
  suspend fun listAnswers(): List<FormAnswer>

  /** Changes the answer [id] to [value]; returns it as saved. */
  suspend fun updateAnswer(id: Long, value: String): FormAnswer

  suspend fun deleteAnswer(id: Long)
}
