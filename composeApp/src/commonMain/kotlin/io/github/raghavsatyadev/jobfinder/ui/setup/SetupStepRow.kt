@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copied_missing
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copy_failed
import io.github.raghavsatyadev.jobfinder.resources.settings_login_copying
import io.github.raghavsatyadev.jobfinder.resources.settings_login_no_extension
import io.github.raghavsatyadev.jobfinder.resources.settings_login_sharing_off
import io.github.raghavsatyadev.jobfinder.resources.setup_action_app_browser
import io.github.raghavsatyadev.jobfinder.resources.setup_action_copy_sign_ins
import io.github.raghavsatyadev.jobfinder.resources.setup_action_extension
import io.github.raghavsatyadev.jobfinder.resources.setup_action_fix
import io.github.raghavsatyadev.jobfinder.resources.setup_action_retry
import io.github.raghavsatyadev.jobfinder.resources.setup_action_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_action_sign_in
import io.github.raghavsatyadev.jobfinder.resources.setup_bytes_gb
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
import io.github.raghavsatyadev.jobfinder.resources.setup_text_app_browser_failed
import io.github.raghavsatyadev.jobfinder.resources.setup_text_app_browser_opened
import io.github.raghavsatyadev.jobfinder.resources.setup_text_extension
import io.github.raghavsatyadev.jobfinder.resources.setup_text_model
import io.github.raghavsatyadev.jobfinder.resources.setup_text_phone
import io.github.raghavsatyadev.jobfinder.resources.setup_text_phone_qr
import io.github.raghavsatyadev.jobfinder.resources.setup_text_searches
import io.github.raghavsatyadev.jobfinder.resources.setup_text_sign_in
import io.github.raghavsatyadev.jobfinder.resources.setup_text_sign_in_copy
import io.github.raghavsatyadev.jobfinder.resources.setup_text_signing_in
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupProblem
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStep
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepKind
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupStepState
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
internal fun StepRow(step: SetupStep, state: SetupUiState, onEvent: (SetupUiEvent) -> Unit) {
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
    if (step.kind == SetupStepKind.EXTENSION) return@Column ExtensionActions(state, onEvent, indent)
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

/** On the extension step: its folder, and the app's own browser to add it to. */
@Composable
private fun ExtensionActions(
  state: SetupUiState,
  onEvent: (SetupUiEvent) -> Unit,
  modifier: Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { onEvent(SetupUiEvent.Act(SetupStepKind.EXTENSION)) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.setup_action_extension))
      }
      if (state.appBrowser != null) {
        OutlinedButton(
          onClick = { onEvent(SetupUiEvent.OpenAppBrowser) },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.setup_action_app_browser))
        }
      }
    }
    state.appBrowserOpen?.let { opened ->
      val page = opened.page
      Text(
        if (page != null) {
          stringResource(Res.string.setup_text_app_browser_opened, state.appBrowser.orEmpty(), page)
        } else {
          stringResource(Res.string.setup_text_app_browser_failed)
        },
        style = MaterialTheme.typography.bodyMedium,
        color =
          if (page == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
      )
    }
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
internal fun gb(bytes: Long): String = stringResource(Res.string.setup_bytes_gb, gigabytes(bytes))

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
