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
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.agy_found
import io.github.raghavsatyadev.jobfinder.resources.agy_found_plain
import io.github.raghavsatyadev.jobfinder.resources.agy_hint
import io.github.raghavsatyadev.jobfinder.resources.agy_install
import io.github.raghavsatyadev.jobfinder.resources.agy_level_high
import io.github.raghavsatyadev.jobfinder.resources.agy_level_low
import io.github.raghavsatyadev.jobfinder.resources.agy_level_medium
import io.github.raghavsatyadev.jobfinder.resources.agy_missing
import io.github.raghavsatyadev.jobfinder.resources.agy_model
import io.github.raghavsatyadev.jobfinder.resources.agy_sign_in
import io.github.raghavsatyadev.jobfinder.resources.agy_title
import io.github.raghavsatyadev.jobfinder.shared.model.AgyChoice
import io.github.raghavsatyadev.jobfinder.shared.model.AgyModels
import io.github.raghavsatyadev.jobfinder.ui.platform.AgyStatus
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The Antigravity CLI row of Settings → Free online AI: installed or not, how to install and sign
 * in, on/off, and the thinking level.
 */
@Composable
internal fun AgySection(
  status: AgyStatus,
  choice: AgyChoice,
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
          onValueChange = { onEvent(OnlineKeysUiEvent.SetAgy(it)) },
        ),
    ) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(Res.string.agy_title), style = MaterialTheme.typography.titleSmall)
        Text(
          stringResource(Res.string.agy_hint),
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
          status.version?.let { stringResource(Res.string.agy_found, it) }
            ?: stringResource(Res.string.agy_found_plain),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    } else {
      InstallHint()
    }
    Text(
      stringResource(Res.string.agy_sign_in),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (choice.enabled) {
      Text(stringResource(Res.string.agy_model), style = MaterialTheme.typography.bodyMedium)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AgyModels.LEVELS.forEach { model ->
          FilterChip(
            selected = choice.model == model,
            onClick = { onEvent(OnlineKeysUiEvent.SetAgyModel(model)) },
            label = { Text(levelName(model)?.let { stringResource(it) } ?: model) },
          )
        }
      }
    }
  }
}

private fun levelName(model: String): StringResource? =
  when (model.substringAfterLast('-')) {
    "low" -> Res.string.agy_level_low
    "medium" -> Res.string.agy_level_medium
    "high" -> Res.string.agy_level_high
    else -> null
  }

@Composable
private fun InstallHint() {
  val uriHandler = LocalUriHandler.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      stringResource(Res.string.agy_missing),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
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
      Text(stringResource(Res.string.agy_install))
    }
  }
}

private const val INSTALL_PAGE = "https://antigravity.google/download"
