@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/** The height of [FloatingTabBar], without the gap below it. */
val FloatingTabBarHeight = 64.dp

/**
 * The phone's navigation: a pill floating above the content. The selected tab grows into a filled
 * pill with its label; the others show only their icon.
 */
@Composable
fun FloatingTabBar(
  tabs: ImmutableList<TopLevel>,
  selected: TopLevel,
  onSelect: (TopLevel) -> Unit,
  modifier: Modifier = Modifier,
) {
  val motion = MaterialTheme.motionScheme
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shadowElevation = 6.dp,
    modifier = modifier,
  ) {
    Row(
      Modifier.heightIn(min = FloatingTabBarHeight).padding(8.dp).selectableGroup(),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      tabs.forEach { tab ->
        val isSelected = tab == selected
        val container by
          animateColorAsState(
            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            motion.defaultEffectsSpec(),
          )
        val content by
          animateColorAsState(
            if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            motion.defaultEffectsSpec(),
          )
        val label = stringResource(tab.label)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier.clip(CircleShape)
              .background(container)
              .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
              .heightIn(min = 48.dp)
              .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
          Icon(
            if (isSelected) tab.selectedIcon else tab.icon,
            // The label reads the name when it shows; an icon alone carries it.
            contentDescription = if (isSelected) null else label,
            tint = content,
          )
          AnimatedVisibility(
            visible = isSelected,
            enter = expandHorizontally(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
          ) {
            Text(
              label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = content,
              maxLines = 1,
              modifier = Modifier.padding(start = 8.dp),
            )
          }
        }
      }
    }
  }
}
