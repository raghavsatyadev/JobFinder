package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ai_problem_expired
import io.github.raghavsatyadev.jobfinder.resources.ai_problem_failing
import io.github.raghavsatyadev.jobfinder.resources.ai_problem_refused
import io.github.raghavsatyadev.jobfinder.resources.ai_problems_dismiss
import io.github.raghavsatyadev.jobfinder.resources.ai_problems_hint
import io.github.raghavsatyadev.jobfinder.resources.ai_problems_mute
import io.github.raghavsatyadev.jobfinder.resources.ai_problems_open_settings
import io.github.raghavsatyadev.jobfinder.resources.ai_problems_title
import io.github.raghavsatyadev.jobfinder.shared.model.AiProblem
import io.github.raghavsatyadev.jobfinder.shared.model.AiProblemKind
import io.github.raghavsatyadev.jobfinder.ui.platform.AiProblems
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** What is wrong with a model, in one sentence. */
@Composable
internal fun aiProblemText(problem: AiProblem): String =
  when (problem.kind) {
    AiProblemKind.KEY_REFUSED -> stringResource(Res.string.ai_problem_refused, problem.name)
    AiProblemKind.KEY_EXPIRED -> stringResource(Res.string.ai_problem_expired, problem.name)
    AiProblemKind.FAILING ->
      stringResource(Res.string.ai_problem_failing, problem.name, problem.failures, problem.detail)
  }

/** A model's problem under its row in Settings. */
@Composable
internal fun AiProblemLine(problem: AiProblem) {
  Text(
    aiProblemText(problem),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.error,
  )
}

/**
 * The warning over the main window when a model has a new problem. Each problem shows once per run
 * of the app; "Mute for a day" hides all of them until the same time tomorrow.
 */
@Composable
fun AiProblemsWarning(source: AiProblems, onOpenSettings: () -> Unit) {
  val problems by source.problems.collectAsStateWithLifecycle()
  val mutedUntil by source.mutedUntil.collectAsStateWithLifecycle()
  var seen by remember { mutableStateOf(emptySet<String>()) }
  // Read the time again each minute, so the warning comes back when a mute ends.
  val now by
    produceState(now()) {
      while (true) {
        delay(MINUTE_MS)
        value = now()
      }
    }
  val fresh = problems.filter { id(it) !in seen }
  if (fresh.isEmpty() || now < mutedUntil) return
  fun close() {
    seen = seen + fresh.map(::id)
  }
  AlertDialog(
    onDismissRequest = ::close,
    title = { Text(stringResource(Res.string.ai_problems_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fresh.forEach { Text(aiProblemText(it), style = MaterialTheme.typography.bodyMedium) }
        Text(
          stringResource(Res.string.ai_problems_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          close()
          onOpenSettings()
        },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.ai_problems_open_settings))
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          close()
          source.muteForDay()
        },
        shapes = ButtonDefaults.shapes(),
      ) {
        Text(stringResource(Res.string.ai_problems_mute))
      }
      TextButton(onClick = ::close, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.ai_problems_dismiss))
      }
    },
  )
}

private fun id(problem: AiProblem) = "${problem.routeId}/${problem.kind}"

private fun now() = Clock.System.now().toEpochMilliseconds()

private const val MINUTE_MS = 60_000L
