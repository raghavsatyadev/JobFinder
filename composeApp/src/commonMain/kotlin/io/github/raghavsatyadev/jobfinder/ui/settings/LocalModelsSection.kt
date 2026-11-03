@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ic_add_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_delete_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_download_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_memory_filled
import io.github.raghavsatyadev.jobfinder.resources.models_add
import io.github.raghavsatyadev.jobfinder.resources.models_add_suggested
import io.github.raghavsatyadev.jobfinder.resources.models_cancel
import io.github.raghavsatyadev.jobfinder.resources.models_check
import io.github.raghavsatyadev.jobfinder.resources.models_checking
import io.github.raghavsatyadev.jobfinder.resources.models_chip_failed
import io.github.raghavsatyadev.jobfinder.resources.models_chip_match
import io.github.raghavsatyadev.jobfinder.resources.models_chip_no_samples
import io.github.raghavsatyadev.jobfinder.resources.models_chip_partial
import io.github.raghavsatyadev.jobfinder.resources.models_downloading
import io.github.raghavsatyadev.jobfinder.resources.models_downloading_unknown
import io.github.raghavsatyadev.jobfinder.resources.models_in_use
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
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelTier
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

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
      Icon(
        vectorResource(Res.drawable.ic_add_filled),
        contentDescription = null,
        Modifier.size(ButtonDefaults.IconSize),
      )
      Spacer(Modifier.size(ButtonDefaults.IconSpacing))
      Text(stringResource(Res.string.models_add))
    }
  }
  state.add?.let { AddDialog(it, onEvent) }
  state.confirmDownload?.let { DownloadDialog(it, onEvent) }
  state.confirmRemove?.let { model ->
    ConfirmDialog(
      icon = vectorResource(Res.drawable.ic_delete_filled),
      title = stringResource(Res.string.models_remove_title, model),
      text = stringResource(Res.string.models_remove_text),
      confirmLabel = stringResource(Res.string.models_remove),
      onConfirm = { onEvent(LocalModelsEvent.ConfirmRemove) },
      onDismiss = { onEvent(LocalModelsEvent.DismissRemove) },
    )
  }
}

/** `jobfinder-qwen` and `jobfinder-qwen:latest` are the same model. */
internal fun sameModel(a: String, b: String) =
  a.trim().removeSuffix(":latest") == b.trim().removeSuffix(":latest")

@Composable
internal fun Profile(
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
    Icon(
      vectorResource(Res.drawable.ic_memory_filled),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
    )
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
        vectorResource(Res.drawable.ic_download_filled),
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
internal fun suggestedName(source: ModelSource): String =
  when (source) {
    is ModelSource.HuggingFace ->
      "jobfinder-" +
        (source.repo.substringAfterLast('/').removeSuffix("-GGUF") + "-" + source.quant).lowercase()
    is ModelSource.Library -> "jobfinder-" + source.name.lowercase()
    is ModelSource.Link -> ""
  }

@Composable
internal fun ModelRow(
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
internal fun CheckChip(check: ModelCheck?) {
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
internal fun Progress(state: LocalModelsUiState, onEvent: (LocalModelsEvent) -> Unit) {
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
