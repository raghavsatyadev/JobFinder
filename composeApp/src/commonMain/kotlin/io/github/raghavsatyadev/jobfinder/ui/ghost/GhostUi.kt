@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.ghost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ghost_careers_found
import io.github.raghavsatyadev.jobfinder.resources.ghost_careers_found_as
import io.github.raghavsatyadev.jobfinder.resources.ghost_careers_not_found
import io.github.raghavsatyadev.jobfinder.resources.ghost_careers_unknown
import io.github.raghavsatyadev.jobfinder.resources.ghost_company_never
import io.github.raghavsatyadev.jobfinder.resources.ghost_failed
import io.github.raghavsatyadev.jobfinder.resources.ghost_load_failed
import io.github.raghavsatyadev.jobfinder.resources.ghost_looks_real
import io.github.raghavsatyadev.jobfinder.resources.ghost_no_salary
import io.github.raghavsatyadev.jobfinder.resources.ghost_none
import io.github.raghavsatyadev.jobfinder.resources.ghost_not_on_site
import io.github.raghavsatyadev.jobfinder.resources.ghost_old
import io.github.raghavsatyadev.jobfinder.resources.ghost_open_board
import io.github.raghavsatyadev.jobfinder.resources.ghost_open_job
import io.github.raghavsatyadev.jobfinder.resources.ghost_possible
import io.github.raghavsatyadev.jobfinder.resources.ghost_queued
import io.github.raghavsatyadev.jobfinder.resources.ghost_reposted
import io.github.raghavsatyadev.jobfinder.resources.ghost_retry
import io.github.raghavsatyadev.jobfinder.resources.ghost_some_signs
import io.github.raghavsatyadev.jobfinder.resources.ghost_unavailable
import io.github.raghavsatyadev.jobfinder.resources.ghost_vague
import io.github.raghavsatyadev.jobfinder.resources.offline_later
import io.github.raghavsatyadev.jobfinder.shared.model.CareersCheck
import io.github.raghavsatyadev.jobfinder.shared.model.CareersResult
import io.github.raghavsatyadev.jobfinder.shared.model.GhostCheck
import io.github.raghavsatyadev.jobfinder.shared.model.GhostSignal
import io.github.raghavsatyadev.jobfinder.shared.model.GhostStatus
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import org.jetbrains.compose.resources.stringResource

/**
 * The job detail's ghost-job check: a verdict, its reasons, and whether the company's own careers
 * site lists the job.
 */
@Composable
fun GhostSection(status: GhostStatus?, loadFailed: Boolean, now: Long, onRetry: () -> Unit) {
  val check = status?.check
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    when {
      loadFailed && LocalComputerOffline.current -> Muted(stringResource(Res.string.offline_later))
      loadFailed -> Retry(stringResource(Res.string.ghost_load_failed), onRetry)
      status == null -> WavyProgress()
      else -> {
        check?.let {
          Verdict(status, it)
          Signals(it, now)
        }
        when (status.state) {
          InsightsState.QUEUED -> {
            Muted(stringResource(Res.string.ghost_queued))
            WavyProgress()
          }
          InsightsState.FAILED -> Retry(stringResource(Res.string.ghost_failed), onRetry)
          InsightsState.UNAVAILABLE ->
            if (check?.careers == null) Muted(stringResource(Res.string.ghost_unavailable))
          InsightsState.READY -> Unit
        }
        check?.careers?.let { Careers(it) }
      }
    }
  }
}

@Composable
private fun Verdict(status: GhostStatus, check: GhostCheck) {
  when {
    check.possibleGhost ->
      StatusPill(stringResource(Res.string.ghost_possible), StatusTone.Negative)
    check.signals.isNotEmpty() ->
      StatusPill(stringResource(Res.string.ghost_some_signs), StatusTone.Neutral)
    check.careers?.result == CareersResult.FOUND || status.state == InsightsState.READY ->
      StatusPill(stringResource(Res.string.ghost_looks_real), StatusTone.Positive)
  }
}

@Composable
private fun Signals(check: GhostCheck, now: Long) {
  if (check.signals.isEmpty()) {
    Muted(stringResource(Res.string.ghost_none))
    return
  }
  // The careers line below says it, and a repost already gives the days.
  val reposted = check.signals.any { it is GhostSignal.Reposted }
  val shown =
    check.signals.filterNot {
      it is GhostSignal.NotOnCareersSite || (reposted && it is GhostSignal.OldPosting)
    }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    shown.forEach { signal ->
      val text =
        when (signal) {
          is GhostSignal.Reposted ->
            stringResource(
              Res.string.ghost_reposted,
              signal.times,
              ((now - signal.firstSeen) / DAY_MS).toInt(),
            )
          is GhostSignal.OldPosting -> stringResource(Res.string.ghost_old, signal.days)
          GhostSignal.NoSalary -> stringResource(Res.string.ghost_no_salary)
          GhostSignal.VagueText -> stringResource(Res.string.ghost_vague)
          GhostSignal.NotOnCareersSite -> stringResource(Res.string.ghost_not_on_site)
          is GhostSignal.CompanyNeverOnOwnSite ->
            stringResource(Res.string.ghost_company_never, signal.jobs)
        }
      Line(Icons.Rounded.WarningAmber, MaterialTheme.colorScheme.error, text)
    }
  }
}

@Composable
private fun Careers(careers: CareersCheck) {
  val host = careers.host?.label.orEmpty()
  when (careers.result) {
    CareersResult.FOUND ->
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Line(
            Icons.Rounded.CheckCircle,
            MaterialTheme.colorScheme.primary,
            stringResource(Res.string.ghost_careers_found, host),
            Modifier.weight(1f),
          )
          (careers.jobUrl ?: careers.boardUrl)?.let {
            Open(it, stringResource(Res.string.ghost_open_job))
          }
        }
        careers.matchedTitle?.let { Muted(stringResource(Res.string.ghost_careers_found_as, it)) }
      }
    CareersResult.NOT_FOUND ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Line(
          Icons.Rounded.WarningAmber,
          MaterialTheme.colorScheme.error,
          stringResource(Res.string.ghost_careers_not_found, careers.openJobs ?: 0, host),
          Modifier.weight(1f),
        )
        careers.boardUrl?.let { Open(it, stringResource(Res.string.ghost_open_board)) }
      }
    CareersResult.UNKNOWN ->
      Line(
        Icons.AutoMirrored.Outlined.HelpOutline,
        MaterialTheme.colorScheme.onSurfaceVariant,
        stringResource(Res.string.ghost_careers_unknown),
      )
  }
}

@Composable
private fun Line(icon: ImageVector, tint: Color, text: String, modifier: Modifier = Modifier) {
  Row(
    modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun Open(url: String, label: String) {
  val uriHandler = LocalUriHandler.current
  IconButton(
    onClick = { runCatching { uriHandler.openUri(url) } },
    shapes = IconButtonDefaults.shapes(),
  ) {
    Icon(Icons.AutoMirrored.Outlined.OpenInNew, label)
  }
}

@Composable
private fun Muted(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun Retry(text: String, onRetry: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Muted(text)
    FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
      Text(stringResource(Res.string.ghost_retry))
    }
  }
}

private const val DAY_MS = 86_400_000L
