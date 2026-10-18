package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
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
 * Above the searches: a text search, then a row of chips for the order, grouping by board, broken
 * searches only, and one chip per board.
 */
@Composable
internal fun LinksToolbar(
  view: LinksView,
  boards: List<Site>,
  onChange: (LinksView) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    OutlinedTextField(
      value = view.query,
      onValueChange = { onChange(view.copy(query = it)) },
      placeholder = { Text(stringResource(Res.string.links_view_search)) },
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
      shape = MaterialTheme.shapes.extraLarge,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      item(key = "sort") { SortChip(view.sort) { onChange(view.copy(sort = it)) } }
      item(key = "group") {
        FilterChip(
          selected = view.groupByBoard,
          onClick = { onChange(view.copy(groupByBoard = !view.groupByBoard)) },
          label = { Text(stringResource(Res.string.links_view_group)) },
          leadingIcon = {
            ChipIcon(if (view.groupByBoard) Icons.Rounded.Check else Icons.Rounded.ViewAgenda)
          },
        )
      }
      item(key = "broken") {
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
        items(boards, key = { "site-${it.id}" }) { site ->
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
}

@Composable
private fun ChipIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
  Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
}

@Composable
private fun SortChip(sort: LinkSort, onPick: (LinkSort) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    AssistChip(
      onClick = { open = true },
      label = { Text(stringResource(sort.label)) },
      leadingIcon = { ChipIcon(Icons.AutoMirrored.Rounded.Sort) },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      LinkSort.entries.forEach { choice ->
        DropdownMenuItem(
          text = { Text(stringResource(choice.label)) },
          leadingIcon =
            if (choice == sort) {
              { Icon(Icons.Rounded.Check, contentDescription = null) }
            } else null,
          onClick = {
            open = false
            onPick(choice)
          },
        )
      }
    }
  }
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
