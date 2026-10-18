@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.agy_chip
import io.github.raghavsatyadev.jobfinder.resources.ai_keys_unavailable
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.keys_add
import io.github.raghavsatyadev.jobfinder.resources.keys_added
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
import io.github.raghavsatyadev.jobfinder.resources.keys_expired
import io.github.raghavsatyadev.jobfinder.resources.keys_expires_in
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_days
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_days_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_none
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_save
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_set
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_tip
import io.github.raghavsatyadev.jobfinder.resources.keys_failed
import io.github.raghavsatyadev.jobfinder.resources.keys_field
import io.github.raghavsatyadev.jobfinder.resources.keys_get
import io.github.raghavsatyadev.jobfinder.resources.keys_guide_title
import io.github.raghavsatyadev.jobfinder.resources.keys_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_keeps_prompts
import io.github.raghavsatyadev.jobfinder.resources.keys_local
import io.github.raghavsatyadev.jobfinder.resources.keys_no_limit
import io.github.raghavsatyadev.jobfinder.resources.keys_per_day
import io.github.raghavsatyadev.jobfinder.resources.keys_per_minute
import io.github.raghavsatyadev.jobfinder.resources.keys_refused
import io.github.raghavsatyadev.jobfinder.resources.keys_remove
import io.github.raghavsatyadev.jobfinder.resources.keys_reset
import io.github.raghavsatyadev.jobfinder.resources.keys_resume_warning
import io.github.raghavsatyadev.jobfinder.resources.keys_route_down
import io.github.raghavsatyadev.jobfinder.resources.keys_route_remove
import io.github.raghavsatyadev.jobfinder.resources.keys_route_up
import io.github.raghavsatyadev.jobfinder.resources.keys_routing_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_routing_title
import io.github.raghavsatyadev.jobfinder.resources.keys_tokens_per_day
import io.github.raghavsatyadev.jobfinder.resources.keys_unknown
import io.github.raghavsatyadev.jobfinder.resources.keys_used_requests
import io.github.raghavsatyadev.jobfinder.resources.keys_used_requests_open
import io.github.raghavsatyadev.jobfinder.resources.keys_used_tokens
import io.github.raghavsatyadev.jobfinder.resources.keys_yours
import io.github.raghavsatyadev.jobfinder.resources.opencode_chip
import io.github.raghavsatyadev.jobfinder.resources.task_answers
import io.github.raghavsatyadev.jobfinder.resources.task_ats
import io.github.raghavsatyadev.jobfinder.resources.task_ghost
import io.github.raghavsatyadev.jobfinder.resources.task_insights
import io.github.raghavsatyadev.jobfinder.resources.task_matching
import io.github.raghavsatyadev.jobfinder.resources.task_parsing
import io.github.raghavsatyadev.jobfinder.resources.task_tips
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.shared.model.FreeTier
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.AddKeyOutcome
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeys
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeysState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** What adding a key last did. */
sealed interface KeyNotice {
  data class Added(val key: AiKey) : KeyNotice

  data object Unknown : KeyNotice

  data object Refused : KeyNotice

  data class Failed(val reason: String) : KeyNotice
}

@Immutable
data class OnlineKeysUiState(
  val loading: Boolean = true,
  val pool: AiKeysState = AiKeysState(),
  /** The key being typed; cleared once added. */
  val draft: String = "",
  val adding: Boolean = false,
  val notice: KeyNotice? = null,
  /** Days until the new key expires, as typed; blank for a key that never expires. */
  val expiryDays: String = AiKey.SUGGESTED_EXPIRY_DAYS.toString(),
  /** The key whose expiry is being set, and the days typed for it. */
  val editingExpiry: String? = null,
  val editDays: String = AiKey.SUGGESTED_EXPIRY_DAYS.toString(),
  /** The key whose name, model and limits are being edited. */
  val editingKey: KeyEdit? = null,
  /** The models the edited key's site offers, once read. */
  val keyModels: List<String> = emptyList(),
) {
  /** The site [draft] looks like it's from, while typing. */
  val detected: AiProvider?
    get() = AiProvider.detect(draft.trim())
}

