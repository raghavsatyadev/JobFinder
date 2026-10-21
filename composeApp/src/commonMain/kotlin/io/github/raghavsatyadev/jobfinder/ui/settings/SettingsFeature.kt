@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.help_title
import io.github.raghavsatyadev.jobfinder.resources.nav_back
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_12_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_1_day
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_1_week
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_2_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_3_days
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_4_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_8_hours
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_never
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risk_confirm
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risk_text
import io.github.raghavsatyadev.jobfinder.resources.settings_frequency_risk_title
import io.github.raghavsatyadev.jobfinder.resources.settings_port_saved
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_confirm
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_text
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_title
import io.github.raghavsatyadev.jobfinder.resources.settings_rotated
import io.github.raghavsatyadev.jobfinder.resources.settings_save_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_title
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_button
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_text
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_title
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoice
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import io.github.raghavsatyadev.jobfinder.ui.resume.ResumePdfViewModel
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
  viewModel { ResumePdfViewModel(get(), get()) }
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

/**
 * Settings: the section menu, or with [section] that section's own page. [onOpenSection] opens a
 * page from the menu and [onBack] leaves a page. On a wide window the page sits beside the menu:
 * [selected] marks its row there and [backArrow] `false` drops the page's back arrow.
 */
@Composable
fun SettingsScreen(
  section: SettingsSection? = null,
  onOpenSection: (SettingsSection) -> Unit = {},
  onBack: (() -> Unit)? = null,
  onOpenHelp: (() -> Unit)? = null,
  selected: SettingsSection? = null,
  backArrow: Boolean = true,
) {
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
    section = section,
    onOpenSection = onOpenSection,
    onBack = onBack,
    onOpenHelp = onOpenHelp,
    selected = selected,
    backArrow = backArrow,
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
  /** The AI model part of the AI section, desktop only; `null` leaves it out. */
  ai: (@Composable () -> Unit)? = null,
  /** The free online AI keys part of the AI section, desktop only; `null` leaves it out. */
  keys: (@Composable () -> Unit)? = null,
  /** The saved application form answers, desktop only; `null` leaves it out. */
  answers: (@Composable () -> Unit)? = null,
  /** The MCP server for AI agents, desktop only; `null` leaves it out. */
  agents: (@Composable () -> Unit)? = null,
  /** The section whose page shows; `null` shows the menu. */
  section: SettingsSection? = null,
  /** The menu's search at first; empty in the app. */
  initialQuery: String = "",
  onOpenSection: (SettingsSection) -> Unit = {},
  /** Adds a back arrow; on a page whose section this device lacks, it is also called at once. */
  onBack: (() -> Unit)? = null,
  /** Opens Help from the menu's top bar; `null` hides the button. */
  onOpenHelp: (() -> Unit)? = null,
  /** The section open beside the menu on a wide window, marked in the menu. */
  selected: SettingsSection? = null,
  /** `false` hides the back arrow, when the menu sits beside the page. */
  backArrow: Boolean = true,
) {
  val entries = settingsEntries(state, onEvent, resume, backup, layout, ai, keys, answers, agents)
  val page = section?.let { wanted -> entries.firstOrNull { it.section == wanted } }
  // A paired computer forgotten, say, takes its section away: leave that page.
  val missing = section != null && page == null && !state.loading
  LaunchedEffect(missing) { if (missing) onBack?.invoke() }
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(section?.title ?: Res.string.settings_title)) },
        navigationIcon = {
          if (onBack != null && backArrow) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
              Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.nav_back),
              )
            }
          }
        },
        actions = {
          if (section == null && onOpenHelp != null) {
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
      // Without the scanner settings (computer out of reach, none kept) the other sections still
      // show: backups, resume and the paired computers work on the phone's own copy.
      when {
        state.loading && state.scanner == null -> LoadingState()
        page != null -> SettingsPage(page)
        section == null -> SettingsMenu(entries, initialQuery, onOpenSection, selected)
      }
    }
  }
  SettingsDialogs(state, onEvent)
}

@Composable
private fun SettingsDialogs(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
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
