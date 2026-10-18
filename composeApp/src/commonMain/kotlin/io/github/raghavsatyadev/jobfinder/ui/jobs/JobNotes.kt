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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_delete
import io.github.raghavsatyadev.jobfinder.resources.common_save
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_add
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_delete
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_delete_text
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_delete_title
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_edit
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_files
import io.github.raghavsatyadev.jobfinder.resources.jobs_note_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_notes_empty
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/** The notes on a job: add one, edit or delete each. Attachments show by file name only. */
@Composable
internal fun JobNotesSection(
  notes: ImmutableList<Note>?,
  now: Long,
  onAdd: (String) -> Unit,
  onEdit: (Long, String) -> Unit,
  onDelete: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  var draft by rememberSaveable { mutableStateOf("") }
  var editing by rememberSaveable { mutableStateOf<Long?>(null) }
  var pendingDelete by remember { mutableStateOf<Long?>(null) }
  // Notes are kept on the computer: offline they show read-only.
  val offline = LocalComputerOffline.current
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (offline) OfflineNote()
    OutlinedTextField(
      value = draft,
      onValueChange = { draft = it },
      placeholder = { Text(stringResource(Res.string.jobs_note_hint)) },
      enabled = !offline,
      minLines = 2,
      shape = MaterialTheme.shapes.large,
      modifier = Modifier.fillMaxWidth(),
    )
    FilledTonalButton(
      onClick = {
        onAdd(draft)
        draft = ""
      },
      enabled = draft.isNotBlank() && !offline,
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.align(Alignment.End),
    ) {
      Text(stringResource(Res.string.jobs_note_add))
    }
    when {
      notes == null -> Unit
      notes.isEmpty() ->
        Text(
          stringResource(Res.string.jobs_notes_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      else ->
        notes.forEach { note ->
          NoteCard(
            note = note,
            now = now,
            editing = editing == note.id,
            onStartEdit = { editing = note.id },
            onSave = {
              onEdit(note.id, it)
              editing = null
            },
            onCancel = { editing = null },
            onDelete = { pendingDelete = note.id },
          )
        }
    }
  }
  pendingDelete?.let { id ->
    ConfirmDialog(
      icon = Icons.Outlined.DeleteOutline,
      title = stringResource(Res.string.jobs_note_delete_title),
      text = stringResource(Res.string.jobs_note_delete_text),
      confirmLabel = stringResource(Res.string.common_delete),
      onConfirm = {
        pendingDelete = null
        onDelete(id)
      },
      onDismiss = { pendingDelete = null },
    )
  }
}

@Composable
private fun NoteCard(
  note: Note,
  now: Long,
  editing: Boolean,
  onStartEdit: () -> Unit,
  onSave: (String) -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)) {
      if (editing) {
        var text by rememberSaveable(note.id) { mutableStateOf(note.text) }
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          minLines = 2,
          modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        )
        Row(Modifier.align(Alignment.End)) {
          TextButton(onClick = onCancel) { Text(stringResource(Res.string.common_cancel)) }
          TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
            Text(stringResource(Res.string.common_save))
          }
        }
      } else {
        Text(
          note.text,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(end = 12.dp),
        )
        if (note.files.isNotEmpty()) {
          Spacer(Modifier.size(8.dp))
          Text(
            stringResource(Res.string.jobs_note_files),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            note.files.forEach { file ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                  file.substringAfterLast('/').substringAfterLast('\\'),
                  style = MaterialTheme.typography.bodySmall,
                )
              }
            }
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            relativeTime(note.createdAt, now),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
          val online = !LocalComputerOffline.current
          IconButton(
            onClick = onStartEdit,
            enabled = online,
            shapes = IconButtonDefaults.shapes(),
          ) {
            Icon(Icons.Outlined.Edit, stringResource(Res.string.jobs_note_edit))
          }
          IconButton(onClick = onDelete, enabled = online, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Outlined.DeleteOutline, stringResource(Res.string.jobs_note_delete))
          }
        }
      }
    }
  }
}
