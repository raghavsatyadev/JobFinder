@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import io.github.raghavsatyadev.jobfinder.resources.help_title
import io.github.raghavsatyadev.jobfinder.resources.settings_ai_paused_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_ai_paused_title
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_add
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_check
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_connected
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_offline
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_other
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_cleanup_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_cleanup_title
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_keep_days
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_keep_fewer
import io.github.raghavsatyadev.jobfinder.resources.settings_excluded_keep_more
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_12_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_1_day
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_1_week
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_2_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_3_days
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_4_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_8_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_custom
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_never
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risk_confirm
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risk_text
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risk_title
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risky
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risky_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_title
import io.github.raghavsatyadev.jobfinder.resources.settings_in_app_browser_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_in_app_browser_title
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_agents
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_ai
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_backup
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_computers
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_form_answers
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_keys
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_layout
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_login
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_pairing
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_resume
import io.github.raghavsatyadev.jobfinder.resources.settings_keywords_scanning
import io.github.raghavsatyadev.jobfinder.resources.settings_load_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_login_button
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied_missing
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copying
import io.github.raghavsatyadev.jobfinder.resources.settings_login_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_login_no_extension
import io.github.raghavsatyadev.jobfinder.resources.settings_login_sharing_off
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_address
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_off
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_qr_description
import io.github.raghavsatyadev.jobfinder.resources.settings_port_error
import io.github.raghavsatyadev.jobfinder.resources.settings_port_label
import io.github.raghavsatyadev.jobfinder.resources.settings_port_save
import io.github.raghavsatyadev.jobfinder.resources.settings_port_saved
import io.github.raghavsatyadev.jobfinder.resources.settings_prevent_sleep_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_prevent_sleep_title
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_button
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_confirm
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_text
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_title
import io.github.raghavsatyadev.jobfinder.resources.settings_rotated
import io.github.raghavsatyadev.jobfinder.resources.settings_save_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_search
import io.github.raghavsatyadev.jobfinder.resources.settings_search_clear
import io.github.raghavsatyadev.jobfinder.resources.settings_search_none
import io.github.raghavsatyadev.jobfinder.resources.settings_section_agents
import io.github.raghavsatyadev.jobfinder.resources.settings_section_ai
import io.github.raghavsatyadev.jobfinder.resources.settings_section_backup
import io.github.raghavsatyadev.jobfinder.resources.settings_section_close
import io.github.raghavsatyadev.jobfinder.resources.settings_section_desktop
import io.github.raghavsatyadev.jobfinder.resources.settings_section_form_answers
import io.github.raghavsatyadev.jobfinder.resources.settings_section_keys
import io.github.raghavsatyadev.jobfinder.resources.settings_section_layout
import io.github.raghavsatyadev.jobfinder.resources.settings_section_login
import io.github.raghavsatyadev.jobfinder.resources.settings_section_open
import io.github.raghavsatyadev.jobfinder.resources.settings_section_pairing
import io.github.raghavsatyadev.jobfinder.resources.settings_section_resume
import io.github.raghavsatyadev.jobfinder.resources.settings_section_scanning
import io.github.raghavsatyadev.jobfinder.resources.settings_sound_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_sound_title
import io.github.raghavsatyadev.jobfinder.resources.settings_title
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_button
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_description
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_text
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_title
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoice
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin bindings of the settings feature. `DesktopFeatures` is bound on desktop only, `PhonePairing`
 * on Android only.
 */
val settingsUiModule = module {
  viewModel { SettingsViewModel(get(), getOrNull(), getOrNull(), getOrNull()) }
  viewModel { ResumeViewModel(get(), getOrNull()) }
  viewModel { AiModelViewModel(get()) }
  viewModel { OnlineKeysViewModel(get()) }
  viewModel { LocalModelsViewModel(get()) }
  viewModel {
    BackupViewModel(
      getOrNull(),
      getOrNull(),
      get(),
      desktop = getOrNull<DesktopFeatures>() != null,
      aiKeys = getOrNull(),
      deviceKeys = getOrNull(),
    )
  }
  viewModel { DetailLayoutViewModel(get()) }
  viewModel { FormAnswersViewModel(get()) }
}

