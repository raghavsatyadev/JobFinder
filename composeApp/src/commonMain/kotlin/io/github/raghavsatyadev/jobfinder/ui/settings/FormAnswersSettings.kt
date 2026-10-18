@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.common_delete
import io.github.raghavsatyadev.jobfinder.resources.common_retry
import io.github.raghavsatyadev.jobfinder.resources.common_save
import io.github.raghavsatyadev.jobfinder.resources.form_answers_answer
import io.github.raghavsatyadev.jobfinder.resources.form_answers_change_failed
import io.github.raghavsatyadev.jobfinder.resources.form_answers_count
import io.github.raghavsatyadev.jobfinder.resources.form_answers_delete
import io.github.raghavsatyadev.jobfinder.resources.form_answers_delete_text
import io.github.raghavsatyadev.jobfinder.resources.form_answers_delete_title
import io.github.raghavsatyadev.jobfinder.resources.form_answers_edit
import io.github.raghavsatyadev.jobfinder.resources.form_answers_empty
import io.github.raghavsatyadev.jobfinder.resources.form_answers_intro
import io.github.raghavsatyadev.jobfinder.resources.form_answers_job
import io.github.raghavsatyadev.jobfinder.resources.form_answers_load_failed
import io.github.raghavsatyadev.jobfinder.resources.form_answers_more
import io.github.raghavsatyadev.jobfinder.resources.form_answers_none_found
import io.github.raghavsatyadev.jobfinder.resources.form_answers_refresh
import io.github.raghavsatyadev.jobfinder.resources.form_answers_search
import io.github.raghavsatyadev.jobfinder.shared.model.FormAnswer
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import io.github.raghavsatyadev.jobfinder.ui.components.ConfirmDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class FormAnswersUiState(
  /** `null` until loaded. */
  val answers: List<FormAnswer>? = null,
  val query: String = "",
  /** How many matching answers are listed; "Show more" adds a page. */
  val shown: Int = FORM_ANSWERS_PAGE,
  val loadFailed: Boolean = false,
  val changeFailed: Boolean = false,
  /** The answer in the edit dialog and its text there. */
  val editing: FormAnswer? = null,
  val draft: String = "",
  val confirmDelete: FormAnswer? = null,
)

sealed interface FormAnswersEvent {
  data object Load : FormAnswersEvent

  data class Query(val text: String) : FormAnswersEvent

  data object ShowMore : FormAnswersEvent

  data class Edit(val answer: FormAnswer) : FormAnswersEvent

  data class Draft(val text: String) : FormAnswersEvent

  data object SaveEdit : FormAnswersEvent

  data object CancelEdit : FormAnswersEvent

  data class AskDelete(val answer: FormAnswer) : FormAnswersEvent

  data object ConfirmDelete : FormAnswersEvent

  data object CancelDelete : FormAnswersEvent
}

/** Settings → Form answers: the answers the browser extension saved, to read, edit and delete. */
class FormAnswersViewModel(private val repository: FormAnswersRepository) : ViewModel() {
  private val _state = MutableStateFlow(FormAnswersUiState())
  val state: StateFlow<FormAnswersUiState> = _state.asStateFlow()

  fun onEvent(event: FormAnswersEvent) {
    when (event) {
      FormAnswersEvent.Load -> load()
      is FormAnswersEvent.Query ->
        _state.update { it.copy(query = event.text, shown = FORM_ANSWERS_PAGE) }
      FormAnswersEvent.ShowMore -> _state.update { it.copy(shown = it.shown + FORM_ANSWERS_PAGE) }
      is FormAnswersEvent.Edit ->
        _state.update { it.copy(editing = event.answer, draft = event.answer.value) }
      is FormAnswersEvent.Draft -> _state.update { it.copy(draft = event.text) }
      FormAnswersEvent.CancelEdit -> _state.update { it.copy(editing = null, draft = "") }
      FormAnswersEvent.SaveEdit -> saveEdit()
      is FormAnswersEvent.AskDelete -> _state.update { it.copy(confirmDelete = event.answer) }
      FormAnswersEvent.CancelDelete -> _state.update { it.copy(confirmDelete = null) }
      FormAnswersEvent.ConfirmDelete -> delete()
    }
  }

  private fun load() {
    _state.update { it.copy(loadFailed = false) }
    change(onFail = { it.copy(loadFailed = true) }) {
      val answers = repository.listAnswers()
      _state.update { it.copy(answers = answers) }
    }
  }

  private fun saveEdit() {
    val editing = _state.value.editing ?: return
    val text = _state.value.draft.trim()
    _state.update { it.copy(editing = null, draft = "") }
    if (text.isEmpty() || text == editing.value) return
    change {
      val saved = repository.updateAnswer(editing.id, text)
      _state.update { s ->
        s.copy(answers = s.answers?.map { if (it.id == saved.id) saved else it })
      }
    }
  }

  private fun delete() {
    val answer = _state.value.confirmDelete ?: return
    _state.update { it.copy(confirmDelete = null) }
    change {
      repository.deleteAnswer(answer.id)
      _state.update { s ->
        s.copy(answers = s.answers?.filterNot { it.id == answer.id })
      }
    }
  }

  private fun change(
    onFail: (FormAnswersUiState) -> FormAnswersUiState = { it.copy(changeFailed = true) },
    work: suspend () -> Unit,
  ) {
    _state.update { it.copy(changeFailed = false) }
    viewModelScope.launch {
      try {
        work()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update(onFail)
      }
    }
  }
}

@Composable
fun FormAnswersSettings() {
  val viewModel = koinViewModel<FormAnswersViewModel>()
  val state by viewModel.state.collectAsStateWithLifecycle()
  // Loads each time the section opens, so answers saved since show up.
  LaunchedEffect(viewModel) { viewModel.onEvent(FormAnswersEvent.Load) }
  FormAnswersContent(state, viewModel::onEvent)
}

