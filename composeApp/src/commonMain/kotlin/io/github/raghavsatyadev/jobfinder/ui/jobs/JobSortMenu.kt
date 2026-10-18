@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_ats
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_ats_lowest
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_latest
import io.github.raghavsatyadev.jobfinder.resources.jobs_sort_oldest
import io.github.raghavsatyadev.jobfinder.shared.model.JobSort
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The menu's choices, in order, with their labels. */
private val SORTS =
  listOf(
    JobSort.LATEST to Res.string.jobs_sort_latest,
    JobSort.OLDEST to Res.string.jobs_sort_oldest,
    JobSort.ATS to Res.string.jobs_sort_ats,
    JobSort.ATS_LOWEST to Res.string.jobs_sort_ats_lowest,
  )

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
      SORTS.forEach { (choice, text) ->
        SortItem(text, sort == choice) {
          onPick(choice)
          open = false
        }
      }
    }
  }
}

@Composable
private fun SortItem(text: StringResource, on: Boolean, onClick: () -> Unit) {
  val icon: ImageVector =
    if (on) Icons.Rounded.RadioButtonChecked else Icons.Rounded.RadioButtonUnchecked
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
