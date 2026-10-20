@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description_empty
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description_failed
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_description_loading
import io.github.raghavsatyadev.jobfinder.resources.jobs_detail_excluded_reason
import io.github.raghavsatyadev.jobfinder.resources.jobs_label_none
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.ui.components.label
import io.github.raghavsatyadev.jobfinder.ui.components.stale
import io.github.raghavsatyadev.jobfinder.ui.translate.shownTranslation
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ExcludedReason(reason: String) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.errorContainer,
    contentColor = MaterialTheme.colorScheme.onErrorContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp)) {
      Text(
        stringResource(Res.string.jobs_detail_excluded_reason),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
      )
      Text(reason, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
internal fun Section(
  title: String,
  action: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      action?.invoke()
    }
    content()
  }
}

/** One label per job, as in the old app; "None" clears it. */
@Composable
internal fun LabelPicker(current: JobLabel?, onPick: (JobLabel?) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    LabelChip(stringResource(Res.string.jobs_label_none), current == null) { onPick(null) }
    JobLabel.CHOICES.forEach { label ->
      LabelChip(stringResource(label.label), current == label) { onPick(label) }
    }
  }
}

@Composable
internal fun LabelChip(text: String, selected: Boolean, onClick: () -> Unit) {
  FilterChip(
    selected = selected,
    onClick = onClick,
    label = { Text(text) },
    leadingIcon =
      if (selected) {
        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
      } else null,
  )
}

@Composable
internal fun Description(state: JobDetailUiState, job: Job) {
  val description = state.shownTranslation()?.description ?: job.description
  when {
    !description.isNullOrBlank() ->
      DescriptionText(
        description,
        Modifier.stale(
          state.descriptionLoading,
          stringResource(Res.string.jobs_detail_description_loading),
        ),
      )
    state.descriptionLoading -> {
      val text = stringResource(Res.string.jobs_detail_description_loading)
      Row(verticalAlignment = Alignment.CenterVertically) {
        LoadingIndicator(Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
      }
    }
    state.descriptionFailed ->
      Text(
        stringResource(Res.string.jobs_detail_description_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    else ->
      Text(
        stringResource(Res.string.jobs_detail_description_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
  }
}

/** A job description's Markdown, tidied by [DescriptionFormat]. */
@Composable
fun DescriptionText(markdown: String, modifier: Modifier = Modifier) {
  Markdown(
    content = remember(markdown) { DescriptionFormat.tidy(markdown) },
    typography = descriptionTypography(),
    padding = descriptionPadding(),
    modifier = modifier.fillMaxWidth(),
  )
}

/** Room between paragraphs and around lists, tight between a list's items. */
@Composable
private fun descriptionPadding() =
  markdownPadding(
    block = 6.dp,
    list = 4.dp,
    listItemTop = 2.dp,
    listItemBottom = 2.dp,
    listIndent = 12.dp,
  )

/** Headings sized for a pane, not a page: a job description's `##` must not out-shout the title. */
@Composable
private fun descriptionTypography() =
  MaterialTheme.typography.let { type ->
    // Bold and a step above the text, so sections stand out without out-shouting the job title.
    val section = type.titleLarge.copy(fontWeight = FontWeight.Bold)
    val subsection = type.titleMedium.copy(fontWeight = FontWeight.Bold)
    markdownTypography(
      h1 = section,
      h2 = section,
      h3 = section,
      h4 = subsection,
      h5 = subsection,
      h6 = subsection,
      text = type.bodyLarge,
      paragraph = type.bodyLarge,
    )
  }
