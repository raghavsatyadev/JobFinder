@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ai_address
import io.github.raghavsatyadev.jobfinder.resources.ai_address_hint
import io.github.raghavsatyadev.jobfinder.resources.ai_failed
import io.github.raghavsatyadev.jobfinder.resources.ai_hint
import io.github.raghavsatyadev.jobfinder.resources.ai_key
import io.github.raghavsatyadev.jobfinder.resources.ai_key_missing
import io.github.raghavsatyadev.jobfinder.resources.ai_key_optional
import io.github.raghavsatyadev.jobfinder.resources.ai_key_remove
import io.github.raghavsatyadev.jobfinder.resources.ai_key_saved
import io.github.raghavsatyadev.jobfinder.resources.ai_keys_unavailable
import io.github.raghavsatyadev.jobfinder.resources.ai_model
import io.github.raghavsatyadev.jobfinder.resources.ai_model_blank
import io.github.raghavsatyadev.jobfinder.resources.ai_online_warning
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_anthropic
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_custom
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_gemini
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_groq
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_nvidia
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_ollama
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_ollama_hint
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_openai
import io.github.raghavsatyadev.jobfinder.resources.ai_provider_openrouter
import io.github.raghavsatyadev.jobfinder.resources.ai_save
import io.github.raghavsatyadev.jobfinder.resources.ai_save_failed
import io.github.raghavsatyadev.jobfinder.resources.ai_saved
import io.github.raghavsatyadev.jobfinder.resources.ai_test
import io.github.raghavsatyadev.jobfinder.resources.ai_test_ok
import io.github.raghavsatyadev.jobfinder.resources.ai_testing
import io.github.raghavsatyadev.jobfinder.shared.model.AiProvider
import io.github.raghavsatyadev.jobfinder.shared.model.AiSettings
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoice
import io.github.raghavsatyadev.jobfinder.ui.platform.AiChoiceState
import io.github.raghavsatyadev.jobfinder.ui.platform.AiTestResult
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel

/** What the AI section last did, shown under its buttons. */
sealed interface AiNotice {
  data object Saved : AiNotice

  data object Works : AiNotice

  data object KeyMissing : AiNotice

  data object ModelBlank : AiNotice

  data object SaveFailed : AiNotice

  data class Failed(val reason: String) : AiNotice
}

@Immutable
data class AiUiState(
  val loading: Boolean = true,
  val saved: AiSettings = AiSettings(),
  val provider: AiProvider = AiProvider.OLLAMA,
  val model: String = AiProvider.OLLAMA.suggestedModel,
  val baseUrl: String = "",
  /** A key typed now; never the saved one, which the UI can't read. */
  val key: String = "",
  val keysSaved: Set<AiProvider> = emptySet(),
  val keysAvailable: Boolean = true,
  val testing: Boolean = false,
  val saving: Boolean = false,
  val notice: AiNotice? = null,
) {
  val keySaved: Boolean
    get() = provider in keysSaved

  val changed: Boolean
    get() = draft() != saved || key.isNotBlank()

  fun draft() =
    AiSettings(
      provider,
      model.trim(),
      baseUrl.trim().takeIf { provider == AiProvider.CUSTOM && it.isNotEmpty() },
    )
}

sealed interface AiUiEvent {
  data class SetProvider(val provider: AiProvider) : AiUiEvent

  data class SetModel(val model: String) : AiUiEvent

  data class SetBaseUrl(val url: String) : AiUiEvent

  data class SetKey(val key: String) : AiUiEvent

  data object RemoveKey : AiUiEvent

  data object Test : AiUiEvent

  data object Save : AiUiEvent
}

