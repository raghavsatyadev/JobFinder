@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.pairing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.pairing_approval_cancel
import io.github.raghavsatyadev.jobfinder.resources.pairing_approval_code_description
import io.github.raghavsatyadev.jobfinder.resources.pairing_approval_sending
import io.github.raghavsatyadev.jobfinder.resources.pairing_approval_text
import io.github.raghavsatyadev.jobfinder.resources.pairing_approval_title
import io.github.raghavsatyadev.jobfinder.resources.pairing_close
import io.github.raghavsatyadev.jobfinder.resources.pairing_contacting
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_address
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_busy
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_code
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_denied
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_expired
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_silent
import io.github.raghavsatyadev.jobfinder.resources.pairing_error_unreachable
import io.github.raghavsatyadev.jobfinder.resources.pairing_found_hint
import io.github.raghavsatyadev.jobfinder.resources.pairing_found_new
import io.github.raghavsatyadev.jobfinder.resources.pairing_found_saved
import io.github.raghavsatyadev.jobfinder.resources.pairing_found_searching
import io.github.raghavsatyadev.jobfinder.resources.pairing_found_title
import io.github.raghavsatyadev.jobfinder.resources.pairing_in_progress
import io.github.raghavsatyadev.jobfinder.resources.pairing_intro
import io.github.raghavsatyadev.jobfinder.resources.pairing_manual_hint
import io.github.raghavsatyadev.jobfinder.resources.pairing_manual_label
import io.github.raghavsatyadev.jobfinder.resources.pairing_manual_submit
import io.github.raghavsatyadev.jobfinder.resources.pairing_manual_title
import io.github.raghavsatyadev.jobfinder.resources.pairing_saved_switch
import io.github.raghavsatyadev.jobfinder.resources.pairing_saved_title
import io.github.raghavsatyadev.jobfinder.resources.pairing_scan
import io.github.raghavsatyadev.jobfinder.resources.pairing_scan_failed
import io.github.raghavsatyadev.jobfinder.resources.pairing_title
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingError
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Koin bindings of the pairing and doctor screens; their platform parts are bound on Android only.
 */
val pairingUiModule = module {
  viewModel { PairingViewModel(get()) }
  viewModel { ConnectionDoctorViewModel(get(), get()) }
}

/**
 * The pairing screen: while no computer is paired, and when adding another. Lists the computers on
 * this Wi-Fi; one tap asks that computer, where the user allows the phone. A typed address or a
 * scanned or pasted code work too.
 */
@Composable
fun PairingScreen(viewModel: PairingViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) { viewModel.watchNetwork() }
  PairingContent(state, viewModel::onEvent)
}

/** The stateless pairing screen, for [PairingScreen] and previews. */
@Composable
fun PairingContent(
  state: PairingUiState,
  onEvent: (PairingUiEvent) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .imePadding()
        .verticalScroll(rememberScrollState())
    ) {
      Column(
        Modifier.align(Alignment.TopCenter)
          .widthIn(max = 520.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            Icons.Rounded.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
          )
          Spacer(Modifier.weight(1f))
          if (state.canClose) {
            IconButton(onClick = { onEvent(PairingUiEvent.Close) }) {
              Icon(Icons.Rounded.Close, stringResource(Res.string.pairing_close))
            }
          }
        }
        Text(
          stringResource(Res.string.pairing_title),
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          stringResource(Res.string.pairing_intro),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The card waits for the computer to take the request, so it never flashes up and away.
        val approval = state.approval?.takeIf { it.code != null }
        if (approval != null) {
          ApprovalCard(approval) { onEvent(PairingUiEvent.CancelApproval) }
        } else {
          FoundSection(state, onEvent)
          Status(state)
          if (state.saved.isNotEmpty()) SavedSection(state, onEvent)
          ManualSection(state, onEvent)
          ScanButton(state, onEvent)
        }
      }
    }
  }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { heading() },
    )
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
      shape = MaterialTheme.shapes.large,
      modifier = Modifier.fillMaxWidth(),
    ) {
      content()
    }
  }
}

@Composable
private fun ComputerRow(
  name: String,
  detail: String,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(name) },
    supportingContent = { Text(detail) },
    leadingContent = { Icon(Icons.Rounded.Computer, contentDescription = null) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick).testTag(testTag),
  )
}

