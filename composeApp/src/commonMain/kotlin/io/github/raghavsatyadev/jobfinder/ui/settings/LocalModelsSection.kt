@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.models_add
import io.github.raghavsatyadev.jobfinder.resources.models_add_button
import io.github.raghavsatyadev.jobfinder.resources.models_add_suggested
import io.github.raghavsatyadev.jobfinder.resources.models_add_title
import io.github.raghavsatyadev.jobfinder.resources.models_cancel
import io.github.raghavsatyadev.jobfinder.resources.models_check
import io.github.raghavsatyadev.jobfinder.resources.models_checking
import io.github.raghavsatyadev.jobfinder.resources.models_chip_failed
import io.github.raghavsatyadev.jobfinder.resources.models_chip_match
import io.github.raghavsatyadev.jobfinder.resources.models_chip_no_samples
import io.github.raghavsatyadev.jobfinder.resources.models_chip_partial
import io.github.raghavsatyadev.jobfinder.resources.models_download_confirm
import io.github.raghavsatyadev.jobfinder.resources.models_download_free
import io.github.raghavsatyadev.jobfinder.resources.models_download_note
import io.github.raghavsatyadev.jobfinder.resources.models_download_size
import io.github.raghavsatyadev.jobfinder.resources.models_download_size_unknown
import io.github.raghavsatyadev.jobfinder.resources.models_download_title
import io.github.raghavsatyadev.jobfinder.resources.models_downloading
import io.github.raghavsatyadev.jobfinder.resources.models_downloading_unknown
import io.github.raghavsatyadev.jobfinder.resources.models_failed_checksum
import io.github.raghavsatyadev.jobfinder.resources.models_failed_download
import io.github.raghavsatyadev.jobfinder.resources.models_failed_not_found
import io.github.raghavsatyadev.jobfinder.resources.models_failed_ollama
import io.github.raghavsatyadev.jobfinder.resources.models_failed_register
import io.github.raghavsatyadev.jobfinder.resources.models_failed_space
import io.github.raghavsatyadev.jobfinder.resources.models_hf_downloads
import io.github.raghavsatyadev.jobfinder.resources.models_hf_nothing
import io.github.raghavsatyadev.jobfinder.resources.models_hf_quant
import io.github.raghavsatyadev.jobfinder.resources.models_hf_query
import io.github.raghavsatyadev.jobfinder.resources.models_hf_search
import io.github.raghavsatyadev.jobfinder.resources.models_in_use
import io.github.raghavsatyadev.jobfinder.resources.models_library_hint
import io.github.raghavsatyadev.jobfinder.resources.models_library_name
import io.github.raghavsatyadev.jobfinder.resources.models_link_sha
import io.github.raghavsatyadev.jobfinder.resources.models_link_sha_hint
import io.github.raghavsatyadev.jobfinder.resources.models_link_url
import io.github.raghavsatyadev.jobfinder.resources.models_never_checked
import io.github.raghavsatyadev.jobfinder.resources.models_none
import io.github.raghavsatyadev.jobfinder.resources.models_ollama_off
import io.github.raghavsatyadev.jobfinder.resources.models_pc
import io.github.raghavsatyadev.jobfinder.resources.models_pc_disk
import io.github.raghavsatyadev.jobfinder.resources.models_pc_gpu
import io.github.raghavsatyadev.jobfinder.resources.models_pc_memory
import io.github.raghavsatyadev.jobfinder.resources.models_pc_unknown
import io.github.raghavsatyadev.jobfinder.resources.models_registering
import io.github.raghavsatyadev.jobfinder.resources.models_remove
import io.github.raghavsatyadev.jobfinder.resources.models_remove_text
import io.github.raghavsatyadev.jobfinder.resources.models_remove_title
import io.github.raghavsatyadev.jobfinder.resources.models_result_detail
import io.github.raghavsatyadev.jobfinder.resources.models_result_dismiss
import io.github.raghavsatyadev.jobfinder.resources.models_result_failed
import io.github.raghavsatyadev.jobfinder.resources.models_result_keep
import io.github.raghavsatyadev.jobfinder.resources.models_result_match
import io.github.raghavsatyadev.jobfinder.resources.models_result_memory
import io.github.raghavsatyadev.jobfinder.resources.models_result_miss
import io.github.raghavsatyadev.jobfinder.resources.models_result_no_answer
import io.github.raghavsatyadev.jobfinder.resources.models_result_no_samples
import io.github.raghavsatyadev.jobfinder.resources.models_result_no_samples_text
import io.github.raghavsatyadev.jobfinder.resources.models_result_partial
import io.github.raghavsatyadev.jobfinder.resources.models_result_speed
import io.github.raghavsatyadev.jobfinder.resources.models_tab_hf
import io.github.raghavsatyadev.jobfinder.resources.models_tab_library
import io.github.raghavsatyadev.jobfinder.resources.models_tab_link
import io.github.raghavsatyadev.jobfinder.resources.models_tier_full
import io.github.raghavsatyadev.jobfinder.resources.models_tier_medium
import io.github.raghavsatyadev.jobfinder.resources.models_tier_online
import io.github.raghavsatyadev.jobfinder.resources.models_tier_row_full
import io.github.raghavsatyadev.jobfinder.resources.models_tier_row_medium
import io.github.raghavsatyadev.jobfinder.resources.models_tier_row_online
import io.github.raghavsatyadev.jobfinder.resources.models_tier_row_small
import io.github.raghavsatyadev.jobfinder.resources.models_tier_row_split
import io.github.raghavsatyadev.jobfinder.resources.models_tier_small
import io.github.raghavsatyadev.jobfinder.resources.models_tier_split
import io.github.raghavsatyadev.jobfinder.resources.models_tier_unknown
import io.github.raghavsatyadev.jobfinder.resources.models_tiers_title
import io.github.raghavsatyadev.jobfinder.resources.models_title
import io.github.raghavsatyadev.jobfinder.resources.models_use
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheckState
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModel
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelTier
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * The local models part of Settings → AI model: the PC's tier, the models Ollama has with their
 * last check, adding one and the result of a check. [active] is the model in the Model field;
 * [onUse] puts another one there.
 */
