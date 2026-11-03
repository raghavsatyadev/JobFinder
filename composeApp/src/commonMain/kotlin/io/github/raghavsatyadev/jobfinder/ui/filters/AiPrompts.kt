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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import io.github.raghavsatyadev.jobfinder.resources.ic_expand_more_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_warning_amber_filled
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
import io.github.raghavsatyadev.jobfinder.resources.prompt_save
import io.github.raghavsatyadev.jobfinder.resources.prompt_save_failed
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
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptTrial
import io.github.raghavsatyadev.jobfinder.shared.model.PromptWarning
import io.github.raghavsatyadev.jobfinder.shared.model.tryable
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel

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
internal fun PromptItem(
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
        vectorResource(Res.drawable.ic_expand_more_filled),
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
internal fun PromptEditor(
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
internal fun TrialResult(trial: PromptTrial) {
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
internal fun ConfirmPromptDialog(confirm: PromptConfirm, onEvent: (AiPromptsEvent) -> Unit) {
  val error = MaterialTheme.colorScheme.error
  AlertDialog(
    onDismissRequest = { onEvent(AiPromptsEvent.CancelSave) },
    icon = {
      Icon(vectorResource(Res.drawable.ic_warning_amber_filled), contentDescription = null)
    },
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
internal fun warningText(warning: PromptWarning): String =
  when (warning) {
    PromptWarning.Empty -> stringResource(Res.string.prompt_warn_empty)
    is PromptWarning.MissingTerms ->
      stringResource(Res.string.prompt_warn_missing, warning.terms.joinToString(", "))
    PromptWarning.MentionsFormat -> stringResource(Res.string.prompt_warn_format)
    PromptWarning.DrivesParsing -> stringResource(Res.string.prompt_warn_parsing)
    PromptWarning.ResultsChange -> stringResource(Res.string.prompt_warn_results)
  }

internal val PromptId.title: StringResource
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

internal val PromptId.hint: StringResource
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
