package io.github.raghavsatyadev.jobfinder.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.raghavsatyadev.jobfinder.shared.model.FormAnswer
import io.github.raghavsatyadev.jobfinder.ui.settings.FormAnswersContent
import io.github.raghavsatyadev.jobfinder.ui.settings.FormAnswersUiState

/** Settings → Form answers with answers from two forms, one of them for a saved job. */
@PreviewTest
@ScreenPreviews
@Composable
fun FormAnswersListScreenshot() {
  Answers(FormAnswersUiState(answers = SAMPLE))
}

/** Nothing saved yet: how answers get here. */
@PreviewTest
@StatePreviews
@Composable
fun FormAnswersEmptyScreenshot() {
  Answers(FormAnswersUiState(answers = emptyList()))
}

/** A search that finds nothing, and a delete that failed. */
@PreviewTest
@StatePreviews
@Composable
fun FormAnswersSearchScreenshot() {
  Answers(FormAnswersUiState(answers = SAMPLE, query = "visa", changeFailed = true))
}

/** Editing an answer. */
@PreviewTest
@StatePreviews
@Composable
fun FormAnswersEditScreenshot() {
  val answer = SAMPLE[3]
  Answers(
    FormAnswersUiState(answers = SAMPLE, editing = answer, draft = "60 days, can be bought out")
  )
}

/** Asking before a delete. */
@PreviewTest
@StatePreviews
@Composable
fun FormAnswersDeleteScreenshot() {
  Answers(FormAnswersUiState(answers = SAMPLE, confirmDelete = SAMPLE[0]))
}

@Composable
private fun Answers(state: FormAnswersUiState) {
  ScreenshotHost {
    Column(Modifier.verticalScroll(rememberScrollState())) {
      FormAnswersContent(state, onEvent = {})
    }
  }
}

private fun answer(
  id: Long,
  label: String,
  key: String,
  value: String,
  site: String = "boards.greenhouse.io",
  job: Pair<String, String>? = "Senior Android Engineer" to "Acme Robotics",
) =
  FormAnswer(
    id = id,
    jobId = job?.let { 7L },
    site = site,
    url = "https://$site/acme/jobs/123",
    label = label,
    inputType = "text",
    questionKey = key,
    value = value,
    createdAt = NOW,
    updatedAt = NOW,
    jobTitle = job?.first,
    companyName = job?.second,
  )

private val SAMPLE =
  listOf(
    answer(1, "First Name", "first_name", "Raghav"),
    answer(2, "Email", "email", "raghav@example.com"),
    answer(3, "LinkedIn Profile", "linkedin", "https://www.linkedin.com/in/example"),
    answer(4, "Notice period (days)", "notice_period", "60", site = "naukri.com", job = null),
    answer(
      5,
      "Why do you want to work at Acme Robotics?",
      "why do you want to work at acme robotics",
      "I have built offline-first Android apps for nine years and Acme's field robots need " +
        "exactly that: apps that keep working with no signal and sync cleanly later.",
    ),
    answer(6, "Expected CTC (LPA)", "expected_ctc", "38", site = "naukri.com", job = null),
  )

private const val NOW = 1_792_000_000_000L