/** A key's name, model and limits as typed; blank limits keep none. */
@Immutable
data class KeyEdit(
  val id: String,
  val label: String,
  val model: String,
  val perMinute: String,
  val perDay: String,
  val tokensPerDay: String,
) {
  companion object {
    fun of(key: AiKey) =
      KeyEdit(
        id = key.id,
        label = key.label.orEmpty(),
        model = key.defaultModel,
        perMinute = key.requestsPerMinute?.toString().orEmpty(),
        perDay = key.requestsPerDay?.toString().orEmpty(),
        tokensPerDay = key.tokensPerDay?.toString().orEmpty(),
      )
  }
}

sealed interface OnlineKeysUiEvent {
  data class SetDraft(val key: String) : OnlineKeysUiEvent

  data object Add : OnlineKeysUiEvent

  data class SetExpiryDays(val days: String) : OnlineKeysUiEvent

  /** Opens the expiry field of key [id]. */
  data class EditExpiry(val id: String) : OnlineKeysUiEvent

  data class SetEditDays(val days: String) : OnlineKeysUiEvent

  data object SaveExpiry : OnlineKeysUiEvent

  data object CancelExpiry : OnlineKeysUiEvent

  data class Remove(val id: String) : OnlineKeysUiEvent

  data class EditKey(val id: String) : OnlineKeysUiEvent

  data class SetKeyEdit(val edit: KeyEdit) : OnlineKeysUiEvent

  data object SaveKey : OnlineKeysUiEvent

  data object CancelKey : OnlineKeysUiEvent

  /** Moves [route] [by] places in [task]'s order: -1 earlier, 1 later. */
  data class Move(val task: AiTask, val route: AiRoute, val by: Int) : OnlineKeysUiEvent

  /** Turns [route] on or off for [task]. */
  data class Toggle(val task: AiTask, val route: AiRoute) : OnlineKeysUiEvent

  data object ResetRouting : OnlineKeysUiEvent

  /** Turns the OpenCode CLI on or off as a model source. */
  data class SetOpenCode(val enabled: Boolean) : OnlineKeysUiEvent

  data class SetOpenCodeModel(val model: String) : OnlineKeysUiEvent

  /** Turns the Antigravity CLI (`agy`) on or off as a model source. */
  data class SetAgy(val enabled: Boolean) : OnlineKeysUiEvent

  data class SetAgyModel(val model: String) : OnlineKeysUiEvent
}

