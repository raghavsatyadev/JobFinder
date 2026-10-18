@file:OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalLayoutApi::class,
)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_apply
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_apply_method
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_clear
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_labels
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_work_mode
import io.github.raghavsatyadev.jobfinder.shared.model.ApplyMethod
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/** The filter sheet over the job feed. Changes apply when the user taps "Show jobs". */
@Composable
internal fun JobFilterSheet(
  filter: JobFilter,
  sites: ImmutableList<Site>,
  links: ImmutableList<Link>,
  onApply: (JobFilter) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    JobFilterSheetBody(
      filter = filter,
      sites = sites,
      links = links,
      onApply = {
        onApply(it)
        onDismiss()
      },
    )
  }
}

/**
 * The body of the filter sheet, for screenshot tests, which show it without a window (a modal sheet
 * needs one).
 */
@Composable
fun JobFilterSheetContent(
  filter: JobFilter,
  sites: List<Site>,
  links: List<Link>,
  onApply: (JobFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  val siteList = remember(sites) { sites.toImmutableList() }
  val linkList = remember(links) { links.toImmutableList() }
  JobFilterSheetBody(filter, siteList, linkList, onApply, modifier)
}

/**
 * The body of the filter sheet: job boards, searches, labels, work mode and apply method as
 * multi-select chips.
 */
@Composable
internal fun JobFilterSheetBody(
  filter: JobFilter,
  sites: ImmutableList<Site>,
  links: ImmutableList<Link>,
  onApply: (JobFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by remember(filter) { mutableStateOf(filter) }
  Column(modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
    Text(
      stringResource(Res.string.jobs_filter_title),
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
    )
    Column(
      Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      SourceFilter(draft, sites, links) { draft = it }
      ChipGroup(stringResource(Res.string.jobs_filter_labels)) {
        JobLabel.entries.forEach { label ->
          Chip(stringResource(label.label), label in draft.labels) {
            draft = draft.copy(labels = draft.labels.toggle(label))
          }
        }
      }
      ChipGroup(stringResource(Res.string.jobs_filter_work_mode)) {
        WorkMode.entries.forEach { mode ->
          Chip(stringResource(mode.label), mode in draft.workModes) {
            draft = draft.copy(workModes = draft.workModes.toggle(mode))
          }
        }
      }
      ChipGroup(stringResource(Res.string.jobs_filter_apply_method)) {
        ApplyMethod.entries.forEach { method ->
          Chip(stringResource(method.label), method in draft.applyMethods) {
            draft = draft.copy(applyMethods = draft.applyMethods.toggle(method))
          }
        }
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(top = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedButton(
        onClick = { draft = JobFilter() },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
      ) {
        Text(stringResource(Res.string.jobs_filter_clear))
      }
      Button(
        onClick = { onApply(draft) },
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
      ) {
        Text(stringResource(Res.string.jobs_filter_apply))
      }
    }
  }
}

internal fun <T> List<T>.toggle(item: T): List<T> = if (item in this) this - item else this + item

@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
  Column {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
  }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text) },
    leadingIcon =
      if (selected) {
        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
      } else null,
    colors = FilterChipDefaults.filterChipColors(),
  )
}