/** One scan frequency choice; [cronRule] `null` is "Never". */
data class ScanFrequency(val cronRule: String?, val label: StringResource)

/**
 * The old app's frequencies of 2 hours or more, and "Never". Every 12 hours is the default and the
 * shortest we advise; the shorter ones ask the user to accept the risk of a blocked address.
 */
val SCAN_FREQUENCIES: ImmutableList<ScanFrequency> =
  persistentListOf(
    ScanFrequency(null, Res.string.settings_frequency_never),
    ScanFrequency("0 */2 * * *", Res.string.settings_frequency_2_hours),
    ScanFrequency("0 */4 * * *", Res.string.settings_frequency_4_hours),
    ScanFrequency("0 */8 * * *", Res.string.settings_frequency_8_hours),
    ScanFrequency(ScannerSettings.DEFAULT_CRON_RULE, Res.string.settings_frequency_12_hours),
    ScanFrequency("0 0 * * *", Res.string.settings_frequency_1_day),
    ScanFrequency("0 0 */3 * *", Res.string.settings_frequency_3_days),
    ScanFrequency("0 0 * * 0", Res.string.settings_frequency_1_week),
  )

/** Scanner settings, and on desktop the phone pairing. */
@Composable
fun SettingsScreen(onOpenHelp: (() -> Unit)? = null) {
  val viewModel = koinViewModel<SettingsViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      val message =
        when (effect) {
          SettingsEffect.SaveFailed -> Res.string.settings_save_failed
          SettingsEffect.PortSaved -> Res.string.settings_port_saved
          SettingsEffect.Rotated -> Res.string.settings_rotated
          SettingsEffect.RotateFailed -> Res.string.settings_rotate_failed
        }
      snackbar.showSnackbar(getString(message))
    }
  }
  SettingsContent(
    state,
    viewModel::onEvent,
    snackbar,
    resume = { ResumeSettings() },
    backup = { BackupSettings() },
    layout = { DetailLayoutSettings() },
    ai = if (getKoin().getOrNull<AiChoice>() != null) ({ AiModelSettings() }) else null,
    keys = if (getKoin().getOrNull<AiKeys>() != null) ({ OnlineKeysSettings() }) else null,
    answers =
      if (getKoin().getOrNull<FormAnswersRepository>() != null) ({ FormAnswersSettings() })
      else null,
    agents = if (getKoin().getOrNull<AgentAccess>() != null) ({ AgentAccessSettings() }) else null,
    onOpenHelp = onOpenHelp,
  )
}

