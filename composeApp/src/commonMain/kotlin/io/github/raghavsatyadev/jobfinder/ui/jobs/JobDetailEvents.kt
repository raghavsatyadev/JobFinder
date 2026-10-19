package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.runtime.Immutable
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobReportResult
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus

/** The "Shouldn't be in New" dialog: the note, then the AI's answer. */
@Immutable
data class ReportUi(
  val note: String = "",
  val checking: Boolean = false,
  val result: JobReportResult? = null,
  val failed: Boolean = false,
)

sealed interface JobDetailEvent {
  data object Retry : JobDetailEvent

  data class ChangeStatus(val to: JobStatus) : JobDetailEvent

  /** The old app allows one label per job; `null` clears it. */
  data class SetLabel(val label: JobLabel?) : JobDetailEvent

  data object ToggleStar : JobDetailEvent

  data object RefreshDescription : JobDetailEvent

  /** Ask for the ATS review again (after a failure). */
  data object RetryAts : JobDetailEvent

  /** Read the company's ratings and salary again (after a failure). */
  data object RetryInsights : JobDetailEvent

  /** Read the company's careers site again (after a failure). */
  data object RetryGhost : JobDetailEvent

  /** Ask the AI for negotiation tips (first time, or new ones). */
  data object WriteTips : JobDetailEvent

  /** Shows the job as posted, or its translation again. */
  data object ToggleOriginal : JobDetailEvent

  /** Ask for the translation again (after a failure). */
  data object RetryTranslation : JobDetailEvent

  data class AddNote(val text: String) : JobDetailEvent

  data class EditNote(val id: Long, val text: String) : JobDetailEvent

  data class DeleteNote(val id: Long) : JobDetailEvent

  /** Opens "Shouldn't be in New". */
  data object OpenReport : JobDetailEvent

  data class SetReportNote(val note: String) : JobDetailEvent

  /** Asks the AI to check the job again. */
  data object SendReport : JobDetailEvent

  data object CloseReport : JobDetailEvent

  /** The AI thinks the job fits; the user moves it to Filtered out anyway. */
  data object MoveAnyway : JobDetailEvent
}

sealed interface JobDetailEffect {
  data class Message(val message: JobsMessage) : JobDetailEffect

  /** The job was deleted: leave the detail. */
  data object Close : JobDetailEffect
}
