@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied_missing
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copying
import io.github.raghavsatyadev.jobfinder.resources.settings_login_no_extension
import io.github.raghavsatyadev.jobfinder.resources.settings_login_sharing_off
import io.github.raghavsatyadev.jobfinder.resources.setup_action_copy_sign_ins
import io.github.raghavsatyadev.jobfinder.resources.setup_action_extension
import io.github.raghavsatyadev.jobfinder.resources.setup_action_fix
import io.github.raghavsatyadev.jobfinder.resources.setup_action_retry
import io.github.raghavsatyadev.jobfinder.resources.setup_action_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_action_sign_in
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_full
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_keys
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_online
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_pc
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_small
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_split
import io.github.raghavsatyadev.jobfinder.resources.setup_ai_title
import io.github.raghavsatyadev.jobfinder.resources.setup_all_done
import io.github.raghavsatyadev.jobfinder.resources.setup_back
import io.github.raghavsatyadev.jobfinder.resources.setup_bytes_gb
import io.github.raghavsatyadev.jobfinder.resources.setup_check_again
import io.github.raghavsatyadev.jobfinder.resources.setup_close
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_cancel
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_go
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_install
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_model
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_permission
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_start
import io.github.raghavsatyadev.jobfinder.resources.setup_confirm_title
import io.github.raghavsatyadev.jobfinder.resources.setup_finish
import io.github.raghavsatyadev.jobfinder.resources.setup_fix_ai
import io.github.raghavsatyadev.jobfinder.resources.setup_fix_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_next
import io.github.raghavsatyadev.jobfinder.resources.setup_page_ai
import io.github.raghavsatyadev.jobfinder.resources.setup_page_ai_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_page_ai_title
import io.github.raghavsatyadev.jobfinder.resources.setup_page_done
import io.github.raghavsatyadev.jobfinder.resources.setup_page_of
import io.github.raghavsatyadev.jobfinder.resources.setup_page_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_page_phone_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_page_phone_title
import io.github.raghavsatyadev.jobfinder.resources.setup_page_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_page_searches_intro
import io.github.raghavsatyadev.jobfinder.resources.setup_page_searches_title
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_blocked
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_download_failed
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_install_failed
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_needs_search
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_needs_server
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_no_firewall_rule
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_no_network
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_ollama_missing
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_ollama_stopped
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_permission_declined
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_port_in_use
import io.github.raghavsatyadev.jobfinder.resources.setup_problem_public_network
import io.github.raghavsatyadev.jobfinder.resources.setup_progress
import io.github.raghavsatyadev.jobfinder.resources.setup_qr_description
import io.github.raghavsatyadev.jobfinder.resources.setup_state_checking
import io.github.raghavsatyadev.jobfinder.resources.setup_state_done
import io.github.raghavsatyadev.jobfinder.resources.setup_state_failed
import io.github.raghavsatyadev.jobfinder.resources.setup_state_not_needed
import io.github.raghavsatyadev.jobfinder.resources.setup_state_todo
import io.github.raghavsatyadev.jobfinder.resources.setup_state_working
import io.github.raghavsatyadev.jobfinder.resources.setup_step_extension
import io.github.raghavsatyadev.jobfinder.resources.setup_step_firewall
import io.github.raghavsatyadev.jobfinder.resources.setup_step_model
import io.github.raghavsatyadev.jobfinder.resources.setup_step_network
import io.github.raghavsatyadev.jobfinder.resources.setup_step_ollama
import io.github.raghavsatyadev.jobfinder.resources.setup_step_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_step_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_step_server
import io.github.raghavsatyadev.jobfinder.resources.setup_step_sign_in
import io.github.raghavsatyadev.jobfinder.resources.setup_text_extension
import io.github.raghavsatyadev.jobfinder.resources.setup_text_model
import io.github.raghavsatyadev.jobfinder.resources.setup_text_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_text_phone_qr
import io.github.raghavsatyadev.jobfinder.resources.setup_text_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_text_sign_in
import io.github.raghavsatyadev.jobfinder.resources.setup_text_sign_in_copy
import io.github.raghavsatyadev.jobfinder.resources.setup_text_signing_in
import io.github.raghavsatyadev.jobfinder.resources.setup_title
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelTier
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupPage
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupProblem
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStep
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepKind
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepState
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import io.github.raghavsatyadev.jobfinder.ui.settings.TierRow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val setupUiModule = module {
  viewModel { SetupViewModel(get(), getOrNull(), getOrNull()) }
  viewModel { AssistantHelpViewModel(get(), get(), get(), get()) }
}