/** Settings → AI model: local Ollama or an online provider with the user's own key. */
class AiModelViewModel(private val choice: AiChoice) : ViewModel() {
  private val _state = MutableStateFlow(AiUiState())
  val state: StateFlow<AiUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        show(choice.load())
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loading = false) }
      }
    }
  }

  fun onEvent(event: AiUiEvent) {
    when (event) {
      is AiUiEvent.SetProvider -> pick(event.provider)
      is AiUiEvent.SetModel -> _state.update { it.copy(model = event.model, notice = null) }
      is AiUiEvent.SetBaseUrl -> _state.update { it.copy(baseUrl = event.url, notice = null) }
      is AiUiEvent.SetKey -> _state.update { it.copy(key = event.key, notice = null) }
      AiUiEvent.RemoveKey -> save(key = "")
      AiUiEvent.Test -> test()
      AiUiEvent.Save -> save(key = _state.value.key.takeIf { it.isNotBlank() })
    }
  }

  private fun show(loaded: AiChoiceState) {
    val saved = loaded.settings
    _state.update {
      it.copy(
        loading = false,
        saved = saved,
        provider = saved.provider,
        model = saved.model,
        baseUrl = saved.baseUrl.orEmpty(),
        key = "",
        keysSaved = loaded.keysSaved,
        keysAvailable = loaded.keysAvailable,
      )
    }
  }

  /** Another provider starts from its saved model, or the one it suggests. */
  private fun pick(provider: AiProvider) {
    _state.update {
      val saved = it.saved
      it.copy(
        provider = provider,
        model = if (saved.provider == provider) saved.model else provider.suggestedModel,
        baseUrl = if (saved.provider == provider) saved.baseUrl.orEmpty() else "",
        key = "",
        notice = null,
      )
    }
  }

  /** Why [state] can't be used yet, or `null`. */
  private fun problem(state: AiUiState): AiNotice? =
    when {
      state.model.isBlank() -> AiNotice.ModelBlank
      state.provider.needsKey && !state.keySaved && state.key.isBlank() -> AiNotice.KeyMissing
      else -> null
    }

  private fun test() {
    val now = _state.value
    if (now.testing) return
    problem(now)?.let { notice ->
      return _state.update { it.copy(notice = notice) }
    }
    _state.update { it.copy(testing = true, notice = null) }
    viewModelScope.launch {
      val notice =
        try {
          when (val result = choice.test(now.draft(), now.key.takeIf { it.isNotBlank() })) {
            AiTestResult.Ok -> AiNotice.Works
            AiTestResult.KeyMissing -> AiNotice.KeyMissing
            is AiTestResult.Failed -> AiNotice.Failed(result.reason)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          AiNotice.Failed(e.message.orEmpty())
        }
      _state.update { it.copy(testing = false, notice = notice) }
    }
  }

  private fun save(key: String?) {
    val now = _state.value
    if (now.saving) return
    if (key == null) {
      problem(now)?.let { notice ->
        return _state.update { it.copy(notice = notice) }
      }
    }
    _state.update { it.copy(saving = true, notice = null) }
    viewModelScope.launch {
      try {
        show(choice.save(now.draft(), key))
        _state.update { it.copy(saving = false, notice = AiNotice.Saved) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(saving = false, notice = AiNotice.SaveFailed) }
      }
    }
  }
}

/** The AI model section of Settings, from its ViewModel. */
@Composable
fun AiModelSettings() {
  val viewModel = koinViewModel<AiModelViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val hasLocalModels = getKoin().getOrNull<LocalModels>() != null
  AiModelContent(
    state,
    viewModel::onEvent,
    local =
      if (hasLocalModels) {
        {
          val models = koinViewModel<LocalModelsViewModel>()
          val modelsState by models.state.collectAsStateWithLifecycle()
          LocalModelsSection(modelsState, state.model, models::onEvent) {
            viewModel.onEvent(AiUiEvent.SetModel(it))
          }
        }
      } else null,
  )
}

private val PROVIDERS: List<Pair<AiProvider, StringResource>> =
  listOf(
    AiProvider.OLLAMA to Res.string.ai_provider_ollama,
    AiProvider.OPENAI to Res.string.ai_provider_openai,
    AiProvider.ANTHROPIC to Res.string.ai_provider_anthropic,
    AiProvider.GEMINI to Res.string.ai_provider_gemini,
    AiProvider.OPENROUTER to Res.string.ai_provider_openrouter,
    AiProvider.NVIDIA to Res.string.ai_provider_nvidia,
    AiProvider.GROQ to Res.string.ai_provider_groq,
    AiProvider.CUSTOM to Res.string.ai_provider_custom,
  )

