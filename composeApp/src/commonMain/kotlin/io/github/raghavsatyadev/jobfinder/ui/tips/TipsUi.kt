@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.offline_later
import io.github.raghavsatyadev.jobfinder.resources.tips_failed
import io.github.raghavsatyadev.jobfinder.resources.tips_intro
import io.github.raghavsatyadev.jobfinder.resources.tips_leverage_high
import io.github.raghavsatyadev.jobfinder.resources.tips_leverage_low
import io.github.raghavsatyadev.jobfinder.resources.tips_leverage_medium
import io.github.raghavsatyadev.jobfinder.resources.tips_load_failed
import io.github.raghavsatyadev.jobfinder.resources.tips_queued
import io.github.raghavsatyadev.jobfinder.resources.tips_unavailable
import io.github.raghavsatyadev.jobfinder.resources.tips_write
import io.github.raghavsatyadev.jobfinder.resources.tips_write_again
import io.github.raghavsatyadev.jobfinder.shared.model.Leverage
import io.github.raghavsatyadev.jobfinder.shared.model.NegotiationTips
import io.github.raghavsatyadev.jobfinder.shared.model.TipsState
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import org.jetbrains.compose.resources.stringResource

/** The job detail's negotiation tips: written only when asked, then kept. */
@Composable
fun TipsSection(status: TipsStatus?, loadFailed: Boolean, onWrite: () -> Unit) {
  val offline = LocalComputerOffline.current
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    when {
      loadFailed && offline -> Muted(stringResource(Res.string.offline_later))
      loadFailed -> Muted(stringResource(Res.string.tips_load_failed))
      status == null -> WavyProgress()
      else -> {
        status.tips?.let { Tips(it) }
        when (status.state) {
          TipsState.NONE -> {
            Muted(stringResource(Res.string.tips_intro))
            if (!offline) WriteButton(stringResource(Res.string.tips_write), onWrite)
          }
          TipsState.QUEUED -> {
            Muted(stringResource(Res.string.tips_queued))
            WavyProgress()
          }
          TipsState.FAILED -> {
            Muted(stringResource(Res.string.tips_failed))
            if (!offline) WriteButton(stringResource(Res.string.tips_write), onWrite)
          }
          TipsState.READY ->
            if (!offline) {
              TextButton(onClick = onWrite, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.tips_write_again))
              }
            }
          TipsState.UNAVAILABLE -> Muted(stringResource(Res.string.tips_unavailable))
        }
      }
    }
  }
}

@Composable
private fun Tips(tips: NegotiationTips) {
  val (label, tone) =
    when (tips.leverage) {
      Leverage.LOW -> Res.string.tips_leverage_low to StatusTone.Negative
      Leverage.MEDIUM -> Res.string.tips_leverage_medium to StatusTone.Neutral
      Leverage.HIGH -> Res.string.tips_leverage_high to StatusTone.Positive
    }
  StatusPill(stringResource(label), tone)
  if (tips.summary.isNotBlank()) Text(tips.summary, style = MaterialTheme.typography.bodyLarge)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    tips.tips.forEachIndexed { i, tip ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "${i + 1}.",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.widthIn(min = 20.dp),
        )
        Text(tip, style = MaterialTheme.typography.bodyLarge)
      }
    }
  }
}

@Composable
private fun WriteButton(text: String, onClick: () -> Unit) {
  FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
    Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
    Text(text, Modifier.padding(start = 8.dp))
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
