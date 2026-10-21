package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_agents
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_ai
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_backup
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_computers
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_form_answers
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_layout
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_login
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_pairing
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_resume
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_scanning
import io.github.raghavsatyadev.jobfinder.resources.settings_pick_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_pick_title
import io.github.raghavsatyadev.jobfinder.resources.settings_search
import io.github.raghavsatyadev.jobfinder.resources.settings_search_clear
import io.github.raghavsatyadev.jobfinder.resources.settings_search_none
import io.github.raghavsatyadev.jobfinder.resources.settings_section_agents
import io.github.raghavsatyadev.jobfinder.resources.settings_section_ai
import io.github.raghavsatyadev.jobfinder.resources.settings_section_backup
import io.github.raghavsatyadev.jobfinder.resources.settings_section_desktop
import io.github.raghavsatyadev.jobfinder.resources.settings_section_form_answers
import io.github.raghavsatyadev.jobfinder.resources.settings_section_layout
import io.github.raghavsatyadev.jobfinder.resources.settings_section_login
import io.github.raghavsatyadev.jobfinder.resources.settings_section_pairing
import io.github.raghavsatyadev.jobfinder.resources.settings_section_resume
import io.github.raghavsatyadev.jobfinder.resources.settings_section_scanning
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_agents
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_ai
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_backup
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_computers
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_form_answers
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_layout
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_login
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_pairing
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_resume
import io.github.raghavsatyadev.jobfinder.resources.settings_summary_scanning
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.ui.components.EmptyState
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A settings section: its menu row (icon, title, one-line summary) and its search words. */
enum class SettingsSection(
  val title: StringResource,
  val summary: StringResource,
  val keywords: StringResource,
  val icon: ImageVector,
) {
  SCANNING(
    Res.string.settings_section_scanning,
    Res.string.settings_summary_scanning,
    Res.string.settings_keywords_scanning,
    Icons.Rounded.TravelExplore,
  ),
  LAYOUT(
    Res.string.settings_section_layout,
    Res.string.settings_summary_layout,
    Res.string.settings_keywords_layout,
    Icons.Rounded.ViewAgenda,
  ),
  AI(
    Res.string.settings_section_ai,
    Res.string.settings_summary_ai,
    Res.string.settings_keywords_ai,
    Icons.Rounded.AutoAwesome,
  ),
  RESUME(
    Res.string.settings_section_resume,
    Res.string.settings_summary_resume,
    Res.string.settings_keywords_resume,
    Icons.Rounded.Description,
  ),
  BACKUP(
    Res.string.settings_section_backup,
    Res.string.settings_summary_backup,
    Res.string.settings_keywords_backup,
    Icons.Rounded.Backup,
  ),
  FORM_ANSWERS(
    Res.string.settings_section_form_answers,
    Res.string.settings_summary_form_answers,
    Res.string.settings_keywords_form_answers,
    Icons.Rounded.EditNote,
  ),
  AGENTS(
    Res.string.settings_section_agents,
    Res.string.settings_summary_agents,
    Res.string.settings_keywords_agents,
    Icons.Rounded.SmartToy,
  ),
  COMPUTERS(
    Res.string.settings_section_desktop,
    Res.string.settings_summary_computers,
    Res.string.settings_keywords_computers,
    Icons.Rounded.Computer,
  ),
  PAIRING(
    Res.string.settings_section_pairing,
    Res.string.settings_summary_pairing,
    Res.string.settings_keywords_pairing,
    Icons.Rounded.QrCode2,
  ),
  LOGIN(
    Res.string.settings_section_login,
    Res.string.settings_summary_login,
    Res.string.settings_keywords_login,
    Icons.AutoMirrored.Rounded.Login,
  ),
}

internal class SectionEntry(val section: SettingsSection, val content: @Composable () -> Unit)

