@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.filters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import io.github.raghavsatyadev.jobfinder.resources.offline_later
import io.github.raghavsatyadev.jobfinder.resources.offline_saved_later
import io.github.raghavsatyadev.jobfinder.resources.prompt_ats_review_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_ats_review_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_default_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_discard
import io.github.raghavsatyadev.jobfinder.resources.prompt_edited
import io.github.raghavsatyadev.jobfinder.resources.prompt_field
import io.github.raghavsatyadev.jobfinder.resources.prompt_ghost_match_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_ghost_match_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_hide_default
import io.github.raghavsatyadev.jobfinder.resources.prompt_improve_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_improve_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_matching_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_matching_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_parse_description_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_parse_description_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_parse_list_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_parse_list_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_reset
import io.github.raghavsatyadev.jobfinder.resources.prompt_reset_done
import io.github.raghavsatyadev.jobfinder.resources.prompt_save
import io.github.raghavsatyadev.jobfinder.resources.prompt_save_failed
import io.github.raghavsatyadev.jobfinder.resources.prompt_saved
import io.github.raghavsatyadev.jobfinder.resources.prompt_show_default
import io.github.raghavsatyadev.jobfinder.resources.prompt_tips_hint
import io.github.raghavsatyadev.jobfinder.resources.prompt_tips_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_try
import io.github.raghavsatyadev.jobfinder.resources.prompt_try_ats
import io.github.raghavsatyadev.jobfinder.resources.prompt_try_excluded
import io.github.raghavsatyadev.jobfinder.resources.prompt_try_failed
import io.github.raghavsatyadev.jobfinder.resources.prompt_try_job
import io.github.raghavsatyadev.jobfinder.resources.prompt_try_kept
import io.github.raghavsatyadev.jobfinder.resources.prompt_try_not_saved
import io.github.raghavsatyadev.jobfinder.resources.prompt_trying
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_again_text
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_again_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_continue
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_empty
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_format
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_missing
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_parsing
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_results
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_save
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_strong_title
import io.github.raghavsatyadev.jobfinder.resources.prompt_warn_title
import io.github.raghavsatyadev.jobfinder.resources.prompts_intro
import io.github.raghavsatyadev.jobfinder.resources.prompts_load_failed
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.PromptChecks
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTrial
import io.github.raghavsatyadev.jobfinder.shared.model.PromptWarning
import io.github.raghavsatyadev.jobfinder.shared.model.tryable
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

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

/** The AI instructions part of the AI filters tab, from its ViewModel. */
@Composable
fun AiPromptsSection() {
  val viewModel = koinViewModel<AiPromptsViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  AiPromptsContent(state, viewModel::onEvent)
}

@Composable
fun AiPromptsContent(state: AiPromptsUiState, onEvent: (AiPromptsEvent) -> Unit) {
  val offline = LocalComputerOffline.current
  Column(Modifier.padding(vertical = 8.dp)) {
    if (offline) OfflineNote(text = stringResource(Res.string.offline_saved_later))
    Text(
      stringResource(Res.string.prompts_intro),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    val prompts = state.prompts
    when {
      prompts == null && state.loadFailed ->
        Row(
          Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            stringResource(
              if (offline) Res.string.offline_later else Res.string.prompts_load_failed
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
          )
          if (!offline) {
            TextButton(
              onClick = { onEvent(AiPromptsEvent.Retry) },
              shapes = ButtonDefaults.shapes(),
            ) {
              Text(stringResource(Res.string.common_retry))
            }
          }
        }
      prompts == null -> WavyProgress(modifier = Modifier.padding(16.dp))
      else ->
        prompts.forEachIndexed { index, prompt ->
          if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
          PromptItem(prompt, state, onEvent)
        }
    }
  }
  state.confirm?.let { ConfirmPromptDialog(it, onEvent) }
}

@Composable
private fun PromptItem(
  prompt: AiPrompt,
  state: AiPromptsUiState,
  onEvent: (AiPromptsEvent) -> Unit,
) {
  val open = state.open == prompt.id
  val motion = MaterialTheme.motionScheme
  Column {
    Row(
      Modifier.fillMaxWidth()
        .clickable(role = Role.Button) { onEvent(AiPromptsEvent.Toggle(prompt.id)) }
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(prompt.id.title), style = MaterialTheme.typography.titleSmall)
        Text(
          stringResource(prompt.id.hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (prompt.edited) StatusPill(stringResource(Res.string.prompt_edited), StatusTone.Neutral)
      Icon(
        Icons.Rounded.ExpandMore,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f },
      )
    }
    AnimatedVisibility(
      visible = open,
      enter =
        if (animationsEnabled())
          expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec())
        else fadeIn(motion.fastEffectsSpec()),
      exit = fadeOut(motion.fastEffectsSpec()),
    ) {
      PromptEditor(prompt, state, onEvent)
    }
  }
}