@Composable
fun LocalModelsSection(
  state: LocalModelsUiState,
  active: String,
  onEvent: (LocalModelsEvent) -> Unit,
  onUse: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    HorizontalDivider()
    Text(stringResource(Res.string.models_title), style = MaterialTheme.typography.titleSmall)
    state.profile?.let { Profile(it, state, onEvent) }
    if (!state.ollamaRunning) {
      Text(
        stringResource(Res.string.models_ollama_off),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    } else if (!state.loading && state.models.isEmpty()) {
      Text(stringResource(Res.string.models_none), style = MaterialTheme.typography.bodyMedium)
    }
    state.models.forEach { model ->
      ModelRow(model, inUse = sameModel(model.name, active), busy = state.busy, onEvent, onUse)
    }
    Progress(state, onEvent)
    state.result?.let { Result(it, onEvent, onUse) }
    OutlinedButton(
      onClick = { onEvent(LocalModelsEvent.OpenAdd) },
      enabled = state.ollamaRunning && !state.busy,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(stringResource(Res.string.models_add))
    }
  }
  state.add?.let { AddDialog(it, onEvent) }
  state.confirmDownload?.let { DownloadDialog(it, onEvent) }
  state.confirmRemove?.let { model ->
    ConfirmDialog(
      icon = Icons.Rounded.Delete,
      title = stringResource(Res.string.models_remove_title, model),
      text = stringResource(Res.string.models_remove_text),
      confirmLabel = stringResource(Res.string.models_remove),
      onConfirm = { onEvent(LocalModelsEvent.ConfirmRemove) },
      onDismiss = { onEvent(LocalModelsEvent.DismissRemove) },
    )
  }
}

/** `jobfinder-qwen` and `jobfinder-qwen:latest` are the same model. */
private fun sameModel(a: String, b: String) =
  a.trim().removeSuffix(":latest") == b.trim().removeSuffix(":latest")