/** The setup assistant, full screen over the app. */
@Composable
fun SetupDialog(viewModel: SetupViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  Dialog(
    onDismissRequest = { viewModel.onEvent(SetupUiEvent.Close) },
    // A full-screen page: only the close button and Escape or Back close it.
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
  ) {
    SetupContent(state, viewModel::onEvent, helpers = { AssistantHelpFor(it) })
  }
  if (state.confirming) ConfirmDialog(state, viewModel::onEvent)
}

/** The stateless setup screen, for [SetupDialog] and previews: one page of steps at a time. */
@Composable
fun SetupContent(
  state: SetupUiState,
  onEvent: (SetupUiEvent) -> Unit,
  modifier: Modifier = Modifier,
  /** Extra cards under a page's steps: the AI-assistant helpers. */
  helpers: @Composable (SetupPage) -> Unit = {},
) {
  val pages = SetupPage.entries
  val index = pages.indexOf(state.page)
  Surface(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
        Column(
          Modifier.align(Alignment.TopCenter)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              stringResource(Res.string.setup_title),
              style = MaterialTheme.typography.headlineMedium,
              modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = { onEvent(SetupUiEvent.Close) }) {
              Icon(Icons.Rounded.Close, stringResource(Res.string.setup_close))
            }
          }
          Text(
            stringResource(Res.string.setup_intro),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          PageTabs(state, onEvent)
          PageHeader(state.page, index, pages.size)
          if (state.page == SetupPage.AI) {
            state.profile?.takeIf { it.tier != ModelTier.UNKNOWN }?.let { AiPathCard(it) }
          }
          Card(
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(vertical = 8.dp)) {
              state.pageSteps.forEachIndexed { i, step ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                StepRow(step, state, onEvent)
              }
            }
          }
          helpers(state.page)
          val fix = state.page.fixLabel
          if (fix != null && state.plan.isNotEmpty()) {
            FilledTonalButton(
              onClick = { onEvent(SetupUiEvent.FixPage) },
              shapes = ButtonDefaults.shapes(),
            ) {
              Text(stringResource(fix))
            }
          }
          if (state.allDone && index == pages.lastIndex) {
            Text(
              stringResource(Res.string.setup_all_done),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
          }
        }
      }
      HorizontalDivider()
      Row(
        Modifier.align(Alignment.CenterHorizontally)
          .widthIn(max = 640.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (index > 0) {
          TextButton(onClick = { onEvent(SetupUiEvent.GoTo(pages[index - 1])) }) {
            Text(stringResource(Res.string.setup_back))
          }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(
          onClick = { onEvent(SetupUiEvent.CheckAgain) },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.setup_check_again))
        }
        Button(
          onClick = {
            if (index < pages.lastIndex) onEvent(SetupUiEvent.GoTo(pages[index + 1]))
            else onEvent(SetupUiEvent.Close)
          },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(
            stringResource(
              if (index < pages.lastIndex) Res.string.setup_next else Res.string.setup_finish
            )
          )
        }
      }
    }
  }
}

/** One chip per page, with a check mark once its steps are done. */
@Composable
private fun PageTabs(state: SetupUiState, onEvent: (SetupUiEvent) -> Unit) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    SetupPage.entries.forEach { page ->
      val done = state.pageDone(page)
      val name = stringResource(page.label)
      val described = stringResource(Res.string.setup_page_done, name)
      FilterChip(
        selected = page == state.page,
        onClick = { onEvent(SetupUiEvent.GoTo(page)) },
        label = { Text(name) },
        leadingIcon = {
          Icon(
            if (done) Icons.Rounded.CheckCircle else page.icon,
            contentDescription = null,
            modifier = Modifier.size(FilterChipDefaults.IconSize),
          )
        },
        modifier = if (done) Modifier.semantics { contentDescription = described } else Modifier,
      )
    }
  }
}

