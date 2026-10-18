@file:OptIn(
  ExperimentalMaterial3ExpressiveApi::class,
  ExperimentalMaterial3Api::class,
  ExperimentalLayoutApi::class,
)

package io.github.raghavsatyadev.jobfinder.ui.filters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_error_generic
import io.github.raghavsatyadev.jobfinder.resources.filters_companies_empty
import io.github.raghavsatyadev.jobfinder.resources.filters_companies_intro
import io.github.raghavsatyadev.jobfinder.resources.filters_companies_title
import io.github.raghavsatyadev.jobfinder.resources.filters_company_add
import io.github.raghavsatyadev.jobfinder.resources.filters_company_error_blank
import io.github.raghavsatyadev.jobfinder.resources.filters_company_error_duplicate
import io.github.raghavsatyadev.jobfinder.resources.filters_company_label
import io.github.raghavsatyadev.jobfinder.resources.filters_company_remove
import io.github.raghavsatyadev.jobfinder.resources.filters_discard
import io.github.raghavsatyadev.jobfinder.resources.filters_intro
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_counter
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_hint
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_label
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_row_hint
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_tips
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_title
import io.github.raghavsatyadev.jobfinder.resources.filters_prompt_too_long
import io.github.raghavsatyadev.jobfinder.resources.filters_save
import io.github.raghavsatyadev.jobfinder.resources.filters_saved
import io.github.raghavsatyadev.jobfinder.resources.filters_title
import io.github.raghavsatyadev.jobfinder.resources.filters_unsaved
import io.github.raghavsatyadev.jobfinder.resources.offline_saved_later
import io.github.raghavsatyadev.jobfinder.resources.settings_section_prompts
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.ui.components.ErrorState
import io.github.raghavsatyadev.jobfinder.ui.components.LoadingState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import io.github.raghavsatyadev.jobfinder.ui.components.paddingAbove
import io.github.raghavsatyadev.jobfinder.ui.components.screenInsets
import io.github.raghavsatyadev.jobfinder.ui.components.scrollBottom
import io.github.raghavsatyadev.jobfinder.ui.theme.animationsEnabled
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Koin bindings of the AI filters feature. */
val aiFiltersUiModule = module {
  viewModelOf(::AiFiltersViewModel)
  viewModelOf(::AiPromptsViewModel)
}

/** The advanced-matching prompt, the company blacklist and the AI instructions. */
@Composable
fun AiFiltersScreen() {
  val viewModel = koinViewModel<AiFiltersViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      val message =
        when (effect) {
          AiFiltersEffect.Saved -> getString(Res.string.filters_saved)
          is AiFiltersEffect.Failed -> effect.message ?: getString(Res.string.common_error_generic)
        }
      snackbar.showSnackbar(message)
    }
  }
  AiFiltersContent(
    state,
    viewModel::onEvent,
    snackbar,
    prompts =
      if (getKoin().getOrNull<AiPromptsRepository>() != null) ({ AiPromptsSection() }) else null,
  )
}

/** The AI filters screen for [state]; stateless, so previews and screenshots can show any state. */
@Composable
fun AiFiltersContent(
  state: AiFiltersUiState,
  onEvent: (AiFiltersEvent) -> Unit,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
  /** The AI instructions; `null` leaves them out. */
  prompts: (@Composable () -> Unit)? = null,
) {
  Scaffold(
    topBar = { TopAppBar(title = { Text(stringResource(Res.string.filters_title)) }) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    contentWindowInsets = screenInsets(),
    bottomBar = {
      // Only once there is something to save; offline the save waits on the phone.
      val motion = MaterialTheme.motionScheme
      AnimatedVisibility(
        visible = state.dirty,
        enter =
          expandVertically(motion.defaultSpatialSpec(), expandFrom = Alignment.Bottom) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        // Floats above the tab bar like it, so the form still shows on both sides.
        modifier = Modifier.padding(bottom = scrollBottom()),
      ) {
        SaveBar(state, onEvent)
      }
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().paddingAbove(padding)) {
      when {
        state.saved == null && state.loadFailed ->
          ErrorState(
            message = state.loadErrorMessage ?: stringResource(Res.string.common_error_generic),
            onRetry = { onEvent(AiFiltersEvent.Retry) },
          )
        state.saved == null -> LoadingState()
        else -> Form(state, onEvent, prompts)
      }
    }
  }
}

@Composable
private fun Form(
  state: AiFiltersUiState,
  onEvent: (AiFiltersEvent) -> Unit,
  prompts: (@Composable () -> Unit)?,
) {
  Box(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      Modifier.widthIn(max = 840.dp)
        .fillMaxWidth()
        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + scrollBottom())
        // The save bar floats over the end of the form while it shows.
        .padding(bottom = if (state.dirty) SaveBarSpace else 0.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      if (LocalComputerOffline.current) {
        OfflineNote(text = stringResource(Res.string.offline_saved_later))
      }
      Text(
        stringResource(Res.string.filters_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      // What to filter out is one of the AI's instructions: it heads them, open.
      // The instructions pad their own rows, so the card adds only the title's padding.
      Card(
        shape = MaterialTheme.shapes.large,
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          stringResource(Res.string.settings_section_prompts),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          modifier =
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).semantics { heading() },
        )
        FilterPromptItem(state, onEvent)
        if (prompts != null) {
          HorizontalDivider(Modifier.padding(horizontal = 16.dp))
          prompts()
        }
      }
      Section(stringResource(Res.string.filters_companies_title)) {
        Text(
          stringResource(Res.string.filters_companies_intro),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CompanyInput(state, onEvent)
        CompanyChips(state.companies, enabled = editable(state), onEvent = onEvent)
      }
    }
  }
}

/** "What to filter out" as a row of the AI instructions, open at first. */
@Composable
private fun FilterPromptItem(state: AiFiltersUiState, onEvent: (AiFiltersEvent) -> Unit) {
  var open by rememberSaveable { mutableStateOf(true) }
  val motion = MaterialTheme.motionScheme
  Column {
    Row(
      Modifier.fillMaxWidth()
        .clickable(role = Role.Button) { open = !open }
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(Res.string.filters_prompt_title),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          stringResource(Res.string.filters_prompt_row_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
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
      Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        PromptField(state, onEvent)
      }
    }
  }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
      )
      content()
    }
  }
}

