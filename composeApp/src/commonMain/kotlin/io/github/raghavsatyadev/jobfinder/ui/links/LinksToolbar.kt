@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.links_filter_boards
import io.github.raghavsatyadev.jobfinder.resources.links_filter_done
import io.github.raghavsatyadev.jobfinder.resources.links_filter_status
import io.github.raghavsatyadev.jobfinder.resources.links_filters
import io.github.raghavsatyadev.jobfinder.resources.links_filters_active
import io.github.raghavsatyadev.jobfinder.resources.links_sort
import io.github.raghavsatyadev.jobfinder.resources.links_view_broken
import io.github.raghavsatyadev.jobfinder.resources.links_view_clear
import io.github.raghavsatyadev.jobfinder.resources.links_view_group
import io.github.raghavsatyadev.jobfinder.resources.links_view_none
import io.github.raghavsatyadev.jobfinder.resources.links_view_search
import io.github.raghavsatyadev.jobfinder.resources.links_view_search_clear
import io.github.raghavsatyadev.jobfinder.resources.links_view_sort_failing
import io.github.raghavsatyadev.jobfinder.resources.links_view_sort_last_checked
import io.github.raghavsatyadev.jobfinder.resources.links_view_sort_name
import io.github.raghavsatyadev.jobfinder.resources.links_view_sort_newest
import io.github.raghavsatyadev.jobfinder.resources.links_view_sort_oldest
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Above the searches, in one row like the jobs list: a text search, the filter button (boards and
 * broken searches, in a sheet) and the sort menu, which also groups the list by board.
 */
@Composable
internal fun LinksToolbar(
  view: LinksView,
  boards: List<Site>,
  onChange: (LinksView) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showFilters by rememberSaveable { mutableStateOf(false) }
  Row(
    modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = view.query,
      onValueChange = { onChange(view.copy(query = it)) },
      placeholder = { Text(stringResource(Res.string.links_view_search), maxLines = 1) },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon =
        if (view.query.isEmpty()) null
        else {
          {
            IconButton(onClick = { onChange(view.copy(query = "")) }) {
              Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.links_view_search_clear),
              )
            }
          }
        },
      singleLine = true,
      shape = CircleShape,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(4.dp))
    val active = view.activeFilters
    val filterLabel =
      if (active > 0) stringResource(Res.string.links_filters_active, active)
      else stringResource(Res.string.links_filters)
    FilledTonalIconButton(
      onClick = { showFilters = true },
      shapes = IconButtonDefaults.shapes(),
      modifier = Modifier.semantics { contentDescription = filterLabel },
    ) {
      BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
        Icon(Icons.Rounded.FilterList, contentDescription = null)
      }
    }
    SortMenu(view, onChange)
  }
  if (showFilters) {
    LinksFilterSheet(view, boards, onChange, onDismiss = { showFilters = false })
  }
}

/** The chips that hide searches: one per board, and the broken ones only. */
private val LinksView.activeFilters: Int
  get() = siteIds.size + if (brokenOnly) 1 else 0

/** The sort button, badged while the order or grouping is not the plain one. */
@Composable
private fun SortMenu(view: LinksView, onChange: (LinksView) -> Unit) {
  var open by remember { mutableStateOf(false) }
  val label = stringResource(Res.string.links_sort)
  Box {
    IconButton(
      onClick = { open = true },
      shapes = IconButtonDefaults.shapes(),
      modifier = Modifier.semantics { contentDescription = label },
    ) {
      BadgedBox(badge = { if (view.sort != LinkSort.NEWEST || view.groupByBoard) Badge() }) {
        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null)
      }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      LinkSort.entries.forEach { choice ->
        MenuItem(
          text = choice.label,
          icon =
            if (choice == view.sort) Icons.Rounded.RadioButtonChecked
            else Icons.Rounded.RadioButtonUnchecked,
          on = choice == view.sort,
        ) {
          open = false
          onChange(view.copy(sort = choice))
        }
      }
      HorizontalDivider()
      MenuItem(
        text = Res.string.links_view_group,
        icon =
          if (view.groupByBoard) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
        on = view.groupByBoard,
      ) {
        open = false
        onChange(view.copy(groupByBoard = !view.groupByBoard))
      }
    }
  }
}

@Composable
private fun MenuItem(text: StringResource, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
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

/** The filters in a sheet, like the jobs list's; each chip applies at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinksFilterSheet(
  view: LinksView,
  boards: List<Site>,
  onChange: (LinksView) -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
      Text(
        stringResource(Res.string.links_filters),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
      )
      Column(
        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        ChipGroup(stringResource(Res.string.links_filter_status)) {
          FilterChip(
            selected = view.brokenOnly,
            onClick = { onChange(view.copy(brokenOnly = !view.brokenOnly)) },
            label = { Text(stringResource(Res.string.links_view_broken)) },
            leadingIcon = {
              ChipIcon(if (view.brokenOnly) Icons.Rounded.Check else Icons.Rounded.Warning)
            },
          )
        }
        if (boards.size > 1) {
          ChipGroup(stringResource(Res.string.links_filter_boards)) {
            boards.forEach { site ->
              val selected = site.id in view.siteIds
              FilterChip(
                selected = selected,
                onClick = {
                  onChange(
                    view.copy(
                      siteIds = if (selected) view.siteIds - site.id else view.siteIds + site.id
                    )
                  )
                },
                label = { Text(site.name) },
                leadingIcon = { SiteLogo(site, size = FilterChipDefaults.IconSize) },
              )
            }
          }
        }
      }
      Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedButton(
          onClick = { onChange(view.copy(siteIds = emptySet(), brokenOnly = false)) },
          enabled = view.activeFilters > 0,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Text(stringResource(Res.string.links_view_clear))
        }
        Button(
          onClick = onDismiss,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        ) {
          Text(stringResource(Res.string.links_filter_done))
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(title: String, content: @Composable () -> Unit) {
  Column {
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(vertical = 4.dp).semantics { heading() },
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      content()
    }
  }
}

@Composable
private fun ChipIcon(icon: ImageVector) {
  Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

/** Shown in place of the list when the search text or chips hide every search. */
@Composable
internal fun NoMatchingLinks(onClear: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      stringResource(Res.string.links_view_none),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
    TextButton(onClick = onClear) { Text(stringResource(Res.string.links_view_clear)) }
  }
}

private val LinkSort.label: StringResource
  get() =
    when (this) {
      LinkSort.NEWEST -> Res.string.links_view_sort_newest
      LinkSort.OLDEST -> Res.string.links_view_sort_oldest
      LinkSort.NAME -> Res.string.links_view_sort_name
      LinkSort.LAST_CHECKED -> Res.string.links_view_sort_last_checked
      LinkSort.FAILING -> Res.string.links_view_sort_failing
    }
