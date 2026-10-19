@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.resume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.linkedin_how
import io.github.raghavsatyadev.jobfinder.resources.linkedin_import
import io.github.raghavsatyadev.jobfinder.resources.linkedin_import_again
import io.github.raghavsatyadev.jobfinder.resources.linkedin_importing
import io.github.raghavsatyadev.jobfinder.resources.linkedin_info
import io.github.raghavsatyadev.jobfinder.resources.linkedin_none
import io.github.raghavsatyadev.jobfinder.resources.linkedin_privacy
import io.github.raghavsatyadev.jobfinder.resources.linkedin_remove
import io.github.raghavsatyadev.jobfinder.resources.linkedin_steps
import io.github.raghavsatyadev.jobfinder.resources.linkedin_title
import io.github.raghavsatyadev.jobfinder.resources.pdf_hint
import io.github.raghavsatyadev.jobfinder.resources.pdf_making
import io.github.raghavsatyadev.jobfinder.resources.pdf_page_a4
import io.github.raghavsatyadev.jobfinder.resources.pdf_page_letter
import io.github.raghavsatyadev.jobfinder.resources.pdf_page_size
import io.github.raghavsatyadev.jobfinder.resources.pdf_pages
import io.github.raghavsatyadev.jobfinder.resources.pdf_pages_hint
import io.github.raghavsatyadev.jobfinder.resources.pdf_pages_one
import io.github.raghavsatyadev.jobfinder.resources.pdf_pages_two
import io.github.raghavsatyadev.jobfinder.resources.pdf_save
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_down
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_up
import io.github.raghavsatyadev.jobfinder.resources.pdf_sections
import io.github.raghavsatyadev.jobfinder.resources.pdf_title
import io.github.raghavsatyadev.jobfinder.resources.pdf_use_linkedin
import io.github.raghavsatyadev.jobfinder.shared.model.PdfPageSize
import io.github.raghavsatyadev.jobfinder.shared.model.PdfSection
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import io.github.raghavsatyadev.jobfinder.ui.jobs.relativeTime
import io.github.raghavsatyadev.jobfinder.ui.platform.FilePicker
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel

/** The resume PDF part of Settings → Resume; nothing where no computer makes PDFs. */
@Composable
fun ResumePdfSettings(now: Long) {
  val koin = getKoin()
  val available =
    remember(koin) {
      koin.getOrNull<ResumePdfRepository>() != null && koin.getOrNull<FilePicker>() != null
    }
  if (!available) return
  val viewModel = koinViewModel<ResumePdfViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  ResumePdfContent(state, now, viewModel::onEvent)
}

@Composable
fun ResumePdfContent(state: ResumePdfUiState, now: Long, onEvent: (ResumePdfEvent) -> Unit) {
  val offline = LocalComputerOffline.current
  val idle = state.busy == null && !offline
  val prefs = state.status.prefs
  Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(Res.string.pdf_title), style = MaterialTheme.typography.titleMedium)
    Muted(stringResource(Res.string.pdf_hint))
    if (state.busy == PdfBusy.LOADING) {
      WavyProgress()
      return@Column
    }
    LinkedInPart(state, now, idle, onEvent)
    Label(stringResource(Res.string.pdf_page_size))
    Chips(
      listOf(
        stringResource(Res.string.pdf_page_a4) to (prefs.pageSize == PdfPageSize.A4),
        stringResource(Res.string.pdf_page_letter) to (prefs.pageSize == PdfPageSize.LETTER),
      ),
      enabled = idle,
    ) {
      onEvent(ResumePdfEvent.SetPageSize(if (it == 0) PdfPageSize.A4 else PdfPageSize.LETTER))
    }
    Label(stringResource(Res.string.pdf_pages))
    Chips(
      listOf(
        stringResource(Res.string.pdf_pages_one) to (prefs.maxPages == 1),
        stringResource(Res.string.pdf_pages_two) to (prefs.maxPages == 2),
      ),
      enabled = idle,
    ) {
      onEvent(ResumePdfEvent.SetPages(it + 1))
    }
    Muted(stringResource(Res.string.pdf_pages_hint))
    Row(
      Modifier.toggleable(
        value = prefs.useLinkedIn,
        enabled = idle,
        role = Role.Switch,
        onValueChange = { onEvent(ResumePdfEvent.SetUseLinkedIn(it)) },
      ),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(stringResource(Res.string.pdf_use_linkedin), Modifier.weight(1f))
      Switch(checked = prefs.useLinkedIn, onCheckedChange = null, enabled = idle)
    }
    Label(stringResource(Res.string.pdf_sections))
    SectionOrder(prefs.fullOrder, prefs.hidden, idle, onEvent)
    Button(
      onClick = { onEvent(ResumePdfEvent.Save) },
      enabled = idle && (state.status.hasResume || state.status.linkedIn != null),
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(ButtonDefaults.IconSize))
      Text(
        stringResource(Res.string.pdf_save),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    if (state.busy == PdfBusy.MAKING) {
      Muted(stringResource(Res.string.pdf_making))
      WavyProgress()
    }
    state.outcome?.let { PdfOutcomeText(it) }
  }
}