@Composable
private fun PromptEditor(
  prompt: AiPrompt,
  state: AiPromptsUiState,
  onEvent: (AiPromptsEvent) -> Unit,
) {
  val saving = state.saving == prompt.id
  Column(
    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedTextField(
      value = state.text(prompt),
      onValueChange = { onEvent(AiPromptsEvent.Edit(prompt.id, it.take(AiPrompt.MAX_LENGTH))) },
      label = { Text(stringResource(Res.string.prompt_field)) },
      enabled = !saving,
      minLines = 6,
      maxLines = 16,
      textStyle = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.fillMaxWidth(),
    )
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (state.changed(prompt)) {
        FilledTonalButton(
          onClick = { onEvent(AiPromptsEvent.Save(prompt.id)) },
          enabled = !saving,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.prompt_save))
        }
        TextButton(
          onClick = { onEvent(AiPromptsEvent.Discard(prompt.id)) },
          enabled = !saving,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.prompt_discard))
        }
      }
      if (prompt.edited) {
        TextButton(
          onClick = { onEvent(AiPromptsEvent.Reset(prompt.id)) },
          enabled = !saving,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.prompt_reset))
        }
      }
      if (prompt.id.tryable) {
        TextButton(
          onClick = { onEvent(AiPromptsEvent.TryIt(prompt.id)) },
          enabled = !saving && state.trying == null && !LocalComputerOffline.current,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.prompt_try))
        }
      }
      if (prompt.edited || state.changed(prompt)) {
        TextButton(
          onClick = { onEvent(AiPromptsEvent.ToggleDefault(prompt.id)) },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(
            stringResource(
              if (prompt.id in state.showingDefault) Res.string.prompt_hide_default
              else Res.string.prompt_show_default
            )
          )
        }
      }
    }
    if (saving) WavyProgress()
    if (state.trying == prompt.id) {
      Text(
        stringResource(Res.string.prompt_trying),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      WavyProgress()
    }
    state.tryFailed
      ?.takeIf { it.first == prompt.id }
      ?.let { (_, reason) ->
        Text(
          stringResource(Res.string.prompt_try_failed, reason),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.error,
        )
      }
    state.trials[prompt.id]?.let { TrialResult(it) }
    state.message
      ?.takeIf { it.first == prompt.id }
      ?.let { (_, message) ->
        Text(
          stringResource(message),
          style = MaterialTheme.typography.bodyMedium,
          color =
            if (message == Res.string.prompt_save_failed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    if (prompt.id in state.showingDefault) {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
      ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            stringResource(Res.string.prompt_default_title),
            style = MaterialTheme.typography.labelLarge,
          )
          SelectionContainer {
            Text(prompt.defaultText, style = MaterialTheme.typography.bodySmall)
          }
        }
      }
    }
  }
}