/** The provider's name as shown in Settings. */
internal fun providerName(provider: AiProvider): StringResource =
  PROVIDERS.first { it.first == provider }.second

@Composable
fun AiModelContent(
  state: AiUiState,
  onEvent: (AiUiEvent) -> Unit,
  /** The models on this computer, shown with the local provider; `null` leaves it out. */
  local: (@Composable () -> Unit)? = null,
) {
  val busy = state.testing || state.saving || state.loading
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      stringResource(Res.string.ai_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.selectableGroup()) {
      PROVIDERS.forEach { (provider, label) ->
        val enabled = !provider.online || state.keysAvailable
        Row(
          Modifier.fillMaxWidth()
            .selectable(
              selected = state.provider == provider,
              enabled = enabled && !busy,
              role = Role.RadioButton,
              onClick = { onEvent(AiUiEvent.SetProvider(provider)) },
            )
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(selected = state.provider == provider, onClick = null, enabled = enabled)
          Column(Modifier.padding(start = 12.dp)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            if (provider == AiProvider.OLLAMA) {
              Text(
                stringResource(Res.string.ai_provider_ollama_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
    if (!state.keysAvailable) {
      Text(
        stringResource(Res.string.ai_keys_unavailable),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (state.provider.online) {
      Text(
        stringResource(Res.string.ai_online_warning),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
      )
    }
    OutlinedTextField(
      value = state.model,
      onValueChange = { onEvent(AiUiEvent.SetModel(it)) },
      label = { Text(stringResource(Res.string.ai_model)) },
      singleLine = true,
      enabled = !busy,
      modifier = Modifier.fillMaxWidth(),
    )
    if (state.provider == AiProvider.CUSTOM) {
      OutlinedTextField(
        value = state.baseUrl,
        onValueChange = { onEvent(AiUiEvent.SetBaseUrl(it)) },
        label = { Text(stringResource(Res.string.ai_address)) },
        supportingText = { Text(stringResource(Res.string.ai_address_hint)) },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (state.provider.online) {
      OutlinedTextField(
        value = state.key,
        onValueChange = { onEvent(AiUiEvent.SetKey(it)) },
        label = {
          Text(
            stringResource(
              if (state.provider.needsKey) Res.string.ai_key else Res.string.ai_key_optional
            )
          )
        },
        supportingText =
          if (state.keySaved) {
            { Text(stringResource(Res.string.ai_key_saved)) }
          } else null,
        singleLine = true,
        enabled = !busy,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (state.testing || state.saving) {
      if (state.testing) {
        Text(stringResource(Res.string.ai_testing), style = MaterialTheme.typography.bodyMedium)
      }
      WavyProgress()
    }
    state.notice?.let { Notice(it, state.draft().model) }
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Button(
        onClick = { onEvent(AiUiEvent.Save) },
        enabled = !busy && state.changed,
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.ai_save))
      }
      OutlinedButton(
        onClick = { onEvent(AiUiEvent.Test) },
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.ai_test))
      }
      if (state.provider.online && state.keySaved) {
        TextButton(
          onClick = { onEvent(AiUiEvent.RemoveKey) },
          enabled = !busy,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.ai_key_remove))
        }
      }
    }
    if (state.provider == AiProvider.OLLAMA) local?.invoke()
  }
}

@Composable
private fun Notice(notice: AiNotice, model: String) {
  val good = notice == AiNotice.Saved || notice == AiNotice.Works
  val text =
    when (notice) {
      AiNotice.Saved -> stringResource(Res.string.ai_saved, model)
      AiNotice.Works -> stringResource(Res.string.ai_test_ok)
      AiNotice.KeyMissing -> stringResource(Res.string.ai_key_missing)
      AiNotice.ModelBlank -> stringResource(Res.string.ai_model_blank)
      AiNotice.SaveFailed -> stringResource(Res.string.ai_save_failed)
      is AiNotice.Failed -> stringResource(Res.string.ai_failed, notice.reason)
    }
  val color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      if (good) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
      contentDescription = null,
      tint = color,
      modifier = Modifier.size(20.dp),
    )
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
  }
}
