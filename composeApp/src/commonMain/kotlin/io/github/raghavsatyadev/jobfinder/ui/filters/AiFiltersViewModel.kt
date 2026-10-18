package io.github.raghavsatyadev.jobfinder.ui.filters

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a company was not added to the blacklist. */
sealed interface CompanyError {
  data object Blank : CompanyError

  /** [existing] is the name as the list already has it. */
  data class Duplicate(val existing: String) : CompanyError
}

/** The AI filters screen. [saved] is `null` until the first load finishes. */
@Immutable
data class AiFiltersUiState(
  val saved: AdvancedMatchingConfig? = null,
  val prompt: String = "",
  val companies: ImmutableList<String> = persistentListOf(),
  val companyInput: String = "",
  val companyError: CompanyError? = null,
  val loading: Boolean = true,
  val loadFailed: Boolean = false,
  val loadErrorMessage: String? = null,
  val saving: Boolean = false,
) {
  val promptTooLong: Boolean
    get() = prompt.length > AdvancedMatchingConfig.MAX_PROMPT_LENGTH

  /** The form differs from what is saved. */
  val dirty: Boolean
    get() = saved != null && (prompt != saved.prompt || companies != saved.blacklistedCompanies)

  val canSave: Boolean
    get() = dirty && !promptTooLong && !saving
}

sealed interface AiFiltersEvent {
  data class PromptChanged(val prompt: String) : AiFiltersEvent

  data class CompanyInputChanged(val text: String) : AiFiltersEvent

  data object AddCompany : AiFiltersEvent

  data class RemoveCompany(val name: String) : AiFiltersEvent

  data object Save : AiFiltersEvent

  /** Puts the form back to what is saved. */
  data object Discard : AiFiltersEvent

  data object Retry : AiFiltersEvent
}

sealed interface AiFiltersEffect {
  data object Saved : AiFiltersEffect

  /** [message] is the server's reason, or `null` for the generic message. */
  data class Failed(val message: String?) : AiFiltersEffect
}

/** The advanced-matching prompt and the company blacklist, saved together. */
class AiFiltersViewModel(private val matching: MatchingRepository) : ViewModel() {
  private val _state = MutableStateFlow(AiFiltersUiState())
  val state: StateFlow<AiFiltersUiState> = _state.asStateFlow()

  private val _effects = Channel<AiFiltersEffect>(Channel.BUFFERED)
  val effects: Flow<AiFiltersEffect> = _effects.receiveAsFlow()

  init {
    load()
  }

  fun onEvent(event: AiFiltersEvent) {
    when (event) {
      is AiFiltersEvent.PromptChanged -> _state.update { it.copy(prompt = event.prompt) }
      is AiFiltersEvent.CompanyInputChanged ->
        _state.update { it.copy(companyInput = event.text, companyError = null) }
      AiFiltersEvent.AddCompany -> addCompany()
      is AiFiltersEvent.RemoveCompany ->
        _state.update { s ->
          s.copy(companies = s.companies.filterNot { it == event.name }.toImmutableList())
        }
      AiFiltersEvent.Save -> save()
      AiFiltersEvent.Discard ->
        _state.update { s ->
          val saved = s.saved ?: return@update s
          s.withConfig(saved).copy(companyInput = "", companyError = null)
        }
      AiFiltersEvent.Retry -> load()
    }
  }

  private fun load() {
    _state.update { it.copy(loading = true, loadFailed = false) }
    viewModelScope.launch {
      try {
        val config = matching.getConfig()
        _state.update { it.withConfig(config).copy(loading = false) }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _state.update {
          it.copy(loading = false, loadFailed = true, loadErrorMessage = e.userMessage())
        }
      }
    }
  }

  private fun addCompany() {
    val s = _state.value
    val name = s.companyInput.trim().replace(Regex("\\s+"), " ")
    val error =
      when {
        name.isEmpty() -> CompanyError.Blank
        else ->
          s.companies
            .firstOrNull { it.equals(name, ignoreCase = true) }
            ?.let(CompanyError::Duplicate)
      }
    if (error != null) {
      _state.update { it.copy(companyError = error) }
      return
    }
    _state.update {
      it.copy(
        companies = (it.companies + name).toImmutableList(),
        companyInput = "",
        companyError = null,
      )
    }
  }

  private fun save() {
    val s = _state.value
    if (!s.canSave) return
    val config =
      AdvancedMatchingConfig(prompt = s.prompt.trim(), blacklistedCompanies = s.companies)
    _state.update { it.copy(saving = true) }
    viewModelScope.launch {
      try {
        val saved = matching.updateConfig(config)
        _state.update { current ->
          // Keep edits made while saving; only the baseline moves.
          val edited = current.prompt != s.prompt || current.companies != s.companies
          val next = if (edited) current.copy(saved = saved) else current.withConfig(saved)
          next.copy(saving = false)
        }
        _effects.send(AiFiltersEffect.Saved)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        _state.update { it.copy(saving = false) }
        _effects.send(AiFiltersEffect.Failed(e.userMessage()))
      }
    }
  }

  private fun AiFiltersUiState.withConfig(config: AdvancedMatchingConfig) =
    copy(
      saved = config,
      prompt = config.prompt,
      companies = config.blacklistedCompanies.toImmutableList(),
    )

  private fun Throwable.userMessage(): String? = (this as? ApiFailure)?.message
}
