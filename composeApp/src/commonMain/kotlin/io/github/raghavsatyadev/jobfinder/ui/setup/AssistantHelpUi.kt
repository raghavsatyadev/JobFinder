@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_apply
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_applying
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_boards_answer
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_boards_text
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_boards_title
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_copied
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_copy
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_failed
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_filters_answer
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_filters_saved
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_filters_text
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_filters_title
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_nothing
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_open
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_resume_text
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_resume_title
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_searches_added
import io.github.raghavsatyadev.jobfinder.resources.setup_helper_searches_failed
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupPage
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The AI-assistant cards for a setup page: job boards on Searches; filters and resume on AI. */
@Composable
fun AssistantHelpFor(page: SetupPage, viewModel: AssistantHelpViewModel = koinViewModel()) {
  val tasks = page.assistantTasks
  if (tasks.isEmpty()) return
  val state by viewModel.state.collectAsStateWithLifecycle()
  tasks.forEach { task ->
    AssistantHelpCard(task, state, viewModel::answerChanged, viewModel::apply)
  }
}

/** The tasks an assistant can help with on [this] page. */
val SetupPage.assistantTasks: List<AssistantTask>
  get() =
    when (this) {
      SetupPage.AI -> listOf(AssistantTask.FILTERS, AssistantTask.RESUME)
      SetupPage.SEARCHES -> listOf(AssistantTask.JOB_BOARDS)
      SetupPage.PHONE -> emptyList()
    }

/**
 * One way an outside assistant can help: copy the request, open Claude, Gemini or ChatGPT, attach
 * the resume there, and paste the answer back here (job boards and filters).
 */
@Composable
fun AssistantHelpCard(
  task: AssistantTask,
  state: AssistantHelpState,
  onAnswer: (AssistantTask, String) -> Unit,
  onApply: (AssistantTask) -> Unit,
  modifier: Modifier = Modifier,
) {
  @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
  val uriHandler = LocalUriHandler.current
  var copied by remember(task) { mutableStateOf(false) }
  val request = state.requests[task].orEmpty()
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        stringResource(task.title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        stringResource(task.text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = {
            clipboard.setText(AnnotatedString(request))
            copied = true
          },
          enabled = request.isNotEmpty(),
          shapes = ButtonDefaults.shapes(),
        ) {
          Icon(Icons.Rounded.ContentCopy, null, Modifier.padding(end = 8.dp))
          Text(
            stringResource(
              if (copied) Res.string.setup_helper_copied else Res.string.setup_helper_copy
            )
          )
        }
        Assistant.entries.forEach { assistant ->
          TextButton(onClick = { uriHandler.openUri(assistant.url) }) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.padding(end = 8.dp))
            Text(stringResource(Res.string.setup_helper_open, assistant.label))
          }
        }
      }
      task.answerLabel?.let { label ->
        val applying = state.applying == task
        OutlinedTextField(
          value = state.answers[task].orEmpty(),
          onValueChange = { onAnswer(task, it) },
          label = { Text(stringResource(label)) },
          minLines = 3,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
          onClick = { onApply(task) },
          enabled = !applying && !state.answers[task].isNullOrBlank(),
          shapes = ButtonDefaults.shapes(),
        ) {
          Text(
            stringResource(
              if (applying) Res.string.setup_helper_applying else Res.string.setup_helper_apply
            )
          )
        }
      }
      state.results[task]?.let { ResultText(it) }
    }
  }
}

@Composable
private fun ResultText(result: AssistantResult) {
  val failed =
    result is AssistantResult.Failed ||
      result is AssistantResult.NothingFound ||
      (result is AssistantResult.SearchesAdded && result.failed > 0)
  Text(
    when (result) {
      is AssistantResult.SearchesAdded ->
        if (result.failed == 0) {
          pluralStringResource(Res.plurals.setup_helper_searches_added, result.added, result.added)
        } else {
          stringResource(Res.string.setup_helper_searches_failed, result.added, result.failed)
        }
      is AssistantResult.FiltersSaved ->
        stringResource(Res.string.setup_helper_filters_saved, result.companies)
      AssistantResult.NothingFound -> stringResource(Res.string.setup_helper_nothing)
      AssistantResult.Failed -> stringResource(Res.string.setup_helper_failed)
    },
    style = MaterialTheme.typography.bodyMedium,
    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
  )
}

private val AssistantTask.title: StringResource
  get() =
    when (this) {
      AssistantTask.JOB_BOARDS -> Res.string.setup_helper_boards_title
      AssistantTask.FILTERS -> Res.string.setup_helper_filters_title
      AssistantTask.RESUME -> Res.string.setup_helper_resume_title
    }

private val AssistantTask.text: StringResource
  get() =
    when (this) {
      AssistantTask.JOB_BOARDS -> Res.string.setup_helper_boards_text
      AssistantTask.FILTERS -> Res.string.setup_helper_filters_text
      AssistantTask.RESUME -> Res.string.setup_helper_resume_text
    }

/** The label of the paste field; `null` when the answer is not pasted back here. */
private val AssistantTask.answerLabel: StringResource?
  get() =
    when (this) {
      AssistantTask.JOB_BOARDS -> Res.string.setup_helper_boards_answer
      AssistantTask.FILTERS -> Res.string.setup_helper_filters_answer
      AssistantTask.RESUME -> null
    }
