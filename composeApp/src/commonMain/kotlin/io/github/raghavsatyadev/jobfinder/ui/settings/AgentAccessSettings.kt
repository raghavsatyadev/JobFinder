@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.agents_address
import io.github.raghavsatyadev.jobfinder.resources.agents_client_claude
import io.github.raghavsatyadev.jobfinder.resources.agents_client_codex
import io.github.raghavsatyadev.jobfinder.resources.agents_client_gemini
import io.github.raghavsatyadev.jobfinder.resources.agents_client_other
import io.github.raghavsatyadev.jobfinder.resources.agents_copied
import io.github.raghavsatyadev.jobfinder.resources.agents_copy_title
import io.github.raghavsatyadev.jobfinder.resources.agents_hint
import io.github.raghavsatyadev.jobfinder.resources.agents_off
import io.github.raghavsatyadev.jobfinder.resources.agents_port_in_use
import io.github.raghavsatyadev.jobfinder.resources.agents_rotate
import io.github.raghavsatyadev.jobfinder.resources.agents_rotate_confirm
import io.github.raghavsatyadev.jobfinder.resources.agents_rotate_text
import io.github.raghavsatyadev.jobfinder.resources.agents_rotate_title
import io.github.raghavsatyadev.jobfinder.resources.agents_rotated
import io.github.raghavsatyadev.jobfinder.resources.agents_try
import io.github.raghavsatyadev.jobfinder.resources.agents_where_claude
import io.github.raghavsatyadev.jobfinder.resources.agents_where_codex
import io.github.raghavsatyadev.jobfinder.resources.agents_where_gemini
import io.github.raghavsatyadev.jobfinder.resources.agents_where_other
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentAccess
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentClient
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentEndpoint
import io.github.raghavsatyadev.jobfinder.ui.platform.AgentSetups
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** What the section last did, shown under its buttons for a while. */
enum class AgentNotice {
  COPIED,
  ROTATED,
}

/** Settings → AI agents (MCP), on the desktop's [AgentAccess]. */
@Composable
fun AgentAccessSettings() {
  val access = koinInject<AgentAccess>()
  val endpoint by access.endpoint.collectAsState()
  @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
  var notice by remember { mutableStateOf<Pair<AgentNotice, AgentClient?>?>(null) }
  LaunchedEffect(notice) {
    if (notice != null) {
      delay(NOTICE_MS)
      notice = null
    }
  }
  AgentAccessContent(
    endpoint,
    notice,
    onCopy = { client ->
      val url = endpoint.url ?: return@AgentAccessContent
      clipboard.setText(AnnotatedString(AgentSetups.text(client, url, access.token())))
      notice = AgentNotice.COPIED to client
    },
    onRotate = {
      access.rotateToken()
      notice = AgentNotice.ROTATED to null
    },
  )
}

/** The stateless section, for [AgentAccessSettings] and previews. */
@Composable
fun AgentAccessContent(
  endpoint: AgentEndpoint,
  notice: Pair<AgentNotice, AgentClient?>?,
  onCopy: (AgentClient) -> Unit,
  onRotate: () -> Unit,
) {
  var confirmRotate by remember { mutableStateOf(false) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      stringResource(Res.string.agents_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(stringResource(Res.string.agents_address), style = MaterialTheme.typography.titleSmall)
    when {
      endpoint.url != null ->
        Text(
          endpoint.url,
          style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
      endpoint.portInUse ->
        Text(
          stringResource(Res.string.agents_port_in_use),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      else ->
        Text(stringResource(Res.string.agents_off), style = MaterialTheme.typography.bodyMedium)
    }
    Text(
      stringResource(Res.string.agents_copy_title),
      style = MaterialTheme.typography.titleSmall,
      modifier = Modifier.padding(top = 4.dp),
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AgentClient.entries.forEach { client ->
        OutlinedButton(
          onClick = { onCopy(client) },
          enabled = endpoint.url != null,
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
          )
          Text(stringResource(client.label))
        }
      }
    }
    when (notice?.first) {
      AgentNotice.COPIED ->
        Text(
          stringResource(
            Res.string.agents_copied,
            notice.second?.let { stringResource(it.where) }.orEmpty(),
          ) + " " + stringResource(Res.string.agents_try),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      AgentNotice.ROTATED ->
        Text(
          stringResource(Res.string.agents_rotated),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
        )
      null -> Unit
    }
    // Off (no protected store, port taken): there is no token to replace.
    TextButton(
      onClick = { confirmRotate = true },
      enabled = endpoint.url != null,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.Rounded.LockReset,
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
      )
      Text(stringResource(Res.string.agents_rotate))
    }
  }
  if (confirmRotate) {
    ConfirmDialog(
      icon = Icons.Rounded.LockReset,
      title = stringResource(Res.string.agents_rotate_title),
      text = stringResource(Res.string.agents_rotate_text),
      confirmLabel = stringResource(Res.string.agents_rotate_confirm),
      onConfirm = {
        confirmRotate = false
        onRotate()
      },
      onDismiss = { confirmRotate = false },
    )
  }
}

private val AgentClient.label: StringResource
  get() =
    when (this) {
      AgentClient.CLAUDE_CODE -> Res.string.agents_client_claude
      AgentClient.GEMINI_CLI -> Res.string.agents_client_gemini
      AgentClient.CODEX -> Res.string.agents_client_codex
      AgentClient.OTHER -> Res.string.agents_client_other
    }

private val AgentClient.where: StringResource
  get() =
    when (this) {
      AgentClient.CLAUDE_CODE -> Res.string.agents_where_claude
      AgentClient.GEMINI_CLI -> Res.string.agents_where_gemini
      AgentClient.CODEX -> Res.string.agents_where_codex
      AgentClient.OTHER -> Res.string.agents_where_other
    }

private const val NOTICE_MS = 8_000L
