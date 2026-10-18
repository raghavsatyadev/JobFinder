@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.connection_banner_fix
import io.github.raghavsatyadev.jobfinder.resources.connection_banner_title
import io.github.raghavsatyadev.jobfinder.resources.doctor_again
import io.github.raghavsatyadev.jobfinder.resources.doctor_all_good
import io.github.raghavsatyadev.jobfinder.resources.doctor_check_app
import io.github.raghavsatyadev.jobfinder.resources.doctor_check_found
import io.github.raghavsatyadev.jobfinder.resources.doctor_check_port
import io.github.raghavsatyadev.jobfinder.resources.doctor_check_same_network
import io.github.raghavsatyadev.jobfinder.resources.doctor_check_token
import io.github.raghavsatyadev.jobfinder.resources.doctor_check_wifi
import io.github.raghavsatyadev.jobfinder.resources.doctor_close
import io.github.raghavsatyadev.jobfinder.resources.doctor_fix_pair
import io.github.raghavsatyadev.jobfinder.resources.doctor_fix_wifi
import io.github.raghavsatyadev.jobfinder.resources.doctor_intro
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_address_updated
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_app_error
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_not_announced
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_not_jobfinder
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_other_computer
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_other_network
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_port_refused
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_port_timeout
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_token_rejected
import io.github.raghavsatyadev.jobfinder.resources.doctor_problem_wifi_off
import io.github.raghavsatyadev.jobfinder.resources.doctor_state_failed
import io.github.raghavsatyadev.jobfinder.resources.doctor_state_passed
import io.github.raghavsatyadev.jobfinder.resources.doctor_state_running
import io.github.raghavsatyadev.jobfinder.resources.doctor_state_skipped
import io.github.raghavsatyadev.jobfinder.resources.doctor_state_waiting
import io.github.raghavsatyadev.jobfinder.resources.doctor_state_warning
import io.github.raghavsatyadev.jobfinder.resources.doctor_title
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckKind
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorCheckState
import io.github.raghavsatyadev.jobfinder.ui.platform.DoctorProblem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The connection doctor, full screen over the app. Runs the checks when it opens. */
@Composable
fun ConnectionDoctorDialog(viewModel: ConnectionDoctorViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) { viewModel.onEvent(DoctorUiEvent.Run) }
  Dialog(
    onDismissRequest = { viewModel.onEvent(DoctorUiEvent.Close) },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    ConnectionDoctorContent(state, viewModel::onEvent)
  }
}

/** The stateless doctor screen, for [ConnectionDoctorDialog] and previews. */
@Composable
fun ConnectionDoctorContent(
  state: DoctorUiState,
  onEvent: (DoctorUiEvent) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState())
    ) {
      Column(
        Modifier.align(Alignment.TopCenter)
          .widthIn(max = 560.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(Res.string.doctor_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
          )
          IconButton(onClick = { onEvent(DoctorUiEvent.Close) }) {
            Icon(Icons.Rounded.Close, stringResource(Res.string.doctor_close))
          }
        }
        Text(
          stringResource(Res.string.doctor_intro, state.computer),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          val checks =
            state.checks.ifEmpty {
              DoctorCheckKind.entries.map { DoctorCheck(it, DoctorCheckState.WAITING) }
            }
          Column(Modifier.padding(vertical = 8.dp)) {
            checks.forEach { CheckRow(it, onEvent) }
          }
        }
        if (state.allGood) {
          Text(
            stringResource(Res.string.doctor_all_good),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
        Button(
          onClick = { onEvent(DoctorUiEvent.Run) },
          enabled = !state.running,
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.align(Alignment.End),
        ) {
          Text(stringResource(Res.string.doctor_again))
        }
      }
    }
  }
}

@Composable
private fun CheckRow(check: DoctorCheck, onEvent: (DoctorUiEvent) -> Unit) {
  val stateLabel = stringResource(check.state.label)
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).semantics {
      liveRegion = LiveRegionMode.Polite
    },
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      StateIcon(check.state)
      Text(
        stringResource(check.kind.label),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
      )
      Text(
        stateLabel,
        style = MaterialTheme.typography.labelLarge,
        color = check.state.color(),
      )
    }
    val problem = check.problem
    if (problem != null && check.state != DoctorCheckState.SKIPPED) {
      Text(
        stringResource(problem.text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 36.dp),
      )
      val fix = problem.fix
      if (fix != null && check.state == DoctorCheckState.FAILED) {
        OutlinedButton(
          onClick = { onEvent(fix.second) },
          shapes = ButtonDefaults.shapes(),
          modifier = Modifier.padding(start = 36.dp),
        ) {
          Text(stringResource(fix.first))
        }
      }
    }
  }
}