/** The stateless settings screen, for [SettingsScreen] and previews. */
@Composable
fun SettingsContent(
  state: SettingsUiState,
  onEvent: (SettingsUiEvent) -> Unit,
  snackbar: SnackbarHostState,
  modifier: Modifier = Modifier,
  /** The resume section; `null` leaves it out (previews of the other sections). */
  resume: (@Composable () -> Unit)? = null,
  /** The backup section; `null` leaves it out. */
  backup: (@Composable () -> Unit)? = null,
  /** The job detail's section order; `null` leaves it out. */
  layout: (@Composable () -> Unit)? = null,
  /** The AI model section, desktop only; `null` leaves it out. */
  ai: (@Composable () -> Unit)? = null,
  /** The free online AI keys section, desktop only; `null` leaves it out. */
  keys: (@Composable () -> Unit)? = null,
  /** The saved application form answers, desktop only; `null` leaves it out. */
  answers: (@Composable () -> Unit)? = null,
  /** The MCP server for AI agents, desktop only; `null` leaves it out. */
  agents: (@Composable () -> Unit)? = null,
  /** Sections open at first; all closed in the app. */
  initiallyOpen: Set<SettingsSection> = emptySet(),
  /** The search at first; empty in the app. */
  initialQuery: String = "",
  /** Opens Help from the top bar; `null` hides the button. */
  onOpenHelp: (() -> Unit)? = null,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(Res.string.settings_title)) },
        actions = {
          if (onOpenHelp != null) {
            IconButton(onClick = onOpenHelp, shapes = IconButtonDefaults.shapes()) {
              Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(Res.string.help_title),
              )
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
    contentWindowInsets = screenInsets(),
    modifier = modifier,
  ) { padding ->
    Box(Modifier.fillMaxSize().paddingAbove(padding)) {
      val scanner = state.scanner
      // Without the scanner settings (computer out of reach, none kept) the other sections still
      // show: backups, resume and the paired computers work on the phone's own copy.
      if (state.loading && scanner == null) LoadingState()
      else
        SettingsList(
          state,
          scanner,
          onEvent,
          resume,
          backup,
          layout,
          ai,
          keys,
          answers,
          agents,
          initiallyOpen,
          initialQuery,
        )
    }
  }
  val forget = state.confirmForget
  if (forget != null) {
    ConfirmDialog(
      icon = Icons.Rounded.LinkOff,
      title = stringResource(Res.string.settings_unpair_title, forget.label),
      text = stringResource(Res.string.settings_unpair_text),
      confirmLabel = stringResource(Res.string.settings_unpair_button),
      onConfirm = { onEvent(SettingsUiEvent.ConfirmForget) },
      onDismiss = { onEvent(SettingsUiEvent.DismissForget) },
    )
  }
  state.confirmFrequency?.let { choice ->
    ConfirmDialog(
      icon = Icons.Rounded.Warning,
      title = stringResource(Res.string.settings_frequency_risk_title),
      text = stringResource(Res.string.settings_frequency_risk_text, stringResource(choice.label)),
      confirmLabel = stringResource(Res.string.settings_frequency_risk_confirm),
      onConfirm = { onEvent(SettingsUiEvent.ConfirmFrequency) },
      onDismiss = { onEvent(SettingsUiEvent.DismissFrequency) },
    )
  }
  if (state.confirmRotate) {
    ConfirmDialog(
      icon = Icons.Rounded.LockReset,
      title = stringResource(Res.string.settings_rotate_title),
      text = stringResource(Res.string.settings_rotate_text),
      confirmLabel = stringResource(Res.string.settings_rotate_confirm),
      onConfirm = { onEvent(SettingsUiEvent.ConfirmRotate) },
      onDismiss = { onEvent(SettingsUiEvent.DismissRotate) },
    )
  }
}

/** A settings section: its title and the words the search finds it by. */
enum class SettingsSection(val title: StringResource, val keywords: StringResource) {
  SCANNING(Res.string.settings_section_scanning, Res.string.settings_keywords_scanning),
  LAYOUT(Res.string.settings_section_layout, Res.string.settings_keywords_layout),
  AI(Res.string.settings_section_ai, Res.string.settings_keywords_ai),
  KEYS(Res.string.settings_section_keys, Res.string.settings_keywords_keys),
  RESUME(Res.string.settings_section_resume, Res.string.settings_keywords_resume),
  BACKUP(Res.string.settings_section_backup, Res.string.settings_keywords_backup),
  FORM_ANSWERS(
    Res.string.settings_section_form_answers,
    Res.string.settings_keywords_form_answers,
  ),
  AGENTS(Res.string.settings_section_agents, Res.string.settings_keywords_agents),
  COMPUTERS(Res.string.settings_section_desktop, Res.string.settings_keywords_computers),
  PAIRING(Res.string.settings_section_pairing, Res.string.settings_keywords_pairing),
  LOGIN(Res.string.settings_section_login, Res.string.settings_keywords_login),
}

private class SectionEntry(val section: SettingsSection, val content: @Composable () -> Unit)

/**
 * Every section starts closed so the titles read as a menu; a search opens the sections it finds.
 */