@Composable
fun FormAnswersContent(state: FormAnswersUiState, onEvent: (FormAnswersEvent) -> Unit) {
  Column(Modifier.padding(vertical = 8.dp)) {
    Note(stringResource(Res.string.form_answers_intro))
    val answers = state.answers
    when {
      answers == null && state.loadFailed ->
        Row(
          Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Note(stringResource(Res.string.form_answers_load_failed), Modifier.weight(1f))
          TextButton(
            onClick = { onEvent(FormAnswersEvent.Load) },
            shapes = ButtonDefaults.shapes(),
          ) {
            Text(stringResource(Res.string.common_retry))
          }
        }
      answers == null -> Unit
      answers.isEmpty() -> Note(stringResource(Res.string.form_answers_empty))
      else -> AnswerList(state, answers, onEvent)
    }
    if (state.changeFailed) {
      Text(
        stringResource(Res.string.form_answers_change_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )
    }
  }
  state.editing?.let { EditDialog(it, state.draft, onEvent) }
  state.confirmDelete?.let { answer ->
    ConfirmDialog(
      icon = Icons.Rounded.Delete,
      title = stringResource(Res.string.form_answers_delete_title),
      text =
        stringResource(Res.string.form_answers_delete_text, answer.value.take(80), answer.question),
      confirmLabel = stringResource(Res.string.common_delete),
      onConfirm = { onEvent(FormAnswersEvent.ConfirmDelete) },
      onDismiss = { onEvent(FormAnswersEvent.CancelDelete) },
    )
  }
}

@Composable
private fun AnswerList(
  state: FormAnswersUiState,
  answers: List<FormAnswer>,
  onEvent: (FormAnswersEvent) -> Unit,
) {
  val query = state.query
  val matching =
    remember(answers, query) {
      val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
      if (words.isEmpty()) answers
      else
        answers.filter { a ->
          val haystack =
            listOfNotNull(a.question, a.value, a.site, a.jobTitle, a.companyName, a.questionKey)
              .joinToString(" ")
              .lowercase()
          words.all { it in haystack }
        }
    }
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    OutlinedTextField(
      value = query,
      onValueChange = { onEvent(FormAnswersEvent.Query(it)) },
      placeholder = { Text(stringResource(Res.string.form_answers_search)) },
      leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
      trailingIcon =
        if (query.isEmpty()) null
        else {
          {
            IconButton(onClick = { onEvent(FormAnswersEvent.Query("")) }) {
              Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(Res.string.common_cancel),
              )
            }
          }
        },
      singleLine = true,
      shape = MaterialTheme.shapes.extraLarge,
      modifier = Modifier.weight(1f),
    )
    IconButton(
      onClick = { onEvent(FormAnswersEvent.Load) },
      shapes = IconButtonDefaults.shapes(),
      modifier = Modifier.padding(start = 8.dp),
    ) {
      Icon(
        Icons.Rounded.Refresh,
        contentDescription = stringResource(Res.string.form_answers_refresh),
      )
    }
  }
  Note(stringResource(Res.string.form_answers_count, answers.size))
  if (matching.isEmpty()) {
    Note(stringResource(Res.string.form_answers_none_found, query.trim()))
    return
  }
  matching.take(state.shown).forEachIndexed { index, answer ->
    key(answer.id) {
      if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
      AnswerRow(answer, onEvent)
    }
  }
  val more = matching.size - state.shown
  if (more > 0) {
    TextButton(
      onClick = { onEvent(FormAnswersEvent.ShowMore) },
      shapes = ButtonDefaults.shapes(),
      modifier = Modifier.padding(horizontal = 8.dp),
    ) {
      Text(stringResource(Res.string.form_answers_more, minOf(more, FORM_ANSWERS_PAGE)))
    }
  }
}

@Composable
private fun AnswerRow(answer: FormAnswer, onEvent: (FormAnswersEvent) -> Unit) {
  Row(
    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        answer.question,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        answer.value,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      val title = answer.jobTitle
      val company = answer.companyName
      val job =
        if (title != null && company != null)
          stringResource(Res.string.form_answers_job, title, company)
        else null
      Text(
        listOfNotNull(answer.site, job).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(
      onClick = { onEvent(FormAnswersEvent.Edit(answer)) },
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(Icons.Rounded.Edit, contentDescription = stringResource(Res.string.form_answers_edit))
    }
    IconButton(
      onClick = { onEvent(FormAnswersEvent.AskDelete(answer)) },
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(
        Icons.Rounded.Delete,
        contentDescription = stringResource(Res.string.form_answers_delete),
        tint = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
private fun EditDialog(answer: FormAnswer, draft: String, onEvent: (FormAnswersEvent) -> Unit) {
  AlertDialog(
    onDismissRequest = { onEvent(FormAnswersEvent.CancelEdit) },
    icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
    title = { Text(answer.question, maxLines = 3, overflow = TextOverflow.Ellipsis) },
    text = {
      OutlinedTextField(
        value = draft,
        onValueChange = { onEvent(FormAnswersEvent.Draft(it)) },
        label = { Text(stringResource(Res.string.form_answers_answer)) },
        minLines = 2,
        maxLines = 8,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = {
      TextButton(
        onClick = { onEvent(FormAnswersEvent.SaveEdit) },
        enabled = draft.isNotBlank(),
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.common_save))
      }
    },
    dismissButton = {
      TextButton(
        onClick = { onEvent(FormAnswersEvent.CancelEdit) },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.common_cancel))
      }
    },
  )
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
  )
}

/** Answers listed at a time. */
const val FORM_ANSWERS_PAGE = 50