@Composable
private fun FoundSection(state: PairingUiState, onEvent: (PairingUiEvent) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionCard(stringResource(Res.string.pairing_found_title)) {
      state.found.forEach { computer ->
        val status =
          stringResource(
            if (computer.savedKey != null) Res.string.pairing_found_saved
            else Res.string.pairing_found_new
          )
        ComputerRow(
          computer.name,
          "$status · ${computer.address}",
          enabled = !state.busy,
          testTag = "found-computer",
        ) {
          onEvent(PairingUiEvent.PickFound(computer))
        }
      }
      Row(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        LoadingIndicator(Modifier.size(32.dp))
        Text(
          stringResource(Res.string.pairing_found_searching),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(
      stringResource(Res.string.pairing_found_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun SavedSection(state: PairingUiState, onEvent: (PairingUiEvent) -> Unit) {
  SectionCard(stringResource(Res.string.pairing_saved_title)) {
    val hint = stringResource(Res.string.pairing_saved_switch)
    state.saved.forEach { computer ->
      ComputerRow(
        computer.label,
        if (computer.name != null) "$hint · ${computer.address}" else hint,
        enabled = !state.busy,
        testTag = "saved-computer",
      ) {
        onEvent(PairingUiEvent.PickSaved(computer.key))
      }
    }
  }
}

@Composable
private fun ManualSection(state: PairingUiState, onEvent: (PairingUiEvent) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.pairing_manual_title),
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { heading() },
    )
    OutlinedTextField(
      value = state.code,
      onValueChange = { onEvent(PairingUiEvent.CodeChanged(it)) },
      label = { Text(stringResource(Res.string.pairing_manual_label)) },
      supportingText = { Text(stringResource(Res.string.pairing_manual_hint)) },
      enabled = !state.busy,
      maxLines = 4,
      keyboardOptions =
        KeyboardOptions(
          autoCorrectEnabled = false,
          keyboardType = KeyboardType.Uri,
          imeAction = ImeAction.Done,
        ),
      keyboardActions = KeyboardActions(onDone = { onEvent(PairingUiEvent.Submit) }),
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = { onEvent(PairingUiEvent.Submit) },
      enabled = state.canSubmit,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.align(Alignment.End),
    ) {
      Icon(
        Icons.Rounded.Link,
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
      )
      Text(
        stringResource(Res.string.pairing_manual_submit),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
  }
}

@Composable
private fun ScanButton(state: PairingUiState, onEvent: (PairingUiEvent) -> Unit) {
  OutlinedButton(
    onClick = { onEvent(PairingUiEvent.Scan) },
    enabled = !state.busy,
    shapes = ButtonDefaults.shapes(),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Icon(
      Icons.Rounded.QrCodeScanner,
      contentDescription = null,
      modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Text(
      stringResource(Res.string.pairing_scan),
      Modifier.padding(start = ButtonDefaults.IconSpacing),
    )
  }
}

/** The request waiting on the computer, with the code the user compares there. */
@Composable
private fun ApprovalCard(approval: PendingApproval, onCancel: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
  ) {
    Column(
      Modifier.fillMaxWidth().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        stringResource(Res.string.pairing_approval_title, approval.computer),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.semantics { heading() },
      )
      val code = approval.code
      if (code == null) {
        LoadingIndicator(Modifier.size(48.dp))
        Text(stringResource(Res.string.pairing_approval_sending))
      } else {
        Text(
          stringResource(Res.string.pairing_approval_text),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
        val description = stringResource(Res.string.pairing_approval_code_description, code)
        Text(
          code.chunked(CODE_GROUP).joinToString(" "),
          style = MaterialTheme.typography.displayMedium,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.semantics { contentDescription = description },
        )
        LoadingIndicator(Modifier.size(40.dp))
      }
      OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.pairing_approval_cancel))
      }
    }
  }
}

/** The check in progress, or why the last attempt failed. Read out when it changes. */
@Composable
private fun Status(state: PairingUiState) {
  val message =
    when {
      state.busy -> null
      state.scanFailed -> Res.string.pairing_scan_failed
      else ->
        when (state.error) {
          PairingError.NOT_A_PAIRING_CODE -> Res.string.pairing_error_code
          PairingError.NOT_A_LAN_ADDRESS -> Res.string.pairing_error_address
          PairingError.DESKTOP_DID_NOT_ANSWER -> Res.string.pairing_error_unreachable
          PairingError.DENIED -> Res.string.pairing_error_denied
          PairingError.EXPIRED -> Res.string.pairing_error_expired
          PairingError.BUSY -> Res.string.pairing_error_busy
          null -> return
        }
    }
  Row(
    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val contacting = state.approval?.computer
    val silent = state.silentComputer
    if (message == null) {
      LoadingIndicator(Modifier.size(40.dp))
      Text(
        if (contacting != null) stringResource(Res.string.pairing_contacting, contacting)
        else stringResource(Res.string.pairing_in_progress),
        style = MaterialTheme.typography.bodyLarge,
      )
    } else {
      Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
      Text(
        if (silent != null && message == Res.string.pairing_error_unreachable)
          stringResource(Res.string.pairing_error_silent, silent)
        else stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

private const val CODE_GROUP = 3
