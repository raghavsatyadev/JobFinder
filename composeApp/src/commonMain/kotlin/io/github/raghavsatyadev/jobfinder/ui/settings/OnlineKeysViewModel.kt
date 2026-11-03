@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
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
internal fun candidates(keys: List<AiKey>, routing: AiRouting): List<AiRoute> =
  keys.sortedBy { FreeAiCatalog.rank(it.provider) }.map { AiRoute(it.id, it.defaultModel) } +
    listOfNotNull(
      AiRoute.OPEN_CODE.takeIf { routing.openCode.enabled },
      AiRoute.AGY.takeIf { routing.agy.enabled },
    ) +
    AiRoute.LOCAL
