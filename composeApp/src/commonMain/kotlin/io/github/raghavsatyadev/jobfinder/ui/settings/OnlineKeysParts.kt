@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.agy_chip
import io.github.raghavsatyadev.jobfinder.resources.ic_add_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_close_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_keyboard_arrow_down_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_keyboard_arrow_up_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_open_in_new_filled
import io.github.raghavsatyadev.jobfinder.resources.ic_schedule_filled
import io.github.raghavsatyadev.jobfinder.resources.keys_added
import io.github.raghavsatyadev.jobfinder.resources.keys_expired
import io.github.raghavsatyadev.jobfinder.resources.keys_expires_in
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_days
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_days_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_expiry_none
import io.github.raghavsatyadev.jobfinder.resources.keys_failed
import io.github.raghavsatyadev.jobfinder.resources.keys_get
import io.github.raghavsatyadev.jobfinder.resources.keys_keeps_prompts
import io.github.raghavsatyadev.jobfinder.resources.keys_local
import io.github.raghavsatyadev.jobfinder.resources.keys_no_limit
import io.github.raghavsatyadev.jobfinder.resources.keys_per_day
import io.github.raghavsatyadev.jobfinder.resources.keys_per_minute
import io.github.raghavsatyadev.jobfinder.resources.keys_refused
import io.github.raghavsatyadev.jobfinder.resources.keys_reset
import io.github.raghavsatyadev.jobfinder.resources.keys_resume_warning
import io.github.raghavsatyadev.jobfinder.resources.keys_route_down
import io.github.raghavsatyadev.jobfinder.resources.keys_route_remove
import io.github.raghavsatyadev.jobfinder.resources.keys_route_up
import io.github.raghavsatyadev.jobfinder.resources.keys_routing_hint
import io.github.raghavsatyadev.jobfinder.resources.keys_routing_title
import io.github.raghavsatyadev.jobfinder.resources.keys_tokens_per_day
import io.github.raghavsatyadev.jobfinder.resources.keys_unknown
import io.github.raghavsatyadev.jobfinder.resources.opencode_chip
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.shared.model.FreeTier
import io.github.raghavsatyadev.jobfinder.ui.platform.AiKeysState
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun NumberField(
  value: String,
  label: StringResource,
  modifier: Modifier,
  onChange: (String) -> Unit,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(stringResource(label)) },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

/** At most this many of the site's models show under the model field. */
internal const val MODEL_CHOICES = 12

/** Days left on the key: plain, then a warning in the last two weeks, then expired. */
@Composable
internal fun ExpiryLine(key: AiKey, now: Long) {
  val left = key.daysLeft(now)
  val warn = left != null && left <= AiKey.EXPIRY_WARNING_DAYS
  val text =
    when {
      left == null -> stringResource(Res.string.keys_expiry_none)
      left <= 0 -> stringResource(Res.string.keys_expired)
      else -> pluralStringResource(Res.plurals.keys_expires_in, left.toInt(), left.toInt())
    }
  val color =
    if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (warn) {
      Icon(
        vectorResource(Res.drawable.ic_schedule_filled),
        contentDescription = null,
        tint = color,
        modifier = Modifier.padding(end = 4.dp).size(16.dp),
      )
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
  }
}