@Composable
private fun Profile(
  profile: PcProfile,
  state: LocalModelsUiState,
  onEvent: (LocalModelsEvent) -> Unit,
) {
  val parts = buildList {
    profile.ramGb?.let { add(stringResource(Res.string.models_pc_memory, gb(it))) }
      ?: add(stringResource(Res.string.models_pc_unknown))
    if (profile.gpuName != null && profile.vramGb != null) {
      add(stringResource(Res.string.models_pc_gpu, profile.gpuName, gb(profile.vramGb)))
    }
    profile.freeDiskGb?.let { add(stringResource(Res.string.models_pc_disk, gb(it))) }
  }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(Icons.Rounded.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    Text(
      stringResource(Res.string.models_pc, parts.joinToString(", ")),
      style = MaterialTheme.typography.bodyMedium,
    )
  }
  val tier = profile.tier
  Text(
    stringResource(
      when (tier) {
        ModelTier.FULL -> Res.string.models_tier_full
        ModelTier.SPLIT -> Res.string.models_tier_split
        ModelTier.MEDIUM -> Res.string.models_tier_medium
        ModelTier.SMALL -> Res.string.models_tier_small
        ModelTier.ONLINE -> Res.string.models_tier_online
        ModelTier.UNKNOWN -> Res.string.models_tier_unknown
      }
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  // The full tier's model is the app's own, which the setup assistant installs.
  val suggested = tier.source?.takeIf { tier != ModelTier.FULL && tier != ModelTier.SPLIT }
  if (suggested != null && state.models.none { sameModel(it.name, suggestedName(suggested)) }) {
    Button(
      onClick = { onEvent(LocalModelsEvent.RequestAdd(suggested)) },
      enabled = state.ollamaRunning && !state.busy,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.Rounded.Download,
        contentDescription = null,
        Modifier.size(ButtonDefaults.IconSize),
      )
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(stringResource(Res.string.models_add_suggested))
    }
  }
  Card(
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = MaterialTheme.shapes.medium,
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        stringResource(Res.string.models_tiers_title),
        style = MaterialTheme.typography.labelLarge,
      )
      listOf(
          ModelTier.FULL to Res.string.models_tier_row_full,
          ModelTier.SPLIT to Res.string.models_tier_row_split,
          ModelTier.MEDIUM to Res.string.models_tier_row_medium,
          ModelTier.SMALL to Res.string.models_tier_row_small,
          ModelTier.ONLINE to Res.string.models_tier_row_online,
        )
        .forEach { (rowTier, text) ->
          Text(
            stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color =
              if (rowTier == tier) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
    }
  }
}

/** The name the app registers [source] as; the same rule as the desktop's `ModelNames`. */
private fun suggestedName(source: ModelSource): String =
  when (source) {
    is ModelSource.HuggingFace ->
      "jobfinder-" +
        (source.repo.substringAfterLast('/').removeSuffix("-GGUF") + "-" + source.quant).lowercase()
    is ModelSource.Library -> "jobfinder-" + source.name.lowercase()
    is ModelSource.Link -> ""
  }

@Composable
private fun ModelRow(
  model: LocalModel,
  inUse: Boolean,
  busy: Boolean,
  onEvent: (LocalModelsEvent) -> Unit,
  onUse: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text(model.name, style = MaterialTheme.typography.bodyLarge)
        Text(
          "${gb(model.sizeBytes / BYTES_PER_GB)} GB",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      CheckChip(model.check)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (inUse) {
        // Same box as a TextButton, so the label lines up with the buttons beside it.
        Box(
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .heightIn(min = ButtonDefaults.MinHeight)
              .padding(ButtonDefaults.TextButtonContentPadding),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            stringResource(Res.string.models_in_use),
            style = ButtonDefaults.textStyleFor(ButtonDefaults.MinHeight),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      } else {
        TextButton(
          onClick = { onUse(model.name) },
          enabled = !busy,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.models_use))
        }
      }
      TextButton(
        onClick = { onEvent(LocalModelsEvent.Check(model.name)) },
        enabled = !busy,
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.models_check))
      }
      if (model.name.startsWith("jobfinder-") && !inUse) {
        TextButton(
          onClick = { onEvent(LocalModelsEvent.RequestRemove(model.name)) },
          enabled = !busy,
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.models_remove))
        }
      }
    }
  }
}

