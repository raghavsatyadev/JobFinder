package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_source_close
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_source_open
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_source_searches
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_sources
import io.github.raghavsatyadev.jobfinder.resources.jobs_filter_sources_hint
import io.github.raghavsatyadev.jobfinder.shared.model.JobFilter
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sources as an accordion: each job board with its saved searches under it. Ticking a board picks
 * all of its searches, including ones added later; ticking single searches picks just those.
 */
@Composable
internal fun SourceFilter(
  filter: JobFilter,
  sites: List<Site>,
  links: List<Link>,
  onChange: (JobFilter) -> Unit,
) {
  val bySite = links.groupBy { it.siteId }
  // Boards with saved searches, plus any board already picked.
  val boards = sites.filter { it.id in bySite || it.id in filter.siteIds }
  if (boards.isEmpty()) return
  var open by rememberSaveable { mutableStateOf(emptyList<Long>()) }
  Column {
    Text(
      stringResource(Res.string.jobs_filter_sources),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
    Text(
      stringResource(Res.string.jobs_filter_sources_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(bottom = 4.dp),
    )
    boards.forEach { site ->
      val searches = bySite[site.id].orEmpty()
      BoardRow(
        site = site,
        searches = searches,
        filter = filter,
        expanded = site.id in open,
        onExpand = { open = if (site.id in open) open - site.id else open + site.id },
        onChange = onChange,
      )
    }
  }
}

@Composable
private fun BoardRow(
  site: Site,
  searches: List<Link>,
  filter: JobFilter,
  expanded: Boolean,
  onExpand: () -> Unit,
  onChange: (JobFilter) -> Unit,
) {
  val whole = site.id in filter.siteIds
  val picked = searches.count { it.id in filter.linkIds }
  val state =
    when {
      whole || (searches.isNotEmpty() && picked == searches.size) -> ToggleableState.On
      picked > 0 -> ToggleableState.Indeterminate
      else -> ToggleableState.Off
    }
  val ownIds = searches.map { it.id }.toSet()
  Row(verticalAlignment = Alignment.CenterVertically) {
    Row(
      Modifier.weight(1f)
        .triStateToggleable(
          state = state,
          role = Role.Checkbox,
          onClick = {
            // On → off clears the board and its searches; otherwise the whole board is picked.
            val withoutOwn = filter.linkIds.filterNot { it in ownIds }
            onChange(
              if (state == ToggleableState.On)
                filter.copy(siteIds = filter.siteIds - site.id, linkIds = withoutOwn)
              else filter.copy(siteIds = filter.siteIds + site.id, linkIds = withoutOwn)
            )
          },
        )
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TriStateCheckbox(state = state, onClick = null)
      Column(Modifier.padding(start = 8.dp)) {
        Text(site.name, style = MaterialTheme.typography.bodyLarge)
        if (searches.isNotEmpty()) {
          Text(
            pluralStringResource(
              Res.plurals.jobs_filter_source_searches,
              searches.size,
              searches.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    if (searches.isNotEmpty()) {
      IconButton(onClick = onExpand) {
        Icon(
          Icons.Rounded.ExpandMore,
          contentDescription =
            stringResource(
              if (expanded) Res.string.jobs_filter_source_close
              else Res.string.jobs_filter_source_open,
              site.name,
            ),
          modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f },
        )
      }
    }
  }
  val motion = MaterialTheme.motionScheme
  AnimatedVisibility(
    visible = expanded,
    enter =
      if (animationsEnabled())
        expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
      else fadeIn(motion.fastEffectsSpec()),
    exit =
      if (animationsEnabled())
        shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
      else fadeOut(motion.fastEffectsSpec()),
  ) {
    Column(Modifier.padding(start = 32.dp)) {
      searches.forEach { link ->
        val checked = whole || link.id in filter.linkIds
        Row(
          Modifier.fillMaxWidth()
            .toggleable(
              value = checked,
              role = Role.Checkbox,
              onValueChange = {
                onChange(
                  if (whole) {
                    // Unticking one search of a whole board keeps the board's other searches.
                    filter.copy(
                      siteIds = filter.siteIds - site.id,
                      linkIds = filter.linkIds + searches.map { it.id }.filter { it != link.id },
                    )
                  } else filter.copy(linkIds = filter.linkIds.toggle(link.id))
                )
              },
            )
            .padding(vertical = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(checked = checked, onCheckedChange = null)
          Text(
            link.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp),
          )
        }
      }
    }
  }
}