/** Days until a key expires; blank means it never does. */
@Composable
internal fun DaysField(
  days: String,
  enabled: Boolean,
  onChange: (String) -> Unit,
  modifier: Modifier = Modifier.fillMaxWidth(),
) {
  OutlinedTextField(
    value = days,
    onValueChange = onChange,
    label = { Text(stringResource(Res.string.keys_expiry_days)) },
    supportingText = { Text(stringResource(Res.string.keys_expiry_days_hint)) },
    singleLine = true,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

@Composable
internal fun KeyNoticeText(notice: KeyNotice) {
  val good = notice is KeyNotice.Added
  val text =
    when (notice) {
      is KeyNotice.Added -> {
        val suits = FreeAiCatalog.of(notice.key.provider)?.suits.orEmpty()
        val names = suits.map { task -> stringResource(TASKS.first { it.first == task }.second) }
        stringResource(
          Res.string.keys_added,
          stringResource(providerName(notice.key.provider)),
          names.joinToString(", "),
        )
      }
      KeyNotice.Unknown -> stringResource(Res.string.keys_unknown)
      KeyNotice.Refused -> stringResource(Res.string.keys_refused)
      is KeyNotice.Failed -> stringResource(Res.string.keys_failed, notice.reason)
    }
  Text(
    text,
    style = MaterialTheme.typography.bodyMedium,
    color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
  )
}

/** One free tier: its limits, its terms and where to get a key. */
@Composable
internal fun TierRow(tier: FreeTier) {
  val uriHandler = LocalUriHandler.current
  val limits = buildList {
    tier.requestsPerMinute?.let { add(stringResource(Res.string.keys_per_minute, it)) }
    tier.requestsPerDay?.let { add(stringResource(Res.string.keys_per_day, it)) }
    tier.tokensPerDay?.let { add(stringResource(Res.string.keys_tokens_per_day, shortCount(it))) }
    if (tier.requestsPerDay == null && tier.tokensPerDay == null) {
      add(stringResource(Res.string.keys_no_limit))
    }
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(stringResource(providerName(tier.provider)), style = MaterialTheme.typography.bodyLarge)
      Text(
        limits.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (tier.keepsPrompts) {
        Text(
          stringResource(Res.string.keys_keeps_prompts),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
    TextButton(
      onClick = { runCatching { uriHandler.openUri(tier.keyPage) } },
      shapes = ButtonDefaults.shapes(),
    ) {
      Icon(
        vectorResource(Res.drawable.ic_open_in_new_filled),
        contentDescription = null,
        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing),
      )
      Text(stringResource(Res.string.keys_get))
    }
  }
}

@Composable
internal fun Routing(pool: AiKeysState, onEvent: (OnlineKeysUiEvent) -> Unit) {
  Text(stringResource(Res.string.keys_routing_title), style = MaterialTheme.typography.titleSmall)
  Text(
    stringResource(Res.string.keys_routing_hint),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  val all = candidates(pool.keys, pool.routing)
  TASKS.forEach { (task, label) ->
    val chosen = pool.routing.forTask(task, pool.keys)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
      chosen.forEachIndexed { index, route ->
        RouteRow(task, route, index, chosen.size, pool, onEvent)
      }
      val others = all.filter { c -> chosen.none { it.keyId == c.keyId } }
      if (others.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          others.forEach { route ->
            FilterChip(
              selected = false,
              onClick = { onEvent(OnlineKeysUiEvent.Toggle(task, route)) },
              leadingIcon = {
                Icon(
                  vectorResource(Res.drawable.ic_add_filled),
                  contentDescription = null,
                  Modifier.size(18.dp),
                )
              },
              label = { Text(routeName(route, pool)) },
            )
          }
        }
      }
      if (task.usesResume && chosen.any { !it.local }) {
        Text(
          stringResource(Res.string.keys_resume_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      }
    }
  }
  TextButton(
    onClick = { onEvent(OnlineKeysUiEvent.ResetRouting) },
    shapes = ButtonDefaults.shapes(),
  ) {
    Text(stringResource(Res.string.keys_reset))
  }
}

/** One model in a task's order: its place, name and model, moved up or down or taken out. */
@Composable
internal fun RouteRow(
  task: AiTask,
  route: AiRoute,
  index: Int,
  count: Int,
  pool: AiKeysState,
  onEvent: (OnlineKeysUiEvent) -> Unit,
) {
  val name = routeName(route, pool)
  val model =
    when {
      route.openCode -> route.model.ifBlank { pool.routing.openCode.model }
      route.agy -> route.model.ifBlank { pool.routing.agy.model }
      route.local -> null
      else -> route.model.ifBlank { pool.keys.firstOrNull { it.id == route.keyId }?.defaultModel }
    }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      "${index + 1}.",
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(end = 8.dp),
    )
    Column(Modifier.weight(1f)) {
      Text(name, style = MaterialTheme.typography.bodyMedium)
      if (!model.isNullOrBlank()) {
        Text(
          model,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Move(task, route, -1)) },
      enabled = index > 0,
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(
        vectorResource(Res.drawable.ic_keyboard_arrow_up_filled),
        stringResource(Res.string.keys_route_up, name),
      )
    }
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Move(task, route, 1)) },
      enabled = index < count - 1,
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(
        vectorResource(Res.drawable.ic_keyboard_arrow_down_filled),
        stringResource(Res.string.keys_route_down, name),
      )
    }
    // A task always keeps one model.
    IconButton(
      onClick = { onEvent(OnlineKeysUiEvent.Toggle(task, route)) },
      enabled = count > 1,
      shapes = IconButtonDefaults.shapes(),
    ) {
      Icon(
        vectorResource(Res.drawable.ic_close_filled),
        stringResource(Res.string.keys_route_remove, name),
      )
    }
  }
}

@Composable
internal fun routeName(route: AiRoute, pool: AiKeysState): String {
  val key = pool.keys.firstOrNull { it.id == route.keyId }
  return when {
    route.openCode -> stringResource(Res.string.opencode_chip)
    route.agy -> stringResource(Res.string.agy_chip)
    key == null -> stringResource(Res.string.keys_local)
    else -> key.label ?: stringResource(providerName(key.provider))
  }
}

/** 1500 → "1.5K", 1000000 → "1M". */
internal fun shortCount(value: Long): String =
  when {
    value >= 1_000_000 -> trimmed(value / 1_000_000.0) + "M"
    value >= 1_000 -> trimmed(value / 1_000.0) + "K"
    else -> value.toString()
  }

internal fun trimmed(value: Double): String {
  val tenths = kotlin.math.round(value * 10).toLong()
  return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
