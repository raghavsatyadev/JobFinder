@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ats_section_title
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import io.github.raghavsatyadev.jobfinder.resources.ghost_section_title
import io.github.raghavsatyadev.jobfinder.resources.insights_section_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_labels
import io.github.raghavsatyadev.jobfinder.resources.jobs_notes
import io.github.raghavsatyadev.jobfinder.resources.layout_drag
import io.github.raghavsatyadev.jobfinder.resources.layout_hidden
import io.github.raghavsatyadev.jobfinder.resources.layout_hide
import io.github.raghavsatyadev.jobfinder.resources.layout_intro
import io.github.raghavsatyadev.jobfinder.resources.layout_load_failed
import io.github.raghavsatyadev.jobfinder.resources.layout_move_down
import io.github.raghavsatyadev.jobfinder.resources.layout_move_up
import io.github.raghavsatyadev.jobfinder.resources.layout_reset
import io.github.raghavsatyadev.jobfinder.resources.layout_save_failed
import io.github.raghavsatyadev.jobfinder.resources.layout_show
import io.github.raghavsatyadev.jobfinder.resources.offline_later
import io.github.raghavsatyadev.jobfinder.resources.tips_section_title
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.DetailSection
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableColumn

data class DetailLayoutUiState(
  /** `null` until loaded. */
  val layout: DetailLayout? = null,
  val loadFailed: Boolean = false,
  val saveFailed: Boolean = false,
)

sealed interface DetailLayoutEvent {
  data class Move(val from: Int, val to: Int) : DetailLayoutEvent

  data class ToggleHidden(val section: DetailSection) : DetailLayoutEvent

  data object Reset : DetailLayoutEvent

  data object Retry : DetailLayoutEvent
}

/** The job detail's section order, saved on the computer after every change. */
class DetailLayoutViewModel(private val settings: SettingsRepository) : ViewModel() {
  private val _state = MutableStateFlow(DetailLayoutUiState())
  val state: StateFlow<DetailLayoutUiState> = _state.asStateFlow()

  init {
    load()
  }

  fun onEvent(event: DetailLayoutEvent) {
    val layout = _state.value.layout?.normalized()
    when (event) {
      is DetailLayoutEvent.Move ->
        layout?.let {
          val order = it.order.toMutableList()
          if (event.from !in order.indices || event.to !in order.indices) return
          order.add(event.to, order.removeAt(event.from))
          save(it.copy(order = order))
        }
      is DetailLayoutEvent.ToggleHidden ->
        layout?.let {
          val hidden =
            if (event.section in it.hidden) it.hidden - event.section else it.hidden + event.section
          save(it.copy(hidden = hidden))
        }
      DetailLayoutEvent.Reset -> save(DetailLayout.DEFAULT)
      DetailLayoutEvent.Retry -> load()
    }
  }

  private fun load() {
    _state.update { it.copy(loadFailed = false) }
    viewModelScope.launch {
      try {
        val layout = settings.getDetailLayout().normalized()
        _state.update { it.copy(layout = layout) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loadFailed = true) }
      }
    }
  }

  /** Shows [layout] at once; a failed save puts the saved one back. */
  private fun save(layout: DetailLayout) {
    val before = _state.value.layout
    _state.update { it.copy(layout = layout, saveFailed = false) }
    viewModelScope.launch {
      try {
        val saved = settings.updateDetailLayout(layout)
        _state.update { it.copy(layout = saved) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(layout = before, saveFailed = true) }
      }
    }
  }
}

@Composable
fun DetailLayoutSettings() {
  val viewModel = koinViewModel<DetailLayoutViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  DetailLayoutContent(state, viewModel::onEvent)
}

