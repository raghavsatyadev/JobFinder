@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_ats
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_ats_latest_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_latest
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_onsite
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_remote
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The sort choices as the menu shows them. "Latest" and "ATS score" tick on and off and combine;
 * "Remote first" and "Onsite first" stand alone and sort latest first inside.
 */
internal object SortChoices {
  fun latestOn(sort: JobSort) = sort != JobSort.ATS

  fun atsOn(sort: JobSort) = sort.usesAts

  fun toggleLatest(sort: JobSort): JobSort =
    when (sort) {
      JobSort.ATS -> JobSort.ATS_LATEST
      JobSort.ATS_LATEST -> JobSort.ATS
      else -> JobSort.LATEST
    }

  /** With Latest ticked, ATS joins it in bands; otherwise ATS sorts alone. */
  fun toggleAts(sort: JobSort): JobSort =
    when (sort) {
      JobSort.LATEST -> JobSort.ATS_LATEST
      JobSort.ATS,
      JobSort.ATS_LATEST -> JobSort.LATEST
      JobSort.REMOTE_FIRST,
      JobSort.ONSITE_FIRST -> JobSort.ATS
    }

  fun toggleMode(sort: JobSort, mode: JobSort): JobSort = if (sort == mode) JobSort.LATEST else mode
}

/** The sort button by the filters, badged while the order is not plain latest first. */
@Composable
internal fun SortMenu(sort: JobSort, onPick: (JobSort) -> Unit) {
  var open by remember { mutableStateOf(false) }
  val label = stringResource(Res.string.jobs_sort)
  Box {
    IconButton(
      onClick = { open = true },
      shapes = IconButtonDefaults.shapes(),
      modifier = Modifier.semantics { contentDescription = label },
    ) {
      BadgedBox(badge = { if (sort != JobSort.LATEST) Badge() }) {
        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null)
      }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      SortItem(Res.string.jobs_sort_latest, SortChoices.latestOn(sort), check = true) {
        onPick(SortChoices.toggleLatest(sort))
      }
      SortItem(Res.string.jobs_sort_ats, SortChoices.atsOn(sort), check = true) {
        onPick(SortChoices.toggleAts(sort))
      }
      if (sort == JobSort.ATS_LATEST) {
        Text(
          stringResource(Res.string.jobs_sort_ats_latest_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.widthIn(max = 280.dp).padding(horizontal = 16.dp, vertical = 4.dp),
        )
      }
      HorizontalDivider()
      SortItem(Res.string.jobs_sort_remote, sort == JobSort.REMOTE_FIRST, check = false) {
        onPick(SortChoices.toggleMode(sort, JobSort.REMOTE_FIRST))
      }
      SortItem(Res.string.jobs_sort_onsite, sort == JobSort.ONSITE_FIRST, check = false) {
        onPick(SortChoices.toggleMode(sort, JobSort.ONSITE_FIRST))
      }
    }
  }
}

@Composable
private fun SortItem(text: StringResource, on: Boolean, check: Boolean, onClick: () -> Unit) {
  val icon: ImageVector =
    when {
      check && on -> Icons.Rounded.CheckBox
      check -> Icons.Rounded.CheckBoxOutlineBlank
      on -> Icons.Rounded.RadioButtonChecked
      else -> Icons.Rounded.RadioButtonUnchecked
    }
  DropdownMenuItem(
    text = { Text(stringResource(text)) },
    leadingIcon = {
      Icon(
        icon,
        contentDescription = null,
        tint =
          if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    onClick = onClick,
  )
}
