@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FiberNew
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.graphics.shapes.RoundedPolygon
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.status_applied
import io.github.raghavsatyadev.jobfinder.resources.status_archived
import io.github.raghavsatyadev.jobfinder.resources.status_deleted
import io.github.raghavsatyadev.jobfinder.resources.status_excluded
import io.github.raghavsatyadev.jobfinder.resources.status_new
import io.github.raghavsatyadev.jobfinder.resources.status_processing
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource

/** The status tabs of the job feed, in order. */
val JobTabs =
  persistentListOf(
    JobStatus.NEW,
    JobStatus.APPLIED,
    JobStatus.ARCHIVED,
    JobStatus.EXCLUDED_BY_ADVANCED_MATCHING,
  )

/** The label of a status. */
val JobStatus.label: StringResource
  get() =
    when (this) {
      JobStatus.NEW -> Res.string.status_new
      JobStatus.APPLIED -> Res.string.status_applied
      JobStatus.ARCHIVED -> Res.string.status_archived
      JobStatus.EXCLUDED_BY_ADVANCED_MATCHING -> Res.string.status_excluded
      JobStatus.PROCESSING -> Res.string.status_processing
      JobStatus.DELETED -> Res.string.status_deleted
    }

/**
 * The colour role of a status: new is the main colour, applied the complementary accent, archived
 * and deleted step back, and jobs the AI filtered out use the error role.
 */
val JobStatus.tone: StatusTone
  get() =
    when (this) {
      JobStatus.NEW -> StatusTone.Positive
      JobStatus.APPLIED -> StatusTone.Neutral
      JobStatus.ARCHIVED,
      JobStatus.DELETED -> StatusTone.Muted
      JobStatus.EXCLUDED_BY_ADVANCED_MATCHING -> StatusTone.Negative
      JobStatus.PROCESSING -> StatusTone.Active
    }

/** Each status has its own shape as well as its own colour, so it never relies on colour alone. */
val JobStatus.shape: RoundedPolygon
  get() =
    when (this) {
      JobStatus.NEW -> MaterialShapes.Sunny
      JobStatus.APPLIED -> MaterialShapes.Cookie9Sided
      JobStatus.ARCHIVED -> MaterialShapes.Square
      JobStatus.EXCLUDED_BY_ADVANCED_MATCHING -> MaterialShapes.SoftBurst
      JobStatus.PROCESSING -> MaterialShapes.Circle
      JobStatus.DELETED -> MaterialShapes.Slanted
    }

val JobStatus.icon: ImageVector
  get() =
    when (this) {
      JobStatus.NEW -> Icons.Rounded.FiberNew
      JobStatus.APPLIED -> Icons.Rounded.Send
      JobStatus.ARCHIVED -> Icons.Rounded.Archive
      JobStatus.EXCLUDED_BY_ADVANCED_MATCHING -> Icons.Rounded.FilterAlt
      JobStatus.PROCESSING -> Icons.Rounded.HourglassTop
      JobStatus.DELETED -> Icons.Rounded.Delete
    }