@Composable
private fun PageHeader(page: SetupPage, index: Int, count: Int) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      stringResource(Res.string.setup_page_of, index + 1, count),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      stringResource(page.title),
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.semantics { heading() },
    )
    Text(
      stringResource(page.intro),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private val SetupPage.label: StringResource
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_page_ai
      SetupPage.SEARCHES -> Res.string.setup_page_searches
      SetupPage.PHONE -> Res.string.setup_page_phone
    }

private val SetupPage.title: StringResource
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_page_ai_title
      SetupPage.SEARCHES -> Res.string.setup_page_searches_title
      SetupPage.PHONE -> Res.string.setup_page_phone_title
    }

private val SetupPage.intro: StringResource
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_page_ai_intro
      SetupPage.SEARCHES -> Res.string.setup_page_searches_intro
      SetupPage.PHONE -> Res.string.setup_page_phone_intro
    }

private val SetupPage.icon: ImageVector
  get() =
    when (this) {
      SetupPage.AI -> Icons.Rounded.AutoAwesome
      SetupPage.SEARCHES -> Icons.Rounded.TravelExplore
      SetupPage.PHONE -> Icons.Rounded.PhoneAndroid
    }

/** The page's button for its automatic steps; `null` when it has none. */
private val SetupPage.fixLabel: StringResource?
  get() =
    when (this) {
      SetupPage.AI -> Res.string.setup_fix_ai
      SetupPage.PHONE -> Res.string.setup_fix_phone
      SetupPage.SEARCHES -> null
    }

/** On the sign-in step: copy the sign-ins from the user's own browser through the extension. */
@Composable
private fun CopySignIns(state: SetupUiState, onEvent: (SetupUiEvent) -> Unit, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(Res.string.setup_text_sign_in_copy),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.canCopySignIns) {
      OutlinedButton(
        onClick = { onEvent(SetupUiEvent.CopySignIns) },
        enabled = !state.copyingSignIns,
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(
          stringResource(
            if (state.copyingSignIns) Res.string.settings_login_copying
            else Res.string.setup_action_copy_sign_ins
          )
        )
      }
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
      )
    }
  }
}

@Composable
private fun StepRow(step: SetupStep, state: SetupUiState, onEvent: (SetupUiEvent) -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics {
      liveRegion = LiveRegionMode.Polite
    },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      StateIcon(step.state)
      Text(
        stringResource(step.kind.label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
      )
      Text(
        stringResource(
          if (step.problem == SetupProblem.NOT_NEEDED_ONLINE) Res.string.setup_state_not_needed
          else step.state.label
        ),
        style = MaterialTheme.typography.labelLarge,
        color = step.state.color(),
      )
    }
    if (step.state == SetupStepState.DONE || step.state == SetupStepState.CHECKING) return@Column
    val indent = Modifier.padding(start = 36.dp)
    detail(step, state)?.let {
      Text(
        it,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = indent,
      )
    }
    if (step.state == SetupStepState.WORKING) {
      Progress(step, indent)
      return@Column
    }
    if (step.kind == SetupStepKind.PHONE) {
      state.pairing?.let { PairingQr(it, indent) }
      return@Column
    }
    step.action?.let { action ->
      OutlinedButton(
        onClick = { onEvent(SetupUiEvent.Act(step.kind)) },
        shapes = ButtonDefaults.shapes(),
        modifier = indent,
      ) {
        Text(stringResource(action))
      }
    }
    if (step.kind == SetupStepKind.SIGN_IN) CopySignIns(state, onEvent, indent)
  }
}