/** Settings → Free online AI: the keys, today's use and which model does which work. */
class OnlineKeysViewModel(
  private val keys: AiKeys,
  private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
  private val _state = MutableStateFlow(OnlineKeysUiState())
  val state: StateFlow<OnlineKeysUiState> = _state.asStateFlow()

  init {
    reload()
  }

  fun onEvent(event: OnlineKeysUiEvent) {
    when (event) {
      is OnlineKeysUiEvent.SetDraft -> _state.update { it.copy(draft = event.key, notice = null) }
      OnlineKeysUiEvent.Add -> add()
      is OnlineKeysUiEvent.SetExpiryDays ->
        _state.update { it.copy(expiryDays = event.days.filter(Char::isDigit).take(4)) }
      is OnlineKeysUiEvent.EditExpiry ->
        _state.update {
          val key = it.pool.keys.firstOrNull { k -> k.id == event.id }
          val left = key?.daysLeft(clock())?.takeIf { d -> d > 0 }
          it.copy(
            editingExpiry = event.id,
            editDays = (left ?: AiKey.SUGGESTED_EXPIRY_DAYS.toLong()).toString(),
          )
        }
      is OnlineKeysUiEvent.SetEditDays ->
        _state.update { it.copy(editDays = event.days.filter(Char::isDigit).take(4)) }
      OnlineKeysUiEvent.SaveExpiry -> {
        val s = _state.value
        val id = s.editingExpiry ?: return
        _state.update { it.copy(editingExpiry = null) }
        change { keys.setExpiry(id, expiryFrom(s.editDays)) }
      }
      OnlineKeysUiEvent.CancelExpiry -> _state.update { it.copy(editingExpiry = null) }
      is OnlineKeysUiEvent.Remove -> change { keys.remove(event.id) }
      is OnlineKeysUiEvent.EditKey -> editKey(event.id)
      is OnlineKeysUiEvent.SetKeyEdit ->
        _state.update {
          val e = event.edit
          it.copy(
            editingKey =
              e.copy(
                perMinute = e.perMinute.filter(Char::isDigit).take(6),
                perDay = e.perDay.filter(Char::isDigit).take(7),
                tokensPerDay = e.tokensPerDay.filter(Char::isDigit).take(10),
              )
          )
        }
      OnlineKeysUiEvent.SaveKey -> saveKey()
      OnlineKeysUiEvent.CancelKey -> _state.update { it.copy(editingKey = null) }
      is OnlineKeysUiEvent.Move -> move(event.task, event.route, event.by)
      is OnlineKeysUiEvent.Toggle -> toggle(event.task, event.route)
      OnlineKeysUiEvent.ResetRouting ->
        saveRouting(_state.value.pool.routing.copy(routes = emptyMap()))
      is OnlineKeysUiEvent.SetOpenCode -> setOpenCode(event.enabled)
      is OnlineKeysUiEvent.SetOpenCodeModel -> {
        val routing = _state.value.pool.routing
        saveRouting(routing.copy(openCode = routing.openCode.copy(model = event.model)))
      }
      is OnlineKeysUiEvent.SetAgy -> setAgy(event.enabled)
      is OnlineKeysUiEvent.SetAgyModel -> {
        val routing = _state.value.pool.routing
        saveRouting(routing.copy(agy = routing.agy.copy(model = event.model)))
      }
    }
  }

  private fun reload() {
    viewModelScope.launch {
      try {
        val loaded = keys.load()
        _state.update { it.copy(loading = false, pool = loaded) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loading = false) }
      }
    }
  }

  private fun change(action: suspend () -> Unit) {
    viewModelScope.launch {
      try {
        action()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update { it.copy(notice = KeyNotice.Failed(e.message.orEmpty())) }
      }
      reload()
    }
  }

  private fun add() {
    val now = _state.value
    if (now.adding || now.draft.isBlank()) return
    _state.update { it.copy(adding = true, notice = null) }
    viewModelScope.launch {
      val notice =
        try {
          when (val result = keys.add(now.draft, expiryFrom(now.expiryDays))) {
            is AddKeyOutcome.Added -> KeyNotice.Added(result.key)
            AddKeyOutcome.Unknown -> KeyNotice.Unknown
            AddKeyOutcome.Refused -> KeyNotice.Refused
            is AddKeyOutcome.Failed -> KeyNotice.Failed(result.reason)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          KeyNotice.Failed(e.message.orEmpty())
        }
      _state.update {
        it.copy(
          adding = false,
          notice = notice,
          draft = if (notice is KeyNotice.Added) "" else it.draft,
        )
      }
      reload()
    }
  }

  private fun editKey(id: String) {
    val key = _state.value.pool.keys.firstOrNull { it.id == id } ?: return
    _state.update { it.copy(editingKey = KeyEdit.of(key), keyModels = emptyList()) }
    viewModelScope.launch {
      val models =
        try {
          keys.models(id)
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          emptyList()
        }
      _state.update { if (it.editingKey?.id == id) it.copy(keyModels = models.sorted()) else it }
    }
  }

  /** Saves the edit; the key's place in each task then uses its new model. */
  private fun saveKey() {
    val s = _state.value
    val edit = s.editingKey ?: return
    val key = s.pool.keys.firstOrNull { it.id == edit.id } ?: return
    val updated =
      key.copy(
        label = edit.label.trim().takeIf { it.isNotEmpty() },
        model =
          edit.model.trim().takeIf {
            it.isNotEmpty() && it != KeyEdit.of(key.copy(model = null)).model
          },
        requestsPerMinute = edit.perMinute.toIntOrNull()?.takeIf { it > 0 },
        requestsPerDay = edit.perDay.toIntOrNull()?.takeIf { it > 0 },
        tokensPerDay = edit.tokensPerDay.toLongOrNull()?.takeIf { it > 0 },
      )
    val routing = s.pool.routing
    val routes =
      routing.routes.mapValues { (_, list) ->
        list.map { if (it.keyId == key.id) it.copy(model = updated.defaultModel) else it }
      }
    _state.update { it.copy(editingKey = null) }
    change {
      keys.update(updated)
      if (routes != routing.routes) keys.setRouting(routing.copy(routes = routes))
    }
  }

  private fun move(task: AiTask, route: AiRoute, by: Int) {
    val pool = _state.value.pool
    val list = pool.routing.forTask(task, pool.keys).toMutableList()
    val from = list.indexOfFirst { it.keyId == route.keyId }
    val to = from + by
    if (from < 0 || to !in list.indices) return
    list.add(to, list.removeAt(from))
    saveRouting(pool.routing.copy(routes = pool.routing.routes + (task to list)))
  }

  /** [days] from now; `null` for blank or 0 (no expiry). */
  private fun expiryFrom(days: String): Long? =
    days.toLongOrNull()?.takeIf { it > 0 }?.let { clock() + it * AiKey.DAY_MS }

  private fun saveRouting(routing: AiRouting) {
    _state.update { it.copy(pool = it.pool.copy(routing = routing)) }
    change { keys.setRouting(routing) }
  }

  /**
   * Turning OpenCode on also adds it, before this computer, to the public-text tasks the user has
   * already set up; tasks still on the suggested setup get it from there.
   */
  private fun setOpenCode(enabled: Boolean) {
    val routing = _state.value.pool.routing
    saveRouting(
      routing.copy(
        routes = if (enabled) withCli(routing.routes, AiRoute.OPEN_CODE) else routing.routes,
        openCode = routing.openCode.copy(enabled = enabled),
      )
    )
  }

  /** Like [setOpenCode], for the Antigravity CLI. */
  private fun setAgy(enabled: Boolean) {
    val routing = _state.value.pool.routing
    saveRouting(
      routing.copy(
        routes = if (enabled) withCli(routing.routes, AiRoute.AGY) else routing.routes,
        agy = routing.agy.copy(enabled = enabled),
      )
    )
  }

  /** [cli] added before this computer to every public-text task that doesn't have it yet. */
  private fun withCli(
    routes: Map<AiTask, List<AiRoute>>,
    cli: AiRoute,
  ): Map<AiTask, List<AiRoute>> = routes.mapValues { (task, list) ->
    if (task.usesResume || list.any { it.keyId == cli.keyId }) list
    else {
      val local = list.indexOfFirst { it.local }.takeIf { it >= 0 } ?: list.size
      list.take(local) + cli + list.drop(local)
    }
  }

  /** Taking a model out keeps the order; one added goes before this computer, else last. */
  private fun toggle(task: AiTask, route: AiRoute) {
    val pool = _state.value.pool
    val current = pool.routing.forTask(task, pool.keys)
    val on = current.any { it.keyId == route.keyId }
    val next =
      if (on) current.filterNot { it.keyId == route.keyId }
      else {
        val local = current.indexOfFirst { it.local }.takeIf { it >= 0 && !route.local }
        if (local == null) current + route else current.take(local) + route + current.drop(local)
      }
    // A task always keeps one model.
    if (next.isEmpty()) return
    saveRouting(pool.routing.copy(routes = pool.routing.routes + (task to next)))
  }
}

