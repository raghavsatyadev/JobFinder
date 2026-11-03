@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ai_keys_unavailable
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.ic_delete_filled
import io.github.raghavsatyadev.jobfinder.resources.keys_add
import io.github.raghavsatyadev.jobfinder.resources.keys_adding
import io.github.raghavsatyadev.jobfinder.resources.keys_detected
import io.github.raghavsatyadev.jobfinder.resources.keys_edit
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_label
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_limits_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_model
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_per_day
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_per_minute
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_save
import io.github.raghavsatyadev.jobfinder.resources.keys_edit_tokens
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_save
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_set
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_tip
import io.github.raghavsatyadev.jobfinder.resources.keys_field
import io.github.raghavsatyadev.jobfinder.resources.keys_guide_title
import io.github.raghavsatyadev.jobfinder.resources.keys_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_remove
import io.github.raghavsatyadev.jobfinder.resources.keys_used_requests
import io.github.raghavsatyadev.jobfinder.resources.keys_used_requests_open
import io.github.raghavsatyadev.jobfinder.resources.keys_used_tokens
import io.github.raghavsatyadev.jobfinder.resources.keys_yours
import io.github.raghavsatyadev.jobfinder.resources.task_answers
import io.github.raghavsatyadev.jobfinder.resources.task_ats
import io.github.raghavsatyadev.jobfinder.resources.task_ghost
import io.github.raghavsatyadev.jobfinder.resources.task_insights
import io.github.raghavsatyadev.jobfinder.resources.task_matching
import io.github.raghavsatyadev.jobfinder.resources.task_parsing
import io.github.raghavsatyadev.jobfinder.resources.task_tips
import io.github.raghavsatyadev.jobfinder.resources.task_translate
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

/** The Free online AI section of Settings, from its ViewModel. */
@Composable
fun OnlineKeysSettings() {
  val viewModel = koinViewModel<OnlineKeysViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  OnlineKeysContent(state, viewModel::onEvent)
}

internal val TASKS: List<Pair<AiTask, StringResource>> =
  listOf(
    AiTask.MATCHING to Res.string.task_matching,
    AiTask.PARSING to Res.string.task_parsing,
    AiTask.INSIGHTS to Res.string.task_insights,
    AiTask.GHOST to Res.string.task_ghost,
    AiTask.ATS to Res.string.task_ats,
    AiTask.TIPS to Res.string.task_tips,
    AiTask.ANSWERS to Res.string.task_answers,
    AiTask.TRANSLATE to Res.string.task_translate,
  )

@Composable
fun OnlineKeysContent(state: OnlineKeysUiState, onEvent: (OnlineKeysUiEvent) -> Unit) {
  val pool = state.pool
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.keys_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!pool.available) {
      Text(
        stringResource(Res.string.ai_keys_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      return@Column
    }
    // No key needed, so it comes first.
    OpenCodeSection(pool.openCode, pool.routing.openCode, onEvent)
    HorizontalDivider()
    AgySection(pool.agy, pool.routing.agy, onEvent)
    HorizontalDivider()
    if (pool.keys.isNotEmpty()) {
      Text(stringResource(Res.string.keys_yours), style = MaterialTheme.typography.titleSmall)
      pool.keys
        .sortedBy { FreeAiCatalog.rank(it.provider) }
        .forEach { key -> KeyRow(key, state, onEvent) }
    }
    Text(
      stringResource(Res.string.keys_expiry_tip, AiKey.SUGGESTED_EXPIRY_DAYS),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.tertiary,
    )
    OutlinedTextField(
      value = state.draft,
      onValueChange = { onEvent(OnlineKeysUiEvent.SetDraft(it)) },
      label = { Text(stringResource(Res.string.keys_field)) },
      supportingText =
        state.detected?.let { found ->
          { Text(stringResource(Res.string.keys_detected, stringResource(providerName(found)))) }
        },
      singleLine = true,
      enabled = !state.adding,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      modifier = Modifier.fillMaxWidth(),
    )
    DaysField(
      state.expiryDays,
      enabled = !state.adding,
      onChange = { onEvent(OnlineKeysUiEvent.SetExpiryDays(it)) },
    )
    Button(
      onClick = { onEvent(OnlineKeysUiEvent.Add) },
      enabled = !state.adding && state.draft.isNotBlank(),
      shapes = ButtonDefaults.shapes(),
    ) {
      Text(stringResource(Res.string.keys_add))
    }
    if (state.adding) {
      Text(stringResource(Res.string.keys_adding), style = MaterialTheme.typography.bodyMedium)
      WavyProgress()
    }
    state.notice?.let { KeyNoticeText(it) }
    HorizontalDivider()
    Text(stringResource(Res.string.keys_guide_title), style = MaterialTheme.typography.titleSmall)
    FreeAiCatalog.TIERS.forEach { TierRow(it) }
    if (pool.keys.isNotEmpty() || pool.routing.openCode.enabled || pool.routing.agy.enabled) {
      HorizontalDivider()
      Routing(pool, onEvent)
    }
  }
}