@Composable
private fun StateIcon(state: DoctorCheckState) {
  val size = Modifier.size(24.dp)
  when (state) {
    DoctorCheckState.RUNNING -> LoadingIndicator(size)
    DoctorCheckState.WAITING ->
      Icon(Icons.Rounded.RadioButtonUnchecked, null, size, tint = state.color())
    DoctorCheckState.PASSED -> Icon(Icons.Rounded.CheckCircle, null, size, tint = state.color())
    DoctorCheckState.WARNING -> Icon(Icons.Rounded.Warning, null, size, tint = state.color())
    DoctorCheckState.FAILED -> Icon(Icons.Rounded.Error, null, size, tint = state.color())
    DoctorCheckState.SKIPPED ->
      Icon(Icons.Rounded.RemoveCircleOutline, null, size, tint = state.color())
  }
}

@Composable
private fun DoctorCheckState.color(): Color =
  when (this) {
    DoctorCheckState.PASSED -> MaterialTheme.colorScheme.primary
    DoctorCheckState.WARNING -> MaterialTheme.colorScheme.tertiary
    DoctorCheckState.FAILED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }

private val DoctorCheckState.label: StringResource
  get() =
    when (this) {
      DoctorCheckState.WAITING -> Res.string.doctor_state_waiting
      DoctorCheckState.RUNNING -> Res.string.doctor_state_running
      DoctorCheckState.PASSED -> Res.string.doctor_state_passed
      DoctorCheckState.WARNING -> Res.string.doctor_state_warning
      DoctorCheckState.FAILED -> Res.string.doctor_state_failed
      DoctorCheckState.SKIPPED -> Res.string.doctor_state_skipped
    }

private val DoctorCheckKind.label: StringResource
  get() =
    when (this) {
      DoctorCheckKind.WIFI -> Res.string.doctor_check_wifi
      DoctorCheckKind.SAME_NETWORK -> Res.string.doctor_check_same_network
      DoctorCheckKind.FOUND -> Res.string.doctor_check_found
      DoctorCheckKind.PORT -> Res.string.doctor_check_port
      DoctorCheckKind.APP -> Res.string.doctor_check_app
      DoctorCheckKind.TOKEN -> Res.string.doctor_check_token
    }

private val DoctorProblem.text: StringResource
  get() =
    when (this) {
      DoctorProblem.WIFI_OFF -> Res.string.doctor_problem_wifi_off
      DoctorProblem.OTHER_NETWORK -> Res.string.doctor_problem_other_network
      DoctorProblem.NOT_ANNOUNCED -> Res.string.doctor_problem_not_announced
      DoctorProblem.ADDRESS_UPDATED -> Res.string.doctor_problem_address_updated
      DoctorProblem.PORT_TIMEOUT -> Res.string.doctor_problem_port_timeout
      DoctorProblem.PORT_REFUSED -> Res.string.doctor_problem_port_refused
      DoctorProblem.NOT_JOBFINDER -> Res.string.doctor_problem_not_jobfinder
      DoctorProblem.OTHER_COMPUTER -> Res.string.doctor_problem_other_computer
      DoctorProblem.APP_ERROR -> Res.string.doctor_problem_app_error
      DoctorProblem.TOKEN_REJECTED -> Res.string.doctor_problem_token_rejected
    }

/** The button that fixes this problem, when the phone can do something about it. */
private val DoctorProblem.fix: Pair<StringResource, DoctorUiEvent>?
  get() =
    when (this) {
      DoctorProblem.WIFI_OFF,
      DoctorProblem.OTHER_NETWORK -> Res.string.doctor_fix_wifi to DoctorUiEvent.OpenWifiSettings
      DoctorProblem.NOT_JOBFINDER,
      DoctorProblem.OTHER_COMPUTER,
      DoctorProblem.TOKEN_REJECTED -> Res.string.doctor_fix_pair to DoctorUiEvent.PairAgain
      else -> null
    }

/**
 * Shown over the app when the active computer can't be reached; [onFix] opens the doctor. Pads for
 * the status bar itself, so the app below it should not.
 */
@Composable
fun ConnectionBanner(computer: String, onFix: () -> Unit, modifier: Modifier = Modifier) {
  Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
    Row(
      Modifier.windowInsetsPadding(WindowInsets.statusBars)
        .padding(start = 16.dp, end = 4.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        Icons.Rounded.WifiOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.size(18.dp),
      )
      Text(
        stringResource(Res.string.connection_banner_title, computer),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onErrorContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = onFix,
        shapes = ButtonDefaults.shapes(),
        colors =
          ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.onErrorContainer
          ),
      ) {
        Text(stringResource(Res.string.connection_banner_fix))
      }
    }
  }
}
