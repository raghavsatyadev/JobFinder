@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.raghavsatyadev.jobfinder.resources.ic_add_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_download_filled
import io.github.raghavsatyadev.jobfinder.resources.models_add_button
import io.github.raghavsatyadev.jobfinder.resources.models_add_title
import io.github.raghavsatyadev.jobfinder.resources.models_cancel
import io.github.raghavsatyadev.jobfinder.resources.models_download_confirm
import io.github.raghavsatyadev.jobfinder.resources.models_download_free
import io.github.raghavsatyadev.jobfinder.resources.models_download_note
import io.github.raghavsatyadev.jobfinder.resources.models_download_size
import io.github.raghavsatyadev.jobfinder.resources.models_download_size_unknown
import io.github.raghavsatyadev.jobfinder.resources.models_download_title
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
import io.github.raghavsatyadev.jobfinder.resources.models_library_hint
import io.github.raghavsatyadev.jobfinder.resources.models_library_name
import io.github.raghavsatyadev.jobfinder.resources.models_link_sha
import io.github.raghavsatyadev.jobfinder.resources.models_link_sha_hint
import io.github.raghavsatyadev.jobfinder.resources.models_link_url
import io.github.raghavsatyadev.jobfinder.resources.models_remove
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
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheckState
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun Result(
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
internal fun AddDialog(add: AddModelState, onEvent: (LocalModelsEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(LocalModelsEvent.CloseAdd) },
    icon = { Icon(vectorResource(Res.drawable.ic_add_filled), contentDescription = null) },
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
internal fun Tabs(selected: AddTab, onSelect: (AddTab) -> Unit) {
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
internal fun HuggingFaceTab(add: AddModelState, onEvent: (LocalModelsEvent) -> Unit) {
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
internal fun Choice(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
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
internal fun DownloadDialog(pending: PendingDownload, onEvent: (LocalModelsEvent) -> Unit) {
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
    icon = vectorResource(Res.drawable.ic_download_filled),
    title = stringResource(Res.string.models_download_title),
    text = text,
    confirmLabel = stringResource(Res.string.models_download_confirm),
    onConfirm = { onEvent(LocalModelsEvent.ConfirmDownload) },
    onDismiss = { onEvent(LocalModelsEvent.DismissDownload) },
  )
}

internal fun failureText(failure: ModelFailure) =
  when (failure) {
    ModelFailure.OLLAMA_NOT_RUNNING -> Res.string.models_failed_ollama
    ModelFailure.NOT_FOUND -> Res.string.models_failed_not_found
    ModelFailure.NO_SPACE -> Res.string.models_failed_space
    ModelFailure.DOWNLOAD_FAILED -> Res.string.models_failed_download
    ModelFailure.WRONG_CHECKSUM -> Res.string.models_failed_checksum
    ModelFailure.REGISTER_FAILED -> Res.string.models_failed_register
  }

/** 412000 as "412,000". */
internal fun thousands(value: Long): String =
  value.toString().reversed().chunked(3).joinToString(",").reversed()

/** Whole gigabytes, or one decimal under 10. */
internal fun gb(value: Double): String =
  if (value >= 10) value.roundToInt().toString() else oneDecimal(value)

internal fun oneDecimal(value: Double): String {
  val tenths = (value * 10).roundToInt()
  return "${tenths / 10}.${tenths % 10}"
}

internal const val BYTES_PER_GB = 1e9

internal const val PERCENT = 100

internal const val MAX_MISSES = 5