@Composable
private fun SettingsList(
  state: SettingsUiState,
  scanner: ScannerSettings?,
  onEvent: (SettingsUiEvent) -> Unit,
  resume: (@Composable () -> Unit)?,
  backup: (@Composable () -> Unit)?,
  layout: (@Composable () -> Unit)?,
  ai: (@Composable () -> Unit)?,
  keys: (@Composable () -> Unit)?,
  answers: (@Composable () -> Unit)?,
  agents: (@Composable () -> Unit)?,
  initiallyOpen: Set<SettingsSection>,
  initialQuery: String,
) {
  var query by rememberSaveable { mutableStateOf(initialQuery) }
  // Names, so the list survives a configuration change.
  var open by rememberSaveable { mutableStateOf(initiallyOpen.map { it.name }) }
  val entries = buildList {
    add(SectionEntry(SettingsSection.SCANNING) { ScanningSection(scanner, onEvent) })
    layout?.let { add(SectionEntry(SettingsSection.LAYOUT, it)) }
    ai?.let { add(SectionEntry(SettingsSection.AI, it)) }
    keys?.let { add(SectionEntry(SettingsSection.KEYS, it)) }
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
  val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
  val shown = entries.filter { entry ->
    val haystack =
      (stringResource(entry.section.title) + ", " + stringResource(entry.section.keywords))
        .lowercase()
    words.all { it in haystack }
  }
  Column(Modifier.fillMaxSize()) {
    SearchField(
      query,
      onChange = { query = it },
      modifier =
        Modifier.align(Alignment.CenterHorizontally)
          .widthIn(max = 760.dp)
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
    )
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
      Column(
        Modifier.align(Alignment.TopCenter)
          .widthIn(max = 760.dp)
          .fillMaxWidth()
          .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + scrollBottom()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (shown.isEmpty()) {
          Text(
            stringResource(Res.string.settings_search_none, query.trim()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
          )
        }
        shown.forEach { entry ->
          val name = entry.section.name
          key(name) {
            Section(
              stringResource(entry.section.title),
              open = words.isNotEmpty() || name in open,
              onToggle = { open = if (name in open) open - name else open + name },
              content = entry.content,
            )
          }
        }
      }
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

@Composable
private fun ScanningSection(scanner: ScannerSettings?, onEvent: (SettingsUiEvent) -> Unit) {
  if (scanner == null) {
    ScannerUnavailable(onRetry = { onEvent(SettingsUiEvent.Retry) })
    return
  }
  val enabled = !LocalComputerOffline.current
  FrequencyPicker(scanner.cronRule, enabled) { onEvent(SettingsUiEvent.SetFrequency(it)) }
  HorizontalDivider(Modifier.padding(horizontal = 16.dp))
  SwitchRow(
    Res.string.settings_ai_paused_title,
    Res.string.settings_ai_paused_hint,
    scanner.aiPaused,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetAiPaused(it))
  }
  SwitchRow(
    Res.string.settings_prevent_sleep_title,
    Res.string.settings_prevent_sleep_hint,
    scanner.preventSleep,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetPreventSleep(it))
  }
  SwitchRow(
    Res.string.settings_sound_title,
    Res.string.settings_sound_hint,
    scanner.useSound,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetSound(it))
  }
  SwitchRow(
    Res.string.settings_in_app_browser_title,
    Res.string.settings_in_app_browser_hint,
    scanner.inAppBrowserEnabled,
    enabled,
  ) {
    onEvent(SettingsUiEvent.SetInAppBrowser(it))
  }
  HorizontalDivider(Modifier.padding(horizontal = 16.dp))
  val keepDays = scanner.excludedKeepDays
  SwitchRow(
    Res.string.settings_excluded_cleanup_title,
    Res.string.settings_excluded_cleanup_hint,
    keepDays != null,
    enabled,
  ) {
    onEvent(
      SettingsUiEvent.SetExcludedKeepDays(
        if (it) ScannerSettings.DEFAULT_EXCLUDED_KEEP_DAYS else null
      )
    )
  }
  if (keepDays != null) {
    KeepDaysStepper(keepDays, enabled) { onEvent(SettingsUiEvent.SetExcludedKeepDays(it)) }
  }
  if (!enabled) OfflineNote()
}

/** Steps [days] through [KEEP_DAY_STEPS]; a value between steps moves to the next one. */
@Composable
private fun KeepDaysStepper(days: Int, enabled: Boolean, onChange: (Int) -> Unit) {
  Row(
    Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp).fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(Res.string.settings_excluded_keep_days, days),
      style = MaterialTheme.typography.bodyLarge,
      modifier = Modifier.weight(1f),
    )
    val fewer = KEEP_DAY_STEPS.lastOrNull { it < days }
    val more = KEEP_DAY_STEPS.firstOrNull { it > days }
    IconButton(onClick = { fewer?.let(onChange) }, enabled = enabled && fewer != null) {
      Icon(
        Icons.Rounded.Remove,
        contentDescription = stringResource(Res.string.settings_excluded_keep_fewer),
      )
    }
    IconButton(onClick = { more?.let(onChange) }, enabled = enabled && more != null) {
      Icon(
        Icons.Rounded.Add,
        contentDescription = stringResource(Res.string.settings_excluded_keep_more),
      )
    }
  }
}

