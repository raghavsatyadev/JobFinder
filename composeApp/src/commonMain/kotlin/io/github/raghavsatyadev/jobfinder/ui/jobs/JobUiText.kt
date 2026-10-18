package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.runtime.Composable
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.allDrawableResources
import io.github.raghavsatyadev.jobfinder.resources.jobs_apply_easy
import io.github.raghavsatyadev.jobfinder.resources.jobs_apply_external
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_considering
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_ghosted
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_interviewing
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_offer
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_rejected
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_starred
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_submitted
import io.github.raghavsatyadev.jobfinder.resources.jobs_time_days
import io.github.raghavsatyadev.jobfinder.resources.jobs_time_hours
import io.github.raghavsatyadev.jobfinder.resources.jobs_time_minutes
import io.github.raghavsatyadev.jobfinder.resources.jobs_time_now
import io.github.raghavsatyadev.jobfinder.resources.jobs_work_mode_hybrid
import io.github.raghavsatyadev.jobfinder.resources.jobs_work_mode_onsite
import io.github.raghavsatyadev.jobfinder.resources.jobs_work_mode_remote
import io.github.raghavsatyadev.jobfinder.shared.model.ApplyMethod
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal val WorkMode.label: StringResource
  get() =
    when (this) {
      WorkMode.REMOTE -> Res.string.jobs_work_mode_remote
      WorkMode.HYBRID -> Res.string.jobs_work_mode_hybrid
      WorkMode.ONSITE -> Res.string.jobs_work_mode_onsite
    }

internal val ApplyMethod.label: StringResource
  get() =
    when (this) {
      ApplyMethod.EASY_APPLY -> Res.string.jobs_apply_easy
      ApplyMethod.EXTERNAL -> Res.string.jobs_apply_external
    }

internal val JobLabel.label: StringResource
  get() =
    when (this) {
      JobLabel.CONSIDERING -> Res.string.jobs_label_considering
      JobLabel.SUBMITTED -> Res.string.jobs_label_submitted
      JobLabel.INTERVIEWING -> Res.string.jobs_label_interviewing
      JobLabel.OFFER -> Res.string.jobs_label_offer
      JobLabel.REJECTED -> Res.string.jobs_label_rejected
      JobLabel.GHOSTED -> Res.string.jobs_label_ghosted
      JobLabel.STARRED -> Res.string.jobs_label_starred
    }

/** Labels on the way to an offer are positive, a rejection or silence is negative. */
internal val JobLabel.tone: StatusTone
  get() =
    when (this) {
      JobLabel.CONSIDERING -> StatusTone.Muted
      JobLabel.SUBMITTED -> StatusTone.Active
      JobLabel.INTERVIEWING -> StatusTone.Neutral
      JobLabel.OFFER,
      JobLabel.STARRED -> StatusTone.Positive
      JobLabel.REJECTED,
      JobLabel.GHOSTED -> StatusTone.Negative
    }

/** The bundled logo of a job board, `drawable/site_<logoKey>`, or `null`. */
internal fun siteLogo(logoKey: String?): DrawableResource? = logoKey?.let {
  Res.allDrawableResources["site_${it.lowercase()}"]
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** "5 minutes ago", "3 days ago". */
@Composable
internal fun relativeTime(at: Long, now: Long): String {
  val elapsed = (now - at).coerceAtLeast(0)
  return when {
    elapsed < MINUTE -> stringResource(Res.string.jobs_time_now)
    elapsed < HOUR ->
      (elapsed / MINUTE).toInt().let { pluralStringResource(Res.plurals.jobs_time_minutes, it, it) }
    elapsed < DAY ->
      (elapsed / HOUR).toInt().let { pluralStringResource(Res.plurals.jobs_time_hours, it, it) }
    else -> (elapsed / DAY).toInt().let { pluralStringResource(Res.plurals.jobs_time_days, it, it) }
  }
}
