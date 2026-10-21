@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_add
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_check
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_connected
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_offline
import io.github.raghavsatyadev.jobfinder.resources.settings_desktop_other
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_address
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_hint
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_off
import io.github.raghavsatyadev.jobfinder.resources.settings_pairing_qr_description
import io.github.raghavsatyadev.jobfinder.resources.settings_port_error
import io.github.raghavsatyadev.jobfinder.resources.settings_port_label
import io.github.raghavsatyadev.jobfinder.resources.settings_port_save
import io.github.raghavsatyadev.jobfinder.resources.settings_rotate_button
import io.github.raghavsatyadev.jobfinder.resources.settings_unpair_description
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingInfo
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ComputersSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
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
internal fun PairingSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
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