private val KEEP_DAY_STEPS = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365)

@Composable
private fun LoginSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.settings_login_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
      onClick = { onEvent(SettingsUiEvent.OpenLoginBrowser) },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.AutoMirrored.Rounded.Login,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(Res.string.settings_login_button),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    HorizontalDivider()
    Text(
      stringResource(Res.string.settings_login_copy_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
      onClick = { onEvent(SettingsUiEvent.CopyBrowserSignIns) },
      enabled = !state.copyingSignIns,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.Rounded.ContentCopy,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(
          if (state.copyingSignIns) Res.string.settings_login_copying
          else Res.string.settings_login_copy
        ),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    state.signInCopy?.let { result ->
      val failed = result !is SignInCopy.Copied || result.missing.isNotEmpty()
      Text(
        when (result) {
          is SignInCopy.Copied ->
            if (result.missing.isEmpty()) {
              stringResource(Res.string.settings_login_copied, result.cookies)
            } else {
              stringResource(
                Res.string.settings_login_copied_missing,
                result.cookies,
                result.missing.joinToString(", "),
              )
            }
          SignInCopy.NoExtension -> stringResource(Res.string.settings_login_no_extension)
          SignInCopy.SharingOff -> stringResource(Res.string.settings_login_sharing_off)
          is SignInCopy.Failed ->
            stringResource(Res.string.settings_login_copy_failed, result.message)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
  }
}

/** A card whose title row opens and closes it. */
@Composable
private fun Section(
  title: String,
  open: Boolean,
  onToggle: () -> Unit,
  content: @Composable () -> Unit,
) {
  val motion = MaterialTheme.motionScheme
  val toggleLabel =
    stringResource(
      if (open) Res.string.settings_section_close else Res.string.settings_section_open
    )
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.fillMaxWidth()
        .clickable(role = Role.Button, onClickLabel = toggleLabel, onClick = onToggle)
        .padding(horizontal = 16.dp, vertical = 16.dp)
        .semantics(mergeDescendants = true) { heading() },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.weight(1f),
      )
      Icon(
        Icons.Rounded.ExpandMore,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f },
      )
    }
    AnimatedVisibility(
      visible = open,
      enter =
        if (animationsEnabled())
          expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
        else fadeIn(motion.fastEffectsSpec()),
      exit =
        if (animationsEnabled())
          shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
        else fadeOut(motion.fastEffectsSpec()),
    ) {
      Column(Modifier.padding(bottom = 8.dp)) { content() }
    }
  }
}

@Composable
private fun SwitchRow(
  title: StringResource,
  hint: StringResource,
  checked: Boolean,
  enabled: Boolean,
  onChange: (Boolean) -> Unit,
) {
  ListItem(
    headlineContent = { Text(stringResource(title)) },
    supportingContent = { Text(stringResource(hint)) },
    trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onChange,
      ),
  )
}