/** The sections this device has, in menu order; a `null` body leaves its section out. */
internal fun settingsEntries(
  state: SettingsUiState,
  onEvent: (SettingsUiEvent) -> Unit,
  resume: (@Composable () -> Unit)?,
  backup: (@Composable () -> Unit)?,
  layout: (@Composable () -> Unit)?,
  ai: (@Composable () -> Unit)?,
  keys: (@Composable () -> Unit)?,
  answers: (@Composable () -> Unit)?,
  agents: (@Composable () -> Unit)?,
): List<SectionEntry> = buildList {
  val scanner: ScannerSettings? = state.scanner
  add(SectionEntry(SettingsSection.SCANNING) { ScanningSection(scanner, onEvent) })
  layout?.let { add(SectionEntry(SettingsSection.LAYOUT, it)) }
  if (ai != null || keys != null) add(SectionEntry(SettingsSection.AI) { AiSection(ai, keys) })
  resume?.let { add(SectionEntry(SettingsSection.RESUME, it)) }
  backup?.let { add(SectionEntry(SettingsSection.BACKUP, it)) }
  answers?.let { add(SectionEntry(SettingsSection.FORM_ANSWERS, it)) }
  agents?.let { add(SectionEntry(SettingsSection.AGENTS, it)) }
  if (state.phone && state.computers.isNotEmpty()) {
    add(SectionEntry(SettingsSection.COMPUTERS) { ComputersSection(state, onEvent) })
  }
  if (state.desktop) {
    add(SectionEntry(SettingsSection.PAIRING) { PairingSection(state, onEvent) })
    add(SectionEntry(SettingsSection.LOGIN) { LoginSection(state, onEvent) })
  }
}

/** The settings menu: a search over the sections, then one row per section that opens its page. */
@Composable
internal fun SettingsMenu(
  entries: List<SectionEntry>,
  initialQuery: String,
  onOpen: (SettingsSection) -> Unit,
  /** The section open beside the menu, shown as chosen; `null` when the page covers the menu. */
  selected: SettingsSection? = null,
) {
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val shown = entries.filter { entry ->
    val section = entry.section
    val haystack =
      listOf(section.title, section.summary, section.keywords)
        .map { stringResource(it) }
        .joinToString(", ")
        .lowercase()
    words.all { it in haystack }
  }
  Column(Modifier.fillMaxSize()) {
    SearchField(
      query,
      onChange = { query = it },
      modifier =
        Modifier.align(Alignment.CenterHorizontally)
          .widthIn(max = MAX_WIDTH)
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    PageColumn(spacing = 8.dp) {
      if (shown.isEmpty()) {
        Text(
          stringResource(Res.string.settings_search_none, query.trim()),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(8.dp),
        )
      }
      shown.forEach { entry ->
        key(entry.section.name) { MenuRow(entry.section, entry.section == selected, onOpen) }
      }
    }
  }
}

/** The empty pane beside the menu on a wide window, before a section is picked. */
@Composable
fun SettingsPlaceholder() {
  EmptyState(
    icon = Icons.Rounded.TouchApp,
    title = stringResource(Res.string.settings_pick_title),
    hint = stringResource(Res.string.settings_pick_hint),
  )
}

/** One section on its own page, in a single card. */
@Composable
internal fun SettingsPage(entry: SectionEntry) {
  PageColumn(spacing = 12.dp) {
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      shape = MaterialTheme.shapes.large,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(Modifier.padding(vertical = 8.dp)) { entry.content() }
    }
  }
}

@Composable
private fun MenuRow(section: SettingsSection, chosen: Boolean, onOpen: (SettingsSection) -> Unit) {
  Card(
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (chosen) MaterialTheme.colorScheme.secondaryContainer
          else MaterialTheme.colorScheme.surfaceContainer
      ),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    ListItem(
      headlineContent = {
        Text(
          stringResource(section.title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      },
      supportingContent = { Text(stringResource(section.summary)) },
      leadingContent = {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.secondaryContainer,
          contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(40.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(section.icon, contentDescription = null, modifier = Modifier.size(22.dp))
          }
        }
      },
      trailingContent = {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      modifier = Modifier.clickable(role = Role.Button) { onOpen(section) },
    )
  }
}

/** The scrolling column every settings page sits in, centred and at most [MAX_WIDTH] wide. */
@Composable
private fun PageColumn(spacing: Dp, content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    Column(
      Modifier.align(Alignment.TopCenter)
        .widthIn(max = MAX_WIDTH)
        .fillMaxWidth()
        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + scrollBottom()),
      verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
      content()
    }
  }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier) {
  OutlinedTextField(
    value = query,
    onValueChange = onChange,
    placeholder = { Text(stringResource(Res.string.settings_search)) },
    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
    trailingIcon =
      if (query.isEmpty()) null
      else {
        {
          IconButton(onClick = { onChange("") }) {
            Icon(
              Icons.Rounded.Close,
              contentDescription = stringResource(Res.string.settings_search_clear),
            )
          }
        }
      },
    singleLine = true,
    shape = MaterialTheme.shapes.extraLarge,
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    modifier = modifier,
  )
}

private val MAX_WIDTH = 760.dp