@Composable
internal fun KeyRow(key: AiKey, state: OnlineKeysUiState, onEvent: (OnlineKeysUiEvent) -> Unit) {
  val name = key.label ?: stringResource(providerName(key.provider))
  val used = state.pool.usage.firstOrNull { it.keyId == key.id }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.bodyLarge)
      val requests = used?.requests ?: 0
      val tokens = used?.tokens ?: 0
      val line = buildList {
        add(
          key.requestsPerDay?.let { stringResource(Res.string.keys_used_requests, requests, it) }
            ?: stringResource(Res.string.keys_used_requests_open, requests)
        )
        key.tokensPerDay?.let {
          add(stringResource(Res.string.keys_used_tokens, shortCount(tokens), shortCount(it)))
        }
      }
      Text(
        line.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        key.defaultModel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      ExpiryLine(key, state.pool.now)
    }
    TextButton(
      onClick = { onEvent(OnlineKeysUiEvent.EditKey(key.id)) },
      shapes = ButtonDefaults.shapes(),
    ) {
      Text(stringResource(Res.string.keys_edit))
    }
    TextButton(
      onClick = { onEvent(OnlineKeysUiEvent.EditExpiry(key.id)) },
      shapes = ButtonDefaults.shapes(),
    ) {
      Text(stringResource(Res.string.keys_expiry_set))
    }
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Remove(key.id)) },
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(
        vectorResource(Res.drawable.ic_delete_filled),
        contentDescription = stringResource(Res.string.keys_remove, name),
      )
    }
  }
  state.editingKey?.takeIf { it.id == key.id }?.let { KeyEditor(it, state.keyModels, onEvent) }
  if (state.editingExpiry == key.id) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DaysField(
        state.editDays,
        enabled = true,
        onChange = { onEvent(OnlineKeysUiEvent.SetEditDays(it)) },
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = { onEvent(OnlineKeysUiEvent.CancelExpiry) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.common_cancel))
      }
      Button(
        onClick = { onEvent(OnlineKeysUiEvent.SaveExpiry) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.keys_expiry_save))
      }
    }
  }
}

/** A key's name, model (with the site's models to pick from) and limits. */
@Composable
internal fun KeyEditor(edit: KeyEdit, models: List<String>, onEvent: (OnlineKeysUiEvent) -> Unit) {
  val set = { e: KeyEdit -> onEvent(OnlineKeysUiEvent.SetKeyEdit(e)) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = edit.label,
      onValueChange = { set(edit.copy(label = it.take(80))) },
      label = { Text(stringResource(Res.string.keys_edit_label)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
      value = edit.model,
      onValueChange = { set(edit.copy(model = it.take(200))) },
      label = { Text(stringResource(Res.string.keys_edit_model)) },
      singleLine = true,
      modifier = Modifier.fillMaxWidth(),
    )
    val typed = edit.model.trim().lowercase()
    val offered = models.filter { typed.isEmpty() || typed in it.lowercase() }.take(MODEL_CHOICES)
    if (offered.isNotEmpty() && offered != listOf(edit.model.trim())) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        offered.forEach { model ->
          SuggestionChip(onClick = { set(edit.copy(model = model)) }, label = { Text(model) })
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      NumberField(
        edit.perMinute,
        Res.string.keys_edit_per_minute,
        Modifier.weight(1f),
      ) {
        set(edit.copy(perMinute = it))
      }
      NumberField(edit.perDay, Res.string.keys_edit_per_day, Modifier.weight(1f)) {
        set(edit.copy(perDay = it))
      }
      NumberField(edit.tokensPerDay, Res.string.keys_edit_tokens, Modifier.weight(1f)) {
        set(edit.copy(tokensPerDay = it))
      }
    }
    Text(
      stringResource(Res.string.keys_edit_limits_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      TextButton(
        onClick = { onEvent(OnlineKeysUiEvent.CancelKey) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.common_cancel))
      }
      Button(
        onClick = { onEvent(OnlineKeysUiEvent.SaveKey) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.keys_edit_save))
      }
    }
  }
}