@Composable
private fun CheckChip(check: ModelCheck?) {
  // A status, not an action: a pill like the job statuses rather than a chip that looks tappable.
  val (text, tone) =
    when (check?.state) {
      null -> stringResource(Res.string.models_never_checked) to StatusTone.Muted
      ModelCheckState.MATCH -> stringResource(Res.string.models_chip_match) to StatusTone.Positive
      ModelCheckState.PARTIAL ->
        stringResource(Res.string.models_chip_partial, check.percent) to StatusTone.Neutral
      ModelCheckState.FAILED -> stringResource(Res.string.models_chip_failed) to StatusTone.Negative
      ModelCheckState.NO_SAMPLES ->
        stringResource(Res.string.models_chip_no_samples) to StatusTone.Muted
    }
  StatusPill(text, tone)
}

@Composable
private fun Progress(state: LocalModelsUiState, onEvent: (LocalModelsEvent) -> Unit) {
  val progress = state.progress
  if (progress is ModelProgress.Failed) {
    Text(
      stringResource(failureText(progress.failure)),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.error,
    )
    return
  }
  if (!state.busy || progress == null) return
  val (text, fraction) =
    when (progress) {
      is ModelProgress.Downloading -> {
        val total = progress.total
        if (total != null && total > 0) {
          val share = progress.done.toFloat() / total
          stringResource(Res.string.models_downloading, "${(share * PERCENT).roundToInt()}%") to
            share
        } else {
          stringResource(Res.string.models_downloading_unknown) to null
        }
      }
      ModelProgress.Registering -> stringResource(Res.string.models_registering) to null
      is ModelProgress.Checking ->
        stringResource(Res.string.models_checking, progress.done, progress.total) to
          (if (progress.total > 0) progress.done.toFloat() / progress.total else null)
      else -> "" to null
    }
  Text(text, style = MaterialTheme.typography.bodyMedium)
  if (fraction != null) {
    WavyProgress(fraction)
  } else {
    WavyProgress()
  }
  TextButton(onClick = { onEvent(LocalModelsEvent.Cancel) }, shapes = ButtonDefaults.shapes()) {
    Text(stringResource(Res.string.models_cancel))
  }
}

