@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.filters

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.prompt_reset_done
import io.github.raghavsatyadev.jobfinder.resources.prompt_save_failed
import io.github.raghavsatyadev.jobfinder.resources.prompt_saved
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptChecks
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTrial
import io.github.raghavsatyadev.jobfinder.shared.model.PromptWarning
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** A save waiting for the user: its warnings, and whether the second confirmation is showing. */
@Immutable
data class PromptConfirm(
  val id: PromptId,
  val warnings: List<PromptWarning>,
  val again: Boolean = false,
) {
  val strong: Boolean
    get() = warnings.any(PromptChecks::isStrong)
}

@Immutable
data class AiPromptsUiState(
  val prompts: List<AiPrompt>? = null,
  val loadFailed: Boolean = false,
  /** Text typed but not saved, per prompt. */
  val drafts: Map<PromptId, String> = emptyMap(),
  val open: PromptId? = null,
  val showingDefault: Set<PromptId> = emptySet(),
  val saving: PromptId? = null,
  val confirm: PromptConfirm? = null,
  /** The last save or reset, said under its prompt. */
  val message: Pair<PromptId, StringResource>? = null,
  /** The prompt "Try it" is running for. */
  val trying: PromptId? = null,
  /** The last try of each prompt. */
  val trials: Map<PromptId, PromptTrial> = emptyMap(),
  /** Why the last try failed, under its prompt. */
  val tryFailed: Pair<PromptId, String>? = null,
) {
  fun text(prompt: AiPrompt): String = drafts[prompt.id] ?: prompt.text

  fun changed(prompt: AiPrompt): Boolean = drafts[prompt.id]?.let { it != prompt.text } == true
}

sealed interface AiPromptsEvent {
  data class Toggle(val id: PromptId) : AiPromptsEvent

  data class Edit(val id: PromptId, val text: String) : AiPromptsEvent

  data class Discard(val id: PromptId) : AiPromptsEvent

  data class Save(val id: PromptId) : AiPromptsEvent

  data object ConfirmSave : AiPromptsEvent

  data object CancelSave : AiPromptsEvent

  data class Reset(val id: PromptId) : AiPromptsEvent

  data class ToggleDefault(val id: PromptId) : AiPromptsEvent

  data object Retry : AiPromptsEvent

  /** Runs the text as typed on one job, without saving it. */
  data class TryIt(val id: PromptId) : AiPromptsEvent
}

/** The AI instructions on the AI filters tab: edit, save with warnings, reset. */
class AiPromptsViewModel(private val repository: AiPromptsRepository) : ViewModel() {
  private val _state = MutableStateFlow(AiPromptsUiState())
  val state: StateFlow<AiPromptsUiState> = _state.asStateFlow()

  init {
    load()
  }

  fun onEvent(event: AiPromptsEvent) {
    when (event) {
      is AiPromptsEvent.Toggle ->
        _state.update { it.copy(open = if (it.open == event.id) null else event.id) }
      is AiPromptsEvent.Edit ->
        _state.update { it.copy(drafts = it.drafts + (event.id to event.text), message = null) }
      is AiPromptsEvent.Discard -> _state.update { it.copy(drafts = it.drafts - event.id) }
      is AiPromptsEvent.Save -> askToSave(event.id)
      AiPromptsEvent.ConfirmSave -> confirm()
      AiPromptsEvent.CancelSave -> _state.update { it.copy(confirm = null) }
      is AiPromptsEvent.Reset -> write(event.id, null)
      is AiPromptsEvent.ToggleDefault ->
        _state.update {
          val shown = it.showingDefault
          it.copy(showingDefault = if (event.id in shown) shown - event.id else shown + event.id)
        }
      AiPromptsEvent.Retry -> load()
      is AiPromptsEvent.TryIt -> tryIt(event.id)
    }
  }

  private fun tryIt(id: PromptId) {
    val state = _state.value
    if (state.trying != null) return
    val prompt = state.prompts?.firstOrNull { it.id == id } ?: return
    val text = state.text(prompt)
    _state.update { it.copy(trying = id, tryFailed = null) }
    viewModelScope.launch {
      try {
        val trial = repository.tryPrompt(id, text)
        _state.update { it.copy(trying = null, trials = it.trials + (id to trial)) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _state.update {
          it.copy(trying = null, trials = it.trials - id, tryFailed = id to e.message.orEmpty())
        }
      }
    }
  }

  private fun load() {
    _state.update { it.copy(loadFailed = false) }
    viewModelScope.launch {
      try {
        val prompts = repository.listPrompts()
        _state.update { it.copy(prompts = prompts) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loadFailed = true) }
      }
    }
  }

  /** Every save asks first: what may break, and for strong warnings a second time. */
  private fun askToSave(id: PromptId) {
    val state = _state.value
    val prompt = state.prompts?.firstOrNull { it.id == id } ?: return
    val text = state.drafts[id] ?: return
    _state.update {
      it.copy(confirm = PromptConfirm(id, PromptChecks.check(id, text, prompt.defaultText)))
    }
  }

  private fun confirm() {
    val confirm = _state.value.confirm ?: return
    if (confirm.strong && !confirm.again) {
      _state.update { it.copy(confirm = confirm.copy(again = true)) }
      return
    }
    _state.update { it.copy(confirm = null) }
    write(confirm.id, _state.value.drafts[confirm.id] ?: return)
  }

  /** Saves [text], or resets with `null`. */
  private fun write(id: PromptId, text: String?) {
    if (_state.value.saving != null) return
    _state.update { it.copy(saving = id, message = null) }
    viewModelScope.launch {
      try {
        val saved =
          if (text == null) repository.resetPrompt(id) else repository.savePrompt(id, text)
        _state.update { s ->
          s.copy(
            prompts = s.prompts?.map { if (it.id == id) saved else it },
            drafts = s.drafts - id,
            saving = null,
            message =
              id to (if (text == null) Res.string.prompt_reset_done else Res.string.prompt_saved),
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(saving = null, message = id to Res.string.prompt_save_failed) }
      }
    }
  }
}