@Composable
private fun LinkedInPart(
  state: ResumePdfUiState,
  now: Long,
  idle: Boolean,
  onEvent: (ResumePdfEvent) -> Unit,
) {
  val linkedIn = state.status.linkedIn
  Label(stringResource(Res.string.linkedin_title))
  if (linkedIn == null) {
    Text(stringResource(Res.string.linkedin_none), style = MaterialTheme.typography.bodyMedium)
  } else {
    Text(
      stringResource(
        Res.string.linkedin_info,
        relativeTime(linkedIn.importedAt, now),
        linkedIn.positions,
        linkedIn.skills,
        linkedIn.projects,
        linkedIn.education,
      ),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
    )
  }
  if (state.busy == PdfBusy.IMPORTING) {
    Muted(stringResource(Res.string.linkedin_importing))
    WavyProgress()
  }
  state.error?.let {
    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
  }
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    OutlinedButton(
      onClick = { onEvent(ResumePdfEvent.ImportLinkedIn) },
      enabled = idle,
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(Icons.Outlined.FileUpload, null, Modifier.size(ButtonDefaults.IconSize))
      Text(
        stringResource(
          if (linkedIn == null) Res.string.linkedin_import else Res.string.linkedin_import_again
        ),
        Modifier.padding(start = ButtonDefaults.IconSpacing),
      )
    }
    if (linkedIn != null) {
      OutlinedButton(
        onClick = { onEvent(ResumePdfEvent.RemoveLinkedIn) },
        enabled = idle,
        shapes = ButtonDefaults.shapes(),
      ) {
        Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(ButtonDefaults.IconSize))
        Text(
          stringResource(Res.string.linkedin_remove),
          Modifier.padding(start = ButtonDefaults.IconSpacing),
        )
      }
    }
    TextButton(onClick = { onEvent(ResumePdfEvent.ToggleSteps) }) {
      Text(stringResource(Res.string.linkedin_how))
    }
  }
  if (state.showSteps) {
    Text(stringResource(Res.string.linkedin_steps), style = MaterialTheme.typography.bodyMedium)
  }
  Muted(stringResource(Res.string.linkedin_privacy))
}

/** Each section with its tick and up and down buttons. */
@Composable
private fun SectionOrder(
  order: List<PdfSection>,
  hidden: Set<PdfSection>,
  enabled: Boolean,
  onEvent: (ResumePdfEvent) -> Unit,
) {
  Column {
    order.forEachIndexed { i, section ->
      val name = stringResource(section.label)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
          checked = section !in hidden,
          onCheckedChange = { onEvent(ResumePdfEvent.SetShown(section, it)) },
          enabled = enabled,
        )
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IconButton(
          onClick = { onEvent(ResumePdfEvent.Move(section, up = true)) },
          enabled = enabled && i > 0,
        ) {
          Icon(Icons.Outlined.KeyboardArrowUp, stringResource(Res.string.pdf_section_up, name))
        }
        IconButton(
          onClick = { onEvent(ResumePdfEvent.Move(section, up = false)) },
          enabled = enabled && i < order.lastIndex,
        ) {
          Icon(Icons.Outlined.KeyboardArrowDown, stringResource(Res.string.pdf_section_down, name))
        }
      }
    }
  }
}

/** Filter chips for one choice; [onPick] gets the index. */
@Composable
private fun Chips(options: List<Pair<String, Boolean>>, enabled: Boolean, onPick: (Int) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    options.forEachIndexed { i, (label, selected) ->
      FilterChip(
        selected = selected,
        onClick = { onPick(i) },
        label = { Text(label) },
        enabled = enabled,
      )
    }
  }
}

@Composable
private fun Label(text: String) {
  Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
internal fun Muted(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