@Composable
private fun PromptField(state: AiFiltersUiState, onEvent: (AiFiltersEvent) -> Unit) {
  val max = AdvancedMatchingConfig.MAX_PROMPT_LENGTH
  val counter = stringResource(Res.string.filters_prompt_counter, state.prompt.length, max)
  OutlinedTextField(
    value = state.prompt,
    onValueChange = { onEvent(AiFiltersEvent.PromptChanged(it)) },
    label = { Text(stringResource(Res.string.filters_prompt_label)) },
    placeholder = { Text(stringResource(Res.string.filters_prompt_hint)) },
    isError = state.promptTooLong,
    enabled = editable(state),
    minLines = 5,
    maxLines = 14,
    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    supportingText = {
      Row(verticalAlignment = Alignment.Top) {
        Text(
          if (state.promptTooLong) stringResource(Res.string.filters_prompt_too_long, max)
          else stringResource(Res.string.filters_prompt_tips),
          modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(counter)
      }
    },
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun CompanyInput(state: AiFiltersUiState, onEvent: (AiFiltersEvent) -> Unit) {
  val add = { onEvent(AiFiltersEvent.AddCompany) }
  Row(verticalAlignment = Alignment.Top) {
    OutlinedTextField(
      value = state.companyInput,
      onValueChange = { onEvent(AiFiltersEvent.CompanyInputChanged(it)) },
      label = { Text(stringResource(Res.string.filters_company_label)) },
      isError = state.companyError != null,
      supportingText =
        state.companyError?.let { error ->
          {
            Text(
              when (error) {
                CompanyError.Blank -> stringResource(Res.string.filters_company_error_blank)
                is CompanyError.Duplicate ->
                  stringResource(Res.string.filters_company_error_duplicate, error.existing)
              }
            )
          }
        },
      singleLine = true,
      enabled = editable(state),
      keyboardOptions =
        KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { add() }),
      modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(8.dp))
    FilledTonalIconButton(
      onClick = add,
      enabled = editable(state),
      modifier = Modifier.padding(top = 8.dp).size(48.dp),
    ) {
      Icon(Icons.Rounded.Add, contentDescription = stringResource(Res.string.filters_company_add))
    }
  }
}

@Composable
private fun CompanyChips(
  companies: ImmutableList<String>,
  enabled: Boolean,
  onEvent: (AiFiltersEvent) -> Unit,
) {
  if (companies.isEmpty()) {
    Text(
      stringResource(Res.string.filters_companies_empty),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    companies.forEach { company ->
      val remove = stringResource(Res.string.filters_company_remove, company)
      InputChip(
        selected = false,
        enabled = enabled,
        onClick = { onEvent(AiFiltersEvent.RemoveCompany(company)) },
        label = { Text(company) },
        leadingIcon = {
          Icon(
            Icons.Rounded.Block,
            contentDescription = null,
            modifier = Modifier.size(InputChipDefaults.IconSize),
          )
        },
        trailingIcon = {
          Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            modifier = Modifier.size(InputChipDefaults.IconSize),
          )
        },
        modifier = Modifier.semantics { contentDescription = remove },
      )
    }
  }
}

/** The form takes changes but while saving; offline they are sent once the computer is back. */
private fun editable(state: AiFiltersUiState): Boolean = !state.saving

/** Pinned under the form while it has unsaved changes: the note, discard and save. */
@Composable
private fun SaveBar(state: AiFiltersUiState, onEvent: (AiFiltersEvent) -> Unit) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    shape = MaterialTheme.shapes.extraLarge,
    shadowElevation = 6.dp,
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  ) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        stringResource(Res.string.filters_unsaved),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
      )
      TextButton(onClick = { onEvent(AiFiltersEvent.Discard) }, enabled = !state.saving) {
        Text(stringResource(Res.string.filters_discard))
      }
      Button(
        onClick = { onEvent(AiFiltersEvent.Save) },
        enabled = state.canSave,
        shapes = ButtonDefaults.shapes(),
      ) {
        if (state.saving) {
          LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
          Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(stringResource(Res.string.filters_save))
      }
    }
  }
}

/** The room the floating save bar takes over the end of the form. */
private val SaveBarSpace = 80.dp