/** Every model a task can use, in the suggested order. */
private fun candidates(keys: List<AiKey>, routing: AiRouting): List<AiRoute> =
  keys.sortedBy { FreeAiCatalog.rank(it.provider) }.map { AiRoute(it.id, it.defaultModel) } +
    listOfNotNull(
      AiRoute.OPEN_CODE.takeIf { routing.openCode.enabled },
      AiRoute.AGY.takeIf { routing.agy.enabled },
    ) +
    AiRoute.LOCAL

/** The Free online AI section of Settings, from its ViewModel. */
@Composable
fun OnlineKeysSettings() {
  val viewModel = koinViewModel<OnlineKeysViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  OnlineKeysContent(state, viewModel::onEvent)
}

private val TASKS: List<Pair<AiTask, StringResource>> =
  listOf(
    AiTask.MATCHING to Res.string.task_matching,
    AiTask.PARSING to Res.string.task_parsing,
    AiTask.INSIGHTS to Res.string.task_insights,
    AiTask.GHOST to Res.string.task_ghost,
    AiTask.ATS to Res.string.task_ats,
    AiTask.TIPS to Res.string.task_tips,
    AiTask.ANSWERS to Res.string.task_answers,
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
private fun KeyRow(key: AiKey, state: OnlineKeysUiState, onEvent: (OnlineKeysUiEvent) -> Unit) {
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
      Icon(Icons.Rounded.Delete, contentDescription = stringResource(Res.string.keys_remove, name))
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
private fun KeyEditor(edit: KeyEdit, models: List<String>, onEvent: (OnlineKeysUiEvent) -> Unit) {
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

@Composable
private fun NumberField(
  value: String,
  label: StringResource,
  modifier: Modifier,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

/** At most this many of the site's models show under the model field. */
private const val MODEL_CHOICES = 12

/** Days left on the key: plain, then a warning in the last two weeks, then expired. */
@Composable
private fun ExpiryLine(key: AiKey, now: Long) {
  val left = key.daysLeft(now)
  val warn = left != null && left <= AiKey.EXPIRY_WARNING_DAYS
  val text =
    when {
      left == null -> stringResource(Res.string.keys_expiry_none)
      left <= 0 -> stringResource(Res.string.keys_expired)
      else -> pluralStringResource(Res.plurals.keys_expires_in, left.toInt(), left.toInt())
    }
  val color =
    if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (warn) {
      Icon(
        Icons.Rounded.Schedule,
        contentDescription = null,
        tint = color,
        modifier = Modifier.padding(end = 4.dp).size(16.dp),
      )
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
  }
}

/** Days until a key expires; blank means it never does. */
@Composable
private fun DaysField(
  days: String,
  enabled: Boolean,
  onChange: (String) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
) {
  OutlinedTextField(
    value = days,
    onValueChange = onChange,
    label = { Text(stringResource(Res.string.keys_expiry_days)) },
    supportingText = { Text(stringResource(Res.string.keys_expiry_days_hint)) },
    singleLine = true,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

@Composable
private fun KeyNoticeText(notice: KeyNotice) {
  val good = notice is KeyNotice.Added
  val text =
    when (notice) {
      is KeyNotice.Added -> {
        val suits = FreeAiCatalog.of(notice.key.provider)?.suits.orEmpty()
        val names = suits.map { task -> stringResource(TASKS.first { it.first == task }.second) }
        stringResource(
          Res.string.keys_added,
          stringResource(providerName(notice.key.provider)),
          names.joinToString(", "),
        )
      }
      KeyNotice.Unknown -> stringResource(Res.string.keys_unknown)
      KeyNotice.Refused -> stringResource(Res.string.keys_refused)
      is KeyNotice.Failed -> stringResource(Res.string.keys_failed, notice.reason)
    }
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
  )
}

/** One free tier: its limits, its terms and where to get a key. */
@Composable
internal fun TierRow(tier: FreeTier) {
  val uriHandler = LocalUriHandler.current
  val limits = buildList {
    tier.requestsPerMinute?.let { add(stringResource(Res.string.keys_per_minute, it)) }
    tier.requestsPerDay?.let { add(stringResource(Res.string.keys_per_day, it)) }
    tier.tokensPerDay?.let { add(stringResource(Res.string.keys_tokens_per_day, shortCount(it))) }
    if (tier.requestsPerDay == null && tier.tokensPerDay == null) {
      add(stringResource(Res.string.keys_no_limit))
    }
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(stringResource(providerName(tier.provider)), style = MaterialTheme.typography.bodyLarge)
      Text(
        limits.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (tier.keepsPrompts) {
        Text(
          stringResource(Res.string.keys_keeps_prompts),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
    TextButton(
      onClick = { runCatching { uriHandler.openUri(tier.keyPage) } },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.AutoMirrored.Rounded.OpenInNew,
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
      )
      Text(stringResource(Res.string.keys_get))
    }
  }
}

@Composable
private fun Routing(pool: AiKeysState, onEvent: (OnlineKeysUiEvent) -> Unit) {
  Text(stringResource(Res.string.keys_routing_title), style = MaterialTheme.typography.titleSmall)
  Text(
    stringResource(Res.string.keys_routing_hint),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  val all = candidates(pool.keys, pool.routing)
  TASKS.forEach { (task, label) ->
    val chosen = pool.routing.forTask(task, pool.keys)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
      chosen.forEachIndexed { index, route ->
        RouteRow(task, route, index, chosen.size, pool, onEvent)
      }
      val others = all.filter { c -> chosen.none { it.keyId == c.keyId } }
      if (others.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          others.forEach { route ->
            FilterChip(
              selected = false,
              onClick = { onEvent(OnlineKeysUiEvent.Toggle(task, route)) },
              leadingIcon = {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
              },
              label = { Text(routeName(route, pool)) },
            )
          }
        }
      }
      if (task.usesResume && chosen.any { !it.local }) {
        Text(
          stringResource(Res.string.keys_resume_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
  TextButton(
    onClick = { onEvent(OnlineKeysUiEvent.ResetRouting) },
    shapes = ButtonDefaults.shapes(),
  ) {
    Text(stringResource(Res.string.keys_reset))
  }
}

/** One model in a task's order: its place, name and model, moved up or down or taken out. */
@Composable
private fun RouteRow(
  task: AiTask,
  route: AiRoute,
  index: Int,
  count: Int,
  pool: AiKeysState,
  onEvent: (OnlineKeysUiEvent) -> Unit,
) {
  val name = routeName(route, pool)
  val model =
    when {
      route.openCode -> route.model.ifBlank { pool.routing.openCode.model }
      route.agy -> route.model.ifBlank { pool.routing.agy.model }
      route.local -> null
      else -> route.model.ifBlank { pool.keys.firstOrNull { it.id == route.keyId }?.defaultModel }
    }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      "${index + 1}.",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(end = 8.dp),
    )
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.bodyMedium)
      if (!model.isNullOrBlank()) {
        Text(
          model,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Move(task, route, -1)) },
      enabled = index > 0,
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(Icons.Rounded.KeyboardArrowUp, stringResource(Res.string.keys_route_up, name))
    }
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Move(task, route, 1)) },
      enabled = index < count - 1,
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.keys_route_down, name))
    }
    // A task always keeps one model.
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Toggle(task, route)) },
      enabled = count > 1,
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(Icons.Rounded.Close, stringResource(Res.string.keys_route_remove, name))
    }
  }
}

@Composable
private fun routeName(route: AiRoute, pool: AiKeysState): String {
  val key = pool.keys.firstOrNull { it.id == route.keyId }
  return when {
    route.openCode -> stringResource(Res.string.opencode_chip)
    route.agy -> stringResource(Res.string.agy_chip)
    key == null -> stringResource(Res.string.keys_local)
    else -> key.label ?: stringResource(providerName(key.provider))
  }
}

/** 1500 → "1.5K", 1000000 → "1M". */
internal fun shortCount(value: Long): String =
  when {
    value >= 1_000_000 -> trimmed(value / 1_000_000.0) + "M"
    value >= 1_000 -> trimmed(value / 1_000.0) + "K"
    else -> value.toString()
  }

private fun trimmed(value: Double): String {
  val tenths = kotlin.math.round(value * 10).toLong()
  return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