/** What a try answered, for the job it ran on. */
@Composable
private fun TrialResult(trial: PromptTrial) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        stringResource(Res.string.prompt_try_job, trial.title, trial.company),
        style = MaterialTheme.typography.labelLarge,
      )
      trial.excluded?.let { excluded ->
        Text(
          if (excluded) {
            stringResource(Res.string.prompt_try_excluded, trial.reason.orEmpty())
          } else {
            stringResource(Res.string.prompt_try_kept)
          },
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      trial.atsMatch?.let { match ->
        Text(
          stringResource(Res.string.prompt_try_ats, match),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      trial.assessment
        ?.takeIf { it.isNotBlank() }
        ?.let {
          Text(it, style = MaterialTheme.typography.bodySmall)
        }
      trial.fixes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
      Text(
        stringResource(Res.string.prompt_try_not_saved),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** First what may break; for a strong warning, a second "Are you sure?". */
@Composable
private fun ConfirmPromptDialog(confirm: PromptConfirm, onEvent: (AiPromptsEvent) -> Unit) {
  val error = MaterialTheme.colorScheme.error
  AlertDialog(
    onDismissRequest = { onEvent(AiPromptsEvent.CancelSave) },
    icon = { Icon(Icons.Rounded.WarningAmber, contentDescription = null) },
    title = {
      Text(
        stringResource(
          when {
            confirm.again -> Res.string.prompt_warn_again_title
            confirm.strong -> Res.string.prompt_warn_strong_title
            else -> Res.string.prompt_warn_title
          }
        )
      )
    },
    text = {
      if (confirm.again) {
        Text(stringResource(Res.string.prompt_warn_again_text))
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          confirm.warnings.forEach { Text("• " + warningText(it)) }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onEvent(AiPromptsEvent.ConfirmSave) },
        shapes = ButtonDefaults.shapes(),
        colors =
          if (confirm.strong) ButtonDefaults.textButtonColors(contentColor = error)
          else ButtonDefaults.textButtonColors(),
      ) {
        Text(
          stringResource(
            if (confirm.strong && !confirm.again) Res.string.prompt_warn_continue
            else Res.string.prompt_warn_save
          )
        )
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onEvent(AiPromptsEvent.CancelSave) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}

@Composable
private fun warningText(warning: PromptWarning): String =
  when (warning) {
    PromptWarning.Empty -> stringResource(Res.string.prompt_warn_empty)
    is PromptWarning.MissingTerms ->
      stringResource(Res.string.prompt_warn_missing, warning.terms.joinToString(", "))
    PromptWarning.MentionsFormat -> stringResource(Res.string.prompt_warn_format)
    PromptWarning.DrivesParsing -> stringResource(Res.string.prompt_warn_parsing)
    PromptWarning.ResultsChange -> stringResource(Res.string.prompt_warn_results)
  }

private val PromptId.title: StringResource
  get() =
    when (this) {
      PromptId.MATCHING -> Res.string.prompt_matching_title
      PromptId.PARSE_LIST -> Res.string.prompt_parse_list_title
      PromptId.PARSE_DESCRIPTION -> Res.string.prompt_parse_description_title
      PromptId.ATS_REVIEW -> Res.string.prompt_ats_review_title
      PromptId.GHOST_MATCH -> Res.string.prompt_ghost_match_title
      PromptId.NEGOTIATION_TIPS -> Res.string.prompt_tips_title
      PromptId.RESUME_IMPROVE -> Res.string.prompt_improve_title
    }

private val PromptId.hint: StringResource
  get() =
    when (this) {
      PromptId.MATCHING -> Res.string.prompt_matching_hint
      PromptId.PARSE_LIST -> Res.string.prompt_parse_list_hint
      PromptId.PARSE_DESCRIPTION -> Res.string.prompt_parse_description_hint
      PromptId.ATS_REVIEW -> Res.string.prompt_ats_review_hint
      PromptId.GHOST_MATCH -> Res.string.prompt_ghost_match_hint
      PromptId.NEGOTIATION_TIPS -> Res.string.prompt_tips_hint
      PromptId.RESUME_IMPROVE -> Res.string.prompt_improve_hint
    }
