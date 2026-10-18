@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.common_cancel
import io.github.raghavsatyadev.jobfinder.resources.jobs_report
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_checking
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_close
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_failed
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_hint
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_move_anyway
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_note
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_right
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_send
import io.github.raghavsatyadev.jobfinder.resources.jobs_report_wrong
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.OfflineNote
import org.jetbrains.compose.resources.stringResource

/** "Shouldn't be in New", under the actions of a job in New. */
@Composable
fun ReportButton(onClick: () -> Unit) {
  TextButton(
    onClick = onClick,
    enabled = !LocalComputerOffline.current,
    shapes = ButtonDefaults.shapes(),
  ) {
    Icon(Icons.Outlined.Flag, null, Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
    Text(stringResource(Res.string.jobs_report))
  }
}

/** The note, then the AI's answer: right (the job moved to Filtered out) or wrong (why it fits). */
@Composable
fun ReportDialog(report: ReportUi, onEvent: (JobDetailEvent) -> Unit) {
  val result = report.result
  val offline = LocalComputerOffline.current
  AlertDialog(
    onDismissRequest = { if (!report.checking) onEvent(JobDetailEvent.CloseReport) },
    icon = { Icon(Icons.Outlined.Flag, null) },
    title = { Text(stringResource(Res.string.jobs_report)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
          result != null -> {
            Text(
              stringResource(
                if (result.userRight) Res.string.jobs_report_right else Res.string.jobs_report_wrong
              ),
              style = MaterialTheme.typography.titleSmall,
            )
            result.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
          }
          report.checking ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              LoadingIndicator(Modifier.size(40.dp))
              Spacer(Modifier.width(12.dp))
              Text(
                stringResource(Res.string.jobs_report_checking),
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          else -> {
            Text(
              stringResource(Res.string.jobs_report_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
              value = report.note,
              onValueChange = { onEvent(JobDetailEvent.SetReportNote(it)) },
              label = { Text(stringResource(Res.string.jobs_report_note)) },
              minLines = 2,
              maxLines = 5,
              modifier = Modifier.fillMaxWidth(),
            )
            if (report.failed) {
              Text(
                stringResource(Res.string.jobs_report_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
              )
            }
            if (offline) OfflineNote()
          }
        }
      }
    },
    confirmButton = {
      when {
        result != null && !result.userRight ->
          TextButton(onClick = { onEvent(JobDetailEvent.MoveAnyway) }) {
            Text(stringResource(Res.string.jobs_report_move_anyway))
          }
        result != null ->
          TextButton(onClick = { onEvent(JobDetailEvent.CloseReport) }) {
            Text(stringResource(Res.string.jobs_report_close))
          }
        else ->
          TextButton(
            onClick = { onEvent(JobDetailEvent.SendReport) },
            enabled = !report.checking && !offline,
          ) {
            Text(stringResource(Res.string.jobs_report_send))
          }
      }
    },
    dismissButton = {
      if (result == null || !result.userRight) {
        TextButton(
          onClick = { onEvent(JobDetailEvent.CloseReport) },
          enabled = !report.checking,
        ) {
          Text(
            stringResource(
              if (result != null) Res.string.jobs_report_close else Res.string.common_cancel
            )
          )
        }
      }
    },
  )
}