@Composable
private fun Result(
  check: ModelCheck,
  onEvent: (LocalModelsEvent) -> Unit,
  onUse: (String) -> Unit,
) {
  val (title, color) =
    when (check.state) {
      ModelCheckState.MATCH ->
        stringResource(Res.string.models_result_match, check.model) to
          MaterialTheme.colorScheme.primary
      ModelCheckState.PARTIAL ->
        stringResource(Res.string.models_result_partial, check.model, check.percent) to
          MaterialTheme.colorScheme.tertiary
      ModelCheckState.FAILED ->
        stringResource(Res.string.models_result_failed, check.model) to
          MaterialTheme.colorScheme.error
      ModelCheckState.NO_SAMPLES ->
        stringResource(Res.string.models_result_no_samples, check.model) to
          MaterialTheme.colorScheme.onSurface
    }
  Card(
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    shape = MaterialTheme.shapes.large,
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = color)
      if (check.state == ModelCheckState.NO_SAMPLES) {
        Text(
          stringResource(Res.string.models_result_no_samples_text),
          style = MaterialTheme.typography.bodyMedium,
        )
      } else {
        Text(
          stringResource(
            Res.string.models_result_detail,
            check.agreed,
            check.checks,
            check.failedAnswers,
          ),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      if (check.secondsPerJob != null || check.secondsPerReview != null) {
        Text(
          stringResource(
            Res.string.models_result_speed,
            check.secondsPerJob?.let(::oneDecimal) ?: "–",
            check.secondsPerReview?.let(::oneDecimal) ?: "–",
          ),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      check.memoryGb?.let { memory ->
        Text(
          stringResource(
            Res.string.models_result_memory,
            oneDecimal(memory),
            oneDecimal(check.gpuMemoryGb ?: 0.0),
          ),
          style = MaterialTheme.typography.bodySmall,
        )
      }
      val noAnswer = stringResource(Res.string.models_result_no_answer)
      check.misses.take(MAX_MISSES).forEach { miss ->
        Text(
          stringResource(
            Res.string.models_result_miss,
            miss.jobTitle,
            miss.company,
            miss.expected,
            miss.got ?: noAnswer,
          ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (check.state == ModelCheckState.MATCH || check.state == ModelCheckState.PARTIAL) {
          Button(
            onClick = {
              onUse(check.model)
              onEvent(LocalModelsEvent.DismissResult)
            },
            shapes = ButtonDefaults.shapes(),
          ) {
            Text(stringResource(Res.string.models_result_keep))
          }
        }
        if (check.model.startsWith("jobfinder-")) {
          OutlinedButton(
            onClick = { onEvent(LocalModelsEvent.RequestRemove(check.model)) },
            shapes = ButtonDefaults.shapes(),
          ) {
            Text(stringResource(Res.string.models_remove))
          }
        }
        TextButton(
          onClick = { onEvent(LocalModelsEvent.DismissResult) },
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(stringResource(Res.string.models_result_dismiss))
        }
      }
    }
  }
}

@Composable
private fun AddDialog(add: AddModelState, onEvent: (LocalModelsEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(LocalModelsEvent.CloseAdd) },
    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
    title = { Text(stringResource(Res.string.models_add_title)) },
    text = {
      Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Tabs(add.tab) { onEvent(LocalModelsEvent.SetTab(it)) }
        when (add.tab) {
          AddTab.LIBRARY ->
            OutlinedTextField(
              value = add.libraryName,
              onValueChange = { onEvent(LocalModelsEvent.SetLibraryName(it)) },
              label = { Text(stringResource(Res.string.models_library_name)) },
              supportingText = { Text(stringResource(Res.string.models_library_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          AddTab.HUGGING_FACE -> HuggingFaceTab(add, onEvent)
          AddTab.LINK -> {
            OutlinedTextField(
              value = add.linkUrl,
              onValueChange = { onEvent(LocalModelsEvent.SetLinkUrl(it)) },
              label = { Text(stringResource(Res.string.models_link_url)) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = add.linkSha,
              onValueChange = { onEvent(LocalModelsEvent.SetLinkSha(it)) },
              label = { Text(stringResource(Res.string.models_link_sha)) },
              supportingText = { Text(stringResource(Res.string.models_link_sha_hint)) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onEvent(LocalModelsEvent.RequestAdd()) },
        enabled = add.source() != null,
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.models_add_button))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onEvent(LocalModelsEvent.CloseAdd) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.models_cancel))
      }
    },
  )
}

@Composable
private fun Tabs(selected: AddTab, onSelect: (AddTab) -> Unit) {
  val tabs =
    listOf(
      AddTab.LIBRARY to Res.string.models_tab_library,
      AddTab.HUGGING_FACE to Res.string.models_tab_hf,
      AddTab.LINK to Res.string.models_tab_link,
    )
  // Secondary tabs: they switch views inside this section, below the screen's own level.
  SecondaryTabRow(
    selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
    containerColor = Color.Transparent,
  ) {
    tabs.forEach { (tab, label) ->
      Tab(
        selected = tab == selected,
        onClick = { onSelect(tab) },
        text = { Text(stringResource(label), maxLines = 1) },
        selectedContentColor = MaterialTheme.colorScheme.primary,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun HuggingFaceTab(add: AddModelState, onEvent: (LocalModelsEvent) -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedTextField(
      value = add.hfQuery,
      onValueChange = { onEvent(LocalModelsEvent.SetHfQuery(it)) },
      label = { Text(stringResource(Res.string.models_hf_query)) },
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { onEvent(LocalModelsEvent.SearchHf) }),
      modifier = Modifier.weight(1f),
    )
    OutlinedButton(
      onClick = { onEvent(LocalModelsEvent.SearchHf) },
      enabled = add.hfQuery.isNotBlank() && !add.hfSearching,
      shapes = ButtonDefaults.shapes(),
    ) {
      Text(stringResource(Res.string.models_hf_search))
    }
  }
  if (add.hfSearching) WavyProgress()
  if (add.hfNothing) Text(stringResource(Res.string.models_hf_nothing))
  Column(Modifier.selectableGroup().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
    add.hfResults.forEach { repo ->
      Choice(
        selected = repo.id == add.hfRepo,
        title = repo.id,
        detail = stringResource(Res.string.models_hf_downloads, thousands(repo.downloads)),
      ) {
        onEvent(LocalModelsEvent.PickRepo(repo.id))
      }
    }
  }
  if (add.quants.isNotEmpty()) {
    Text(stringResource(Res.string.models_hf_quant), style = MaterialTheme.typography.labelLarge)
    Column(Modifier.selectableGroup()) {
      add.quants.forEach { quant ->
        Choice(
          selected = quant.name == add.quant,
          title = quant.name,
          detail = "${gb(quant.sizeBytes / BYTES_PER_GB)} GB",
        ) {
          onEvent(LocalModelsEvent.PickQuant(quant.name))
        }
      }
    }
  }
}

@Composable
private fun Choice(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Column(Modifier.padding(start = 12.dp)) {
      Text(title, style = MaterialTheme.typography.bodyMedium)
      Text(
        detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DownloadDialog(pending: PendingDownload, onEvent: (LocalModelsEvent) -> Unit) {
  val name =
    when (val source = pending.source) {
      is ModelSource.Library -> source.name
      is ModelSource.HuggingFace -> "${source.repo} ${source.quant}"
      is ModelSource.Link -> source.url.substringBefore('?').substringAfterLast('/')
    }
  val text = buildString {
    append(
      pending.sizeBytes?.let {
        stringResource(Res.string.models_download_size, name, gb(it / BYTES_PER_GB))
      } ?: stringResource(Res.string.models_download_size_unknown, name)
    )
    pending.freeGb?.let {
      append(' ').append(stringResource(Res.string.models_download_free, gb(it)))
    }
    append("\n\n").append(stringResource(Res.string.models_download_note))
  }
  ConfirmDialog(
    icon = Icons.Rounded.Download,
    title = stringResource(Res.string.models_download_title),
    text = text,
    confirmLabel = stringResource(Res.string.models_download_confirm),
    onConfirm = { onEvent(LocalModelsEvent.ConfirmDownload) },
    onDismiss = { onEvent(LocalModelsEvent.DismissDownload) },
  )
}

private fun failureText(failure: ModelFailure) =
  when (failure) {
    ModelFailure.OLLAMA_NOT_RUNNING -> Res.string.models_failed_ollama
    ModelFailure.NOT_FOUND -> Res.string.models_failed_not_found
    ModelFailure.NO_SPACE -> Res.string.models_failed_space
    ModelFailure.DOWNLOAD_FAILED -> Res.string.models_failed_download
    ModelFailure.WRONG_CHECKSUM -> Res.string.models_failed_checksum
    ModelFailure.REGISTER_FAILED -> Res.string.models_failed_register
  }

/** 412000 as "412,000". */
private fun thousands(value: Long): String =
  value.toString().reversed().chunked(3).joinToString(",").reversed()

/** Whole gigabytes, or one decimal under 10. */
private fun gb(value: Double): String =
  if (value >= 10) value.roundToInt().toString() else oneDecimal(value)

private fun oneDecimal(value: Double): String {
  val tenths = (value * 10).roundToInt()
  return "${tenths / 10}.${tenths % 10}"
}

private const val BYTES_PER_GB = 1e9
private const val PERCENT = 100
private const val MAX_MISSES = 5
