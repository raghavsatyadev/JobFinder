@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package io.github.raghavsatyadev.jobfinder.ui.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.language_da
import io.github.raghavsatyadev.jobfinder.resources.language_de
import io.github.raghavsatyadev.jobfinder.resources.language_en
import io.github.raghavsatyadev.jobfinder.resources.language_es
import io.github.raghavsatyadev.jobfinder.resources.language_fr
import io.github.raghavsatyadev.jobfinder.resources.language_it
import io.github.raghavsatyadev.jobfinder.resources.language_nb
import io.github.raghavsatyadev.jobfinder.resources.language_nl
import io.github.raghavsatyadev.jobfinder.resources.language_pl
import io.github.raghavsatyadev.jobfinder.resources.language_pt
import io.github.raghavsatyadev.jobfinder.resources.language_sv
import io.github.raghavsatyadev.jobfinder.resources.language_unknown
import io.github.raghavsatyadev.jobfinder.resources.translate_card_hint
import io.github.raghavsatyadev.jobfinder.resources.translate_failed
import io.github.raghavsatyadev.jobfinder.resources.translate_from
import io.github.raghavsatyadev.jobfinder.resources.translate_queued
import io.github.raghavsatyadev.jobfinder.resources.translate_retry
import io.github.raghavsatyadev.jobfinder.resources.translate_setting_hint
import io.github.raghavsatyadev.jobfinder.resources.translate_setting_off
import io.github.raghavsatyadev.jobfinder.resources.translate_setting_title
import io.github.raghavsatyadev.jobfinder.resources.translate_show_original
import io.github.raghavsatyadev.jobfinder.resources.translate_show_translation
import io.github.raghavsatyadev.jobfinder.resources.translate_showing_original
import io.github.raghavsatyadev.jobfinder.shared.model.JobLanguages
import io.github.raghavsatyadev.jobfinder.shared.model.JobTranslation
import io.github.raghavsatyadev.jobfinder.shared.model.TranslationState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailEvent
import io.github.raghavsatyadev.jobfinder.ui.jobs.JobDetailUiState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The translation the detail shows: none while the user looks at the original. */
fun JobDetailUiState.shownTranslation(): JobTranslation? =
  if (showOriginal) null else translation?.translation ?: job?.translation

/** A [JobLanguages] code's name, such as "German". */
@Composable
fun languageName(code: String?): String =
  stringResource(LANGUAGE_NAMES[code] ?: Res.string.language_unknown)

/**
 * Above the job detail's sections: "Translated from German" with the way back to the original, the
 * translation on its way, or why there is none. Nothing for a job in the user's language.
 */
@Composable
fun TranslationBar(state: JobDetailUiState, onEvent: (JobDetailEvent) -> Unit) {
  val status = state.translation
  val ready = status?.translation ?: state.job?.translation
  when {
    ready != null ->
      Bar(
        text =
          if (state.showOriginal)
            stringResource(Res.string.translate_showing_original, languageName(ready.from))
          else stringResource(Res.string.translate_from, languageName(ready.from)),
        action =
          stringResource(
            if (state.showOriginal) Res.string.translate_show_translation
            else Res.string.translate_show_original
          ),
        onAction = { onEvent(JobDetailEvent.ToggleOriginal) },
      )
    status?.state == TranslationState.QUEUED ->
      Bar(
        text = stringResource(Res.string.translate_queued, languageName(status.from)),
        busy = true,
      )
    status?.state == TranslationState.FAILED ->
      Bar(
        text = stringResource(Res.string.translate_failed, languageName(status.from)),
        action =
          if (LocalComputerOffline.current) null else stringResource(Res.string.translate_retry),
        onAction = { onEvent(JobDetailEvent.RetryTranslation) },
      )
  }
}

@Composable
private fun Bar(
  text: String,
  action: String? = null,
  onAction: () -> Unit = {},
  busy: Boolean = false,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).semantics {
        liveRegion = LiveRegionMode.Polite
      },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (busy) LoadingIndicator(Modifier.size(24.dp))
      else
        Icon(
          Icons.Rounded.Translate,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp),
        )
      Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
      )
      if (action != null) {
        TextButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(action) }
      }
    }
  }
}

/** Under a card's translated title: the original is in another language. */
@Composable
fun TranslatedMark(from: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.padding(vertical = 2.dp),
  ) {
    Icon(
      Icons.Rounded.Translate,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(14.dp),
    )
    Text(
      stringResource(Res.string.translate_card_hint, languageName(from)),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}

/** Settings → Scanning: the language jobs are translated into, or none. */
@Composable
fun TranslateToPicker(current: String?, enabled: Boolean, onPick: (String?) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val label =
    if (current == null) stringResource(Res.string.translate_setting_off) else languageName(current)
  Column(
    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      stringResource(Res.string.translate_setting_title),
      style = MaterialTheme.typography.bodyLarge,
    )
    Text(
      stringResource(Res.string.translate_setting_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { expanded = it && enabled },
    ) {
      OutlinedTextField(
        value = label,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(Res.string.translate_setting_title)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        modifier =
          Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
            .widthIn(max = 360.dp)
            .fillMaxWidth(),
      )
      ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        (JobLanguages.ALL + listOf<String?>(null)).forEach { code ->
          DropdownMenuItem(
            text = {
              Text(
                if (code == null) stringResource(Res.string.translate_setting_off)
                else languageName(code)
              )
            },
            onClick = {
              expanded = false
              onPick(code)
            },
            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
          )
        }
      }
    }
  }
}

private val LANGUAGE_NAMES: Map<String, StringResource> =
  mapOf(
    "en" to Res.string.language_en,
    "de" to Res.string.language_de,
    "fr" to Res.string.language_fr,
    "nl" to Res.string.language_nl,
    "es" to Res.string.language_es,
    "it" to Res.string.language_it,
    "pt" to Res.string.language_pt,
    "sv" to Res.string.language_sv,
    "da" to Res.string.language_da,
    "nb" to Res.string.language_nb,
    "pl" to Res.string.language_pl,
  )
