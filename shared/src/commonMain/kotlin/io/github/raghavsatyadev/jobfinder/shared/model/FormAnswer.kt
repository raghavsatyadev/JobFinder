package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.Serializable

/**
 * One answer the user gave on a job application form, recorded by the browser extension when the
 * form was sent. Kept only on the computer; never sent to the phone. [questionKey] is the
 * normalised question ("first_name", "expected_ctc", or the cleaned label), what autofill matches
 * on. Passwords, card and bank details and ID numbers are never recorded.
 */
@Serializable
data class FormAnswer(
  val id: Long,
  /** The job the form was for; `null` when the page was not a saved job. */
  val jobId: Long? = null,
  /** The site's host without `www.`, e.g. "boards.greenhouse.io". */
  val site: String,
  val url: String,
  val label: String? = null,
  val name: String? = null,
  val elementId: String? = null,
  val placeholder: String? = null,
  val ariaLabel: String? = null,
  /** The input type: "text", "email", "select", "radio", "checkbox", "textarea", … */
  val inputType: String,
  val questionKey: String,
  val value: String,
  val createdAt: Long,
  val updatedAt: Long,
  /** The job's title and company, for showing; `null` without a job. */
  val jobTitle: String? = null,
  val companyName: String? = null,
) {
  /** What the question said, best first. */
  val question: String
    get() =
      listOfNotNull(label, ariaLabel, placeholder, name, elementId).firstOrNull { it.isNotBlank() }
        ?: questionKey
}
