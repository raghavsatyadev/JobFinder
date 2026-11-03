@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_close
import io.github.raghavsatyadev.jobfinder.resources.common_delete
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.common_save
import io.github.raghavsatyadev.jobfinder.resources.ic_delete_outline_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_edit_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_open_in_browser_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_refresh_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_warning_amber_filled
import io.github.raghavsatyadev.jobfinder.resources.links_broken_explain
import io.github.raghavsatyadev.jobfinder.resources.links_broken_explain_title
import io.github.raghavsatyadev.jobfinder.resources.links_broken_summary
import io.github.raghavsatyadev.jobfinder.resources.links_delete
import io.github.raghavsatyadev.jobfinder.resources.links_delete_text
import io.github.raghavsatyadev.jobfinder.resources.links_delete_title
import io.github.raghavsatyadev.jobfinder.resources.links_edit
import io.github.raghavsatyadev.jobfinder.resources.links_edit_text
import io.github.raghavsatyadev.jobfinder.resources.links_edit_title
import io.github.raghavsatyadev.jobfinder.resources.links_error_title_required
import io.github.raghavsatyadev.jobfinder.resources.links_field_title
import io.github.raghavsatyadev.jobfinder.resources.links_field_title_hint
import io.github.raghavsatyadev.jobfinder.resources.links_field_url
import io.github.raghavsatyadev.jobfinder.resources.links_open_in_browser
import io.github.raghavsatyadev.jobfinder.resources.links_scan_now
import io.github.raghavsatyadev.jobfinder.resources.links_why
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrokenWarning(
  link: Link,
  scanning: Boolean,
  canOpenPage: Boolean,
  onEvent: (LinksEvent) -> Unit,
) {
  // Text buttons on the warning use its own content colour: primary is too faint on it.
  val onWarning =
    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
  Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          vectorResource(Res.drawable.ic_warning_amber_filled),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          stringResource(Res.string.links_broken_summary, link.scrapeFailureCount),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.weight(1f),
        )
      }
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        TextButton(onClick = { onEvent(LinksEvent.Explain(link)) }, colors = onWarning) {
          Text(stringResource(Res.string.links_why))
        }
        if (canOpenPage) {
          TextButton(onClick = { onEvent(LinksEvent.OpenPage(link)) }, colors = onWarning) {
            Icon(vectorResource(Res.drawable.ic_open_in_browser_filled), contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.links_open_in_browser))
          }
        }
        FilledTonalButton(
          onClick = { onEvent(LinksEvent.ScanLink(link)) },
          enabled = !scanning && !LocalComputerOffline.current,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(vectorResource(Res.drawable.ic_refresh_filled), contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text(stringResource(Res.string.links_scan_now))
        }
      }
    }
  }
}

@Composable
internal fun CardActions(
  link: Link,
  scanning: Boolean,
  showOpenPage: Boolean,
  onEvent: (LinksEvent) -> Unit,
) {
  // Scans and changes need the computer.
  val online = !LocalComputerOffline.current
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showOpenPage) {
      IconButton(onClick = { onEvent(LinksEvent.OpenPage(link)) }) {
        Icon(
          vectorResource(Res.drawable.ic_open_in_browser_filled),
          contentDescription = stringResource(Res.string.links_open_in_browser),
        )
      }
    }
    if (!link.isBroken) {
      IconButton(onClick = { onEvent(LinksEvent.ScanLink(link)) }, enabled = !scanning && online) {
        Icon(
          vectorResource(Res.drawable.ic_refresh_filled),
          contentDescription = stringResource(Res.string.links_scan_now),
        )
      }
    }
    IconButton(onClick = { onEvent(LinksEvent.StartEdit(link)) }, enabled = online) {
      Icon(
        vectorResource(Res.drawable.ic_edit_filled),
        contentDescription = stringResource(Res.string.links_edit),
      )
    }
    IconButton(
      onClick = { onEvent(LinksEvent.AskDelete(link)) },
      enabled = online,
      colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
      Icon(
        vectorResource(Res.drawable.ic_delete_outline_filled),
        contentDescription = stringResource(Res.string.links_delete),
      )
    }
  }
}

@Composable
internal fun Dialogs(state: LinksUiState, onEvent: (LinksEvent) -> Unit) {
  state.edit?.let { EditLinkDialog(it, onEvent) }
  state.pendingDelete?.let { link ->
    ConfirmDialog(
      icon = vectorResource(Res.drawable.ic_delete_outline_filled),
      title = stringResource(Res.string.links_delete_title),
      text = stringResource(Res.string.links_delete_text, link.title),
      confirmLabel = stringResource(Res.string.common_delete),
      onConfirm = { onEvent(LinksEvent.ConfirmDelete) },
      onDismiss = { onEvent(LinksEvent.CancelDelete) },
    )
  }
  state.explaining?.let { link ->
    AlertDialog(
      onDismissRequest = { onEvent(LinksEvent.CloseExplain) },
      icon = {
        Icon(vectorResource(Res.drawable.ic_warning_amber_filled), contentDescription = null)
      },
      title = { Text(stringResource(Res.string.links_broken_explain_title)) },
      text = {
        Text(stringResource(Res.string.links_broken_explain, ServerEvent.BROKEN_LINK_THRESHOLD))
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (state.canOpenPages) {
            TextButton(onClick = { onEvent(LinksEvent.OpenPage(link)) }) {
              Text(stringResource(Res.string.links_open_in_browser))
            }
          }
          TextButton(onClick = { onEvent(LinksEvent.ScanLink(link)) }) {
            Text(stringResource(Res.string.links_scan_now))
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { onEvent(LinksEvent.CloseExplain) }) {
          Text(stringResource(Res.string.common_close))
        }
      },
    )
  }
}

@Composable
internal fun EditLinkDialog(edit: LinkEditState, onEvent: (LinksEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { if (!edit.saving) onEvent(LinksEvent.CancelEdit) },
    icon = { Icon(vectorResource(Res.drawable.ic_edit_filled), contentDescription = null) },
    title = { Text(stringResource(Res.string.links_edit_title)) },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          stringResource(Res.string.links_edit_text),
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = edit.title,
          onValueChange = { onEvent(LinksEvent.EditTitle(it)) },
          label = { Text(stringResource(Res.string.links_field_title)) },
          placeholder = { Text(stringResource(Res.string.links_field_title_hint)) },
          isError = edit.titleMissing,
          supportingText =
            if (edit.titleMissing) {
              { Text(stringResource(Res.string.links_error_title_required)) }
            } else null,
          singleLine = true,
          enabled = !edit.saving,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = edit.url,
          onValueChange = { onEvent(LinksEvent.EditUrl(it)) },
          label = { Text(stringResource(Res.string.links_field_url)) },
          isError = edit.urlError != null,
          supportingText = edit.urlError?.let { error -> { Text(urlErrorText(error)) } },
          singleLine = true,
          enabled = !edit.saving,
          keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
          modifier = Modifier.fillMaxWidth(),
        )
        if (edit.saveFailed) {
          Text(
            edit.saveErrorMessage ?: stringResource(Res.string.common_error_generic),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onEvent(LinksEvent.SaveEdit) },
        enabled = !edit.saving,
        shapes = ButtonDefaults.shapes(),
      ) {
        if (edit.saving) {
          LoadingIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
          Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(Res.string.common_save))
      }
    },
    dismissButton = {
      TextButton(onClick = { onEvent(LinksEvent.CancelEdit) }, enabled = !edit.saving) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}