/** Why the step is not done, or what the user does for it. */
@Composable
private fun detail(step: SetupStep, state: SetupUiState): String? {
  step.problem?.let {
    return stringResource(it.text)
  }
  return when (step.kind) {
    SetupStepKind.SIGN_IN ->
      stringResource(
        if (step.state == SetupStepState.WORKING) Res.string.setup_text_signing_in
        else Res.string.setup_text_sign_in
      )
    SetupStepKind.SEARCHES -> stringResource(Res.string.setup_text_searches)
    SetupStepKind.EXTENSION -> stringResource(Res.string.setup_text_extension)
    SetupStepKind.PHONE ->
      stringResource(
        if (state.pairing == null) Res.string.setup_text_phone else Res.string.setup_text_phone_qr,
        state.computerName,
      )
    SetupStepKind.MODEL ->
      if (step.state == SetupStepState.WORKING) null
      else stringResource(Res.string.setup_text_model, gb(state.modelBytes))
    else -> null
  }
}

@Composable
private fun Progress(step: SetupStep, modifier: Modifier) {
  val done = step.doneBytes
  val total = step.totalBytes
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (done != null && total != null && total > 0) {
      WavyProgress((done.toFloat() / total).coerceIn(0f, 1f))
      Text(
        stringResource(Res.string.setup_progress, gb(done), gb(total)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      WavyProgress()
    }
  }
}

@Composable
private fun PairingQr(pairing: PairingInfo, modifier: Modifier) {
  val painter = rememberQrCodePainter(pairing.pairingCode)
  // Dark modules on white in both themes: phone scanners read that best.
  Box(modifier.size(180.dp).background(Color.White, MaterialTheme.shapes.medium).padding(10.dp)) {
    Image(
      painter,
      contentDescription = stringResource(Res.string.setup_qr_description),
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
private fun ConfirmDialog(state: SetupUiState, onEvent: (SetupUiEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(SetupUiEvent.Cancel) },
    title = { Text(stringResource(Res.string.setup_confirm_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.setup_confirm_intro))
        state.plan.forEach { item ->
          val text =
            when (item) {
              SetupPlanItem.PERMISSION -> stringResource(Res.string.setup_confirm_permission)
              SetupPlanItem.INSTALL_OLLAMA -> stringResource(Res.string.setup_confirm_install)
              SetupPlanItem.START_OLLAMA -> stringResource(Res.string.setup_confirm_start)
              SetupPlanItem.DOWNLOAD_MODEL ->
                stringResource(Res.string.setup_confirm_model, gb(state.modelBytes))
            }
          Text("•  $text")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onEvent(SetupUiEvent.Confirm) }) {
        Text(stringResource(Res.string.setup_confirm_go))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(SetupUiEvent.Cancel) }) {
        Text(stringResource(Res.string.setup_confirm_cancel))
      }
    },
  )
}

@Composable
private fun StateIcon(state: SetupStepState) {
  val size = Modifier.size(24.dp)
  when (state) {
    SetupStepState.CHECKING,
    SetupStepState.WORKING -> LoadingIndicator(size)
    SetupStepState.DONE -> Icon(Icons.Rounded.CheckCircle, null, size, tint = state.color())
    SetupStepState.TODO ->
      Icon(Icons.Rounded.RadioButtonUnchecked, null, size, tint = state.color())
    SetupStepState.FAILED -> Icon(Icons.Rounded.Error, null, size, tint = state.color())
  }
}

@Composable
private fun gb(bytes: Long): String = stringResource(Res.string.setup_bytes_gb, gigabytes(bytes))

@Composable
private fun SetupStepState.color(): Color =
  when (this) {
    SetupStepState.DONE -> MaterialTheme.colorScheme.primary
    SetupStepState.FAILED -> MaterialTheme.colorScheme.error
    SetupStepState.TODO -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

private val SetupStepState.label: StringResource
  get() =
    when (this) {
      SetupStepState.CHECKING -> Res.string.setup_state_checking
      SetupStepState.DONE -> Res.string.setup_state_done
      SetupStepState.TODO -> Res.string.setup_state_todo
      SetupStepState.WORKING -> Res.string.setup_state_working
      SetupStepState.FAILED -> Res.string.setup_state_failed
    }

private val SetupStepKind.label: StringResource
  get() =
    when (this) {
      SetupStepKind.SERVER -> Res.string.setup_step_server
      SetupStepKind.NETWORK -> Res.string.setup_step_network
      SetupStepKind.FIREWALL -> Res.string.setup_step_firewall
      SetupStepKind.OLLAMA -> Res.string.setup_step_ollama
      SetupStepKind.MODEL -> Res.string.setup_step_model
      SetupStepKind.SIGN_IN -> Res.string.setup_step_sign_in
      SetupStepKind.SEARCHES -> Res.string.setup_step_searches
      SetupStepKind.PHONE -> Res.string.setup_step_phone
      SetupStepKind.EXTENSION -> Res.string.setup_step_extension
    }

/** The step's button, when the user can do something from here. */
private val SetupStep.action: StringResource?
  get() =
    when {
      problem == SetupProblem.NEEDS_SEARCH -> null
      kind == SetupStepKind.SIGN_IN -> Res.string.setup_action_sign_in
      kind == SetupStepKind.SEARCHES -> Res.string.setup_action_searches
      kind == SetupStepKind.EXTENSION -> Res.string.setup_action_extension
      !kind.automatic || problem == SetupProblem.NEEDS_SERVER -> null
      state == SetupStepState.FAILED -> Res.string.setup_action_retry
      else -> Res.string.setup_action_fix
    }

private val SetupProblem.text: StringResource
  get() =
    when (this) {
      SetupProblem.NO_NETWORK -> Res.string.setup_problem_no_network
      SetupProblem.PORT_IN_USE -> Res.string.setup_problem_port_in_use
      SetupProblem.NEEDS_SERVER -> Res.string.setup_problem_needs_server
      SetupProblem.PUBLIC_NETWORK -> Res.string.setup_problem_public_network
      SetupProblem.NO_FIREWALL_RULE -> Res.string.setup_problem_no_firewall_rule
      SetupProblem.BLOCKED_BY_FIREWALL -> Res.string.setup_problem_blocked
      SetupProblem.PERMISSION_DECLINED -> Res.string.setup_problem_permission_declined
      SetupProblem.OLLAMA_MISSING -> Res.string.setup_problem_ollama_missing
      SetupProblem.OLLAMA_STOPPED -> Res.string.setup_problem_ollama_stopped
      SetupProblem.INSTALL_FAILED -> Res.string.setup_problem_install_failed
      SetupProblem.DOWNLOAD_FAILED -> Res.string.setup_problem_download_failed
      SetupProblem.NEEDS_SEARCH -> Res.string.setup_problem_needs_search
      SetupProblem.NOT_NEEDED_ONLINE -> Res.string.setup_state_not_needed
    }

/** Offline or online first, from this computer's memory and graphics card. */
@Composable
private fun AiPathCard(profile: PcProfile) {
  val tier = profile.tier
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(Res.string.setup_ai_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        stringResource(
          Res.string.setup_ai_pc,
          wholeGb(profile.ramGb),
          wholeGb(profile.vramGb),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        stringResource(
          when (tier) {
            ModelTier.FULL -> Res.string.setup_ai_full
            ModelTier.SPLIT -> Res.string.setup_ai_split
            ModelTier.MEDIUM,
            ModelTier.SMALL -> Res.string.setup_ai_small
            ModelTier.ONLINE,
            ModelTier.UNKNOWN -> Res.string.setup_ai_online
          }
        ),
        style = MaterialTheme.typography.bodyLarge,
      )
      if (tier != ModelTier.FULL) {
        Text(
          stringResource(Res.string.setup_ai_keys),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FreeAiCatalog.TIERS.forEach { TierRow(it) }
      }
    }
  }
}

private fun wholeGb(gb: Double?): String =
  gb?.let { kotlin.math.round(it).toLong().toString() } ?: "?"
