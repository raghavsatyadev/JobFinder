@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.opencode_found
import io.github.raghavsatyadev.jobfinder.resources.opencode_found_plain
import io.github.raghavsatyadev.jobfinder.resources.opencode_hint
import io.github.raghavsatyadev.jobfinder.resources.opencode_install
import io.github.raghavsatyadev.jobfinder.resources.opencode_install_command
import io.github.raghavsatyadev.jobfinder.resources.opencode_keeps_prompts
import io.github.raghavsatyadev.jobfinder.resources.opencode_missing
import io.github.raghavsatyadev.jobfinder.resources.opencode_model
import io.github.raghavsatyadev.jobfinder.resources.opencode_sign_in
import io.github.raghavsatyadev.jobfinder.resources.opencode_title
import io.github.raghavsatyadev.jobfinder.shared.model.OpenCodeChoice
import io.github.raghavsatyadev.jobfinder.shared.model.OpenCodeModels
import io.github.raghavsatyadev.jobfinder.ui.platform.OpenCodeStatus
import org.jetbrains.compose.resources.stringResource

/**
 * The OpenCode CLI row of Settings → Free online AI: installed or not, how to install and sign in
 * (a hint only, never required), on/off, and the model to try first.
 */
@Composable
internal fun OpenCodeSection(
  status: OpenCodeStatus,
  choice: OpenCodeChoice,
  onEvent: (OnlineKeysUiEvent) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.toggleable(
          value = choice.enabled,
          enabled = status.installed || choice.enabled,
          role = Role.Switch,
          onValueChange = { onEvent(OnlineKeysUiEvent.SetOpenCode(it)) },
        ),
    ) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(Res.string.opencode_title), style = MaterialTheme.typography.titleSmall)
        Text(
          stringResource(Res.string.opencode_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(
        checked = choice.enabled,
        onCheckedChange = null,
        enabled = status.installed || choice.enabled,
        modifier = Modifier.padding(start = 12.dp),
      )
    }
    if (status.installed) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.CheckCircle,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(end = 6.dp),
        )
        Text(
          status.version?.let { stringResource(Res.string.opencode_found, it) }
            ?: stringResource(Res.string.opencode_found_plain),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    } else {
      InstallHint()
    }
    Text(
      stringResource(Res.string.opencode_sign_in),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (choice.enabled) {
      Text(stringResource(Res.string.opencode_model), style = MaterialTheme.typography.bodyMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OpenCodeModels.FREE.forEach { model ->
          FilterChip(
            selected = choice.model == model,
            onClick = { onEvent(OnlineKeysUiEvent.SetOpenCodeModel(model)) },
            label = { Text(model) },
          )
        }
      }
      Text(
        stringResource(Res.string.opencode_keeps_prompts),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.tertiary,
      )
    }
  }
}

@Composable
private fun InstallHint() {
  val uriHandler = LocalUriHandler.current
  Text(
    stringResource(Res.string.opencode_missing),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error,
  )
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      stringResource(Res.string.opencode_install_command),
      style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
      modifier = Modifier.weight(1f),
    )
    TextButton(
      onClick = { runCatching { uriHandler.openUri(INSTALL_PAGE) } },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.AutoMirrored.Rounded.OpenInNew,
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
      )
      Text(stringResource(Res.string.opencode_install))
    }
  }
}

private const val INSTALL_PAGE = "https://opencode.ai/download"