@Composable
fun DetailLayoutContent(state: DetailLayoutUiState, onEvent: (DetailLayoutEvent) -> Unit) {
  val offline = LocalComputerOffline.current
  val enabled = !offline
  Column(Modifier.padding(vertical = 8.dp)) {
    Muted(stringResource(Res.string.layout_intro))
    val layout = state.layout
    when {
      layout == null && state.loadFailed ->
        Row(
          Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Muted(
            stringResource(
              if (offline) Res.string.offline_later else Res.string.layout_load_failed
            ),
            Modifier.weight(1f),
          )
          TextButton(
            onClick = { onEvent(DetailLayoutEvent.Retry) },
            shapes = ButtonDefaults.shapes(),
          ) {
            Text(stringResource(Res.string.common_retry))
          }
        }
      layout != null -> {
        val order = layout.normalized().order
        ReorderableColumn(
          list = order,
          onSettle = { from, to -> onEvent(DetailLayoutEvent.Move(from, to)) },
          modifier = Modifier.fillMaxWidth(),
        ) { index, section, dragging ->
          key(section) {
            ReorderableItem {
              SectionRow(
                section = section,
                hidden = section in layout.hidden,
                dragging = dragging,
                enabled = enabled,
                index = index,
                last = order.lastIndex,
                handle = Modifier.draggableHandle(enabled = enabled),
                onEvent = onEvent,
              )
            }
          }
        }
        if (state.saveFailed) {
          Text(
            stringResource(Res.string.layout_save_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
          )
        }
        if (layout.normalized() != DetailLayout.DEFAULT) {
          TextButton(
            onClick = { onEvent(DetailLayoutEvent.Reset) },
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(horizontal = 8.dp),
          ) {
            Text(stringResource(Res.string.layout_reset))
          }
        }
      }
    }
  }
}

@Composable
private fun SectionRow(
  section: DetailSection,
  hidden: Boolean,
  dragging: Boolean,
  enabled: Boolean,
  index: Int,
  last: Int,
  handle: Modifier,
  onEvent: (DetailLayoutEvent) -> Unit,
) {
  val title = stringResource(section.title)
  val moveUp = stringResource(Res.string.layout_move_up)
  val moveDown = stringResource(Res.string.layout_move_down)
  Surface(
    color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
    shadowElevation = if (dragging) 4.dp else 0.dp,
    shape = MaterialTheme.shapes.medium,
  ) {
    ListItem(
      leadingContent = {
        Icon(
          Icons.Rounded.DragIndicator,
          stringResource(Res.string.layout_drag, title),
          tint =
            if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
          modifier = handle.padding(8.dp),
        )
      },
      headlineContent = {
        Text(
          title,
          color =
            if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
      },
      supportingContent =
        if (hidden) ({ Text(stringResource(Res.string.layout_hidden)) }) else null,
      trailingContent = {
        IconButton(
          onClick = { onEvent(DetailLayoutEvent.ToggleHidden(section)) },
          enabled = enabled,
          shapes = IconButtonDefaults.shapes(),
        ) {
          Icon(
            if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            stringResource(if (hidden) Res.string.layout_show else Res.string.layout_hide, title),
          )
        }
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      modifier =
        Modifier.semantics {
          if (enabled) {
            customActions = buildList {
              if (index > 0) {
                add(
                  CustomAccessibilityAction(moveUp) {
                    onEvent(DetailLayoutEvent.Move(index, index - 1))
                    true
                  }
                )
              }
              if (index < last) {
                add(
                  CustomAccessibilityAction(moveDown) {
                    onEvent(DetailLayoutEvent.Move(index, index + 1))
                    true
                  }
                )
              }
            }
          }
        },
    )
  }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

/** The section's title, as the job detail shows it. */
val DetailSection.title: StringResource
  get() =
    when (this) {
      DetailSection.LABELS -> Res.string.jobs_detail_labels
      DetailSection.ATS -> Res.string.ats_section_title
      DetailSection.DESCRIPTION -> Res.string.jobs_detail_description
      DetailSection.COMPANY -> Res.string.insights_section_title
      DetailSection.GHOST -> Res.string.ghost_section_title
      DetailSection.TIPS -> Res.string.tips_section_title
      DetailSection.NOTES -> Res.string.jobs_notes
    }
