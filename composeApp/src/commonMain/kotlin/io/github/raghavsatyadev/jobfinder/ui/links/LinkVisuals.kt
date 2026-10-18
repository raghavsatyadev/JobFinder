package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.allDrawableResources
import io.github.raghavsatyadev.jobfinder.resources.links_error_url_invalid
import io.github.raghavsatyadev.jobfinder.resources.links_error_url_other_host
import io.github.raghavsatyadev.jobfinder.resources.links_time_days_ago
import io.github.raghavsatyadev.jobfinder.resources.links_time_hours_ago
import io.github.raghavsatyadev.jobfinder.resources.links_time_just_now
import io.github.raghavsatyadev.jobfinder.resources.links_time_minutes_ago
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** "5 minutes ago" for [then], against [now] (both epoch milliseconds). */
@Composable
fun relativeTime(then: Long, now: Long): String {
  val elapsed = (now - then).coerceAtLeast(0)
  return when {
    elapsed < MINUTE -> stringResource(Res.string.links_time_just_now)
    elapsed < HOUR -> {
      val minutes = (elapsed / MINUTE).toInt()
      pluralStringResource(Res.plurals.links_time_minutes_ago, minutes, minutes)
    }
    elapsed < DAY -> {
      val hours = (elapsed / HOUR).toInt()
      pluralStringResource(Res.plurals.links_time_hours_ago, hours, hours)
    }
    else -> {
      val days = (elapsed / DAY).toInt()
      pluralStringResource(Res.plurals.links_time_days_ago, days, days)
    }
  }
}

/**
 * The logo of [site] on a round light plate (logos are drawn for a light background), or a globe
 * when the board has none. Decorative: the board's name is always shown next to it.
 */
@Composable
fun SiteLogo(site: Site?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
  val logo = site?.let { Res.allDrawableResources["site_${it.logoKey}"] }
  Surface(
    shape = CircleShape,
    color =
      if (logo != null) MaterialTheme.colorScheme.surfaceBright
      else MaterialTheme.colorScheme.secondaryContainer,
    modifier = modifier.size(size),
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (logo != null) {
        Image(
          painterResource(logo),
          contentDescription = null,
          contentScale = ContentScale.Fit,
          modifier = Modifier.padding(size / 8).clip(CircleShape),
        )
      } else {
        Icon(
          Icons.Rounded.Language,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(size / 2),
        )
      }
    }
  }
}

/** Why [error] rejected a search URL. */
@Composable
fun urlErrorText(error: UrlError): String =
  when (error) {
    UrlError.Invalid -> stringResource(Res.string.links_error_url_invalid)
    is UrlError.OtherHost -> stringResource(Res.string.links_error_url_other_host, error.host)
  }