@Composable
private fun FrequencyPicker(cronRule: String?, enabled: Boolean, onPick: (String?) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val current = SCAN_FREQUENCIES.firstOrNull { it.cronRule == cronRule }
  val label =
    if (current != null) stringResource(current.label)
    else stringResource(Res.string.settings_frequency_custom, cronRule.orEmpty())
  Column(
    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      stringResource(Res.string.settings_frequency_title),
      style = MaterialTheme.typography.bodyLarge,
    )
    Text(
      stringResource(Res.string.settings_frequency_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = it && enabled },
    ) {
      OutlinedTextField(
        value = label,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(Res.string.settings_frequency_title)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier =
          Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        SCAN_FREQUENCIES.forEach { option ->
          DropdownMenuItem(
            text = { Text(stringResource(option.label)) },
            leadingIcon =
              if (ScannerSettings.isRisky(option.cronRule)) {
                {
                  Icon(
                    Icons.Rounded.Warning,
                    contentDescription = stringResource(Res.string.settings_frequency_risky),
                    tint = MaterialTheme.colorScheme.error,
                  )
                }
              } else {
                null
              },
            onClick = {
              expanded = false
              onPick(option.cronRule)
            },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
          )
        }
      }
    }
    if (ScannerSettings.isRisky(cronRule)) {
      Text(
        stringResource(Res.string.settings_frequency_risky_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComputersSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
  state.computers.forEach { computer ->
    val status =
      when {
        !computer.active -> stringResource(Res.string.settings_desktop_other)
        state.phoneConnected -> stringResource(Res.string.settings_desktop_connected)
        else -> stringResource(Res.string.settings_desktop_offline)
      }
    ListItem(
      headlineContent = { Text(computer.label) },
      supportingContent = {
        // A computer without a name already shows its address as the title.
        Text(if (computer.name != null) "$status · ${computer.address}" else status)
      },
      leadingContent = {
        RadioButton(selected = computer.active, onClick = null)
      },
      trailingContent = {
        IconButton(onClick = { onEvent(SettingsUiEvent.RequestForget(computer)) }) {
          Icon(
            Icons.Rounded.LinkOff,
            stringResource(Res.string.settings_unpair_description, computer.label),
          )
        }
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      modifier =
        Modifier.selectable(
          selected = computer.active,
          role = Role.RadioButton,
          onClick = { onEvent(SettingsUiEvent.SelectComputer(computer.key)) },
        ),
    )
  }
  FlowRow(
    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedButton(
      onClick = { onEvent(SettingsUiEvent.CheckConnection) },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        if (state.phoneConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(Res.string.settings_desktop_check),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    OutlinedButton(
      onClick = { onEvent(SettingsUiEvent.AddComputer) },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.Rounded.Add,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(Res.string.settings_desktop_add),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PairingSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    val pairing = state.pairing
    if (pairing == null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(
          Icons.Rounded.WifiOff,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.error,
        )
        Text(
          stringResource(Res.string.settings_pairing_off),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    } else {
      Text(
        stringResource(Res.string.settings_pairing_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (pairing != null) PairingQr(pairing)
      Column(
        Modifier.widthIn(min = 240.dp, max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (pairing != null) {
          Text(
            stringResource(Res.string.settings_pairing_address),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          SelectionContainer {
            Text(pairing.baseUrl, style = MaterialTheme.typography.titleMedium)
          }
        }
        PortField(state, onEvent)
        OutlinedButton(
          onClick = { onEvent(SettingsUiEvent.RequestRotate) },
          enabled = !state.rotating,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(
            Icons.Rounded.LockReset,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
          )
          Text(
            stringResource(Res.string.settings_rotate_button),
            Modifier.padding(start = ButtonDefaults.IconSpacing),
          )
        }
      }
    }
  }
}

@Composable
private fun PairingQr(pairing: PairingInfo) {
  val painter = rememberQrCodePainter(pairing.pairingCode)
  // Dark modules on white in both themes: phone scanners read that best.
  Box(Modifier.size(212.dp).background(Color.White, MaterialTheme.shapes.medium).padding(12.dp)) {
    Image(
      painter,
      contentDescription =
        stringResource(Res.string.settings_pairing_qr_description, pairing.baseUrl),
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
private fun PortField(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
  val range = SettingsViewModel.PORT_RANGE
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = state.portInput,
      onValueChange = { onEvent(SettingsUiEvent.PortChanged(it)) },
      label = { Text(stringResource(Res.string.settings_port_label)) },
      singleLine = true,
      isError = !state.portValid,
      supportingText =
        if (state.portValid) null
        else {
          { Text(stringResource(Res.string.settings_port_error, range.first, range.last)) }
        },
      keyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { onEvent(SettingsUiEvent.SavePort) }),
      modifier = Modifier.fillMaxWidth(),
    )
    FilledTonalButton(
      onClick = { onEvent(SettingsUiEvent.SavePort) },
      enabled = state.canSavePort,
      shapes = ButtonDefaults.shapes(),
    ) {
      Text(stringResource(Res.string.settings_port_save))
    }
  }
}

/** The scanning section while its settings can't be read: the computer is out of reach. */
@Composable
private fun ScannerUnavailable(onRetry: () -> Unit) {
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.settings_load_failed),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
    )
    OutlinedButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
      Text(stringResource(Res.string.common_retry))
    }
  }
}
