@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_archive
import io.github.raghavsatyadev.jobfinder.resources.jobs_action_delete
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.ui.ats.AtsPill
import io.github.raghavsatyadev.jobfinder.ui.components.CardCorner
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.jobAtsKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobCompanyKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobContainerKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobLogoKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobMetaKey
import io.github.raghavsatyadev.jobfinder.ui.components.jobTitleKey
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContainer
import io.github.raghavsatyadev.jobfinder.ui.components.sharedContent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Test tag on every job card; Android exposes it as the resource id for Maestro. */
const val JOB_CARD_TAG = "job_card"

/**
 * One job in the feed: logo, title, company, where and how much, labels and when it was found. The
 * card grows into the job detail; its title, company, logo and meta line fly on their own.
 */
@Composable
internal fun JobCard(
  job: Job,
  siteName: String?,
  selected: Boolean,
  now: Long,
  onOpen: () -> Unit,
  onArchive: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = MaterialTheme.colorScheme
  val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
  val container by
    animateColorAsState(
      if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
      effects,
      label = "card container",
    )
  Surface(
    onClick = onOpen,
    shape = RoundedCornerShape(CardCorner),
    color = container,
    border = if (selected) BorderStroke(2.dp, colors.primary) else null,
    modifier =
      modifier
        .sharedContainer(jobContainerKey(job.id))
        .fillMaxWidth()
        .testTag(JOB_CARD_TAG)
        .semantics { this.selected = selected },
  ) {
    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        CompanyLogo(
          job,
          size = CardLogo,
          modifier = Modifier.sharedContent(jobLogoKey(job.id), DetailLogo / CardLogo),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            job.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedContent(jobTitleKey(job.id), titleRatio(detail = true)),
          )
          Text(
            job.companyName,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedContent(jobCompanyKey(job.id), companyRatio(detail = true)),
          )
        }
      }
      Spacer(Modifier.size(8.dp))
      JobMeta(job, Modifier.padding(end = 8.dp).sharedContent(jobMetaKey(job.id)))
      val ats = job.ats
      if (job.labels.isNotEmpty() || ats != null) {
        Spacer(Modifier.size(8.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          job.labels.forEach { StatusPill(stringResource(it.label), it.tone) }
          if (ats != null) AtsPill(ats, Modifier.sharedContent(jobAtsKey(job.id)))
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          listOfNotNull(siteName, relativeTime(job.updatedAt, now)).joinToString(" · "),
          style = MaterialTheme.typography.labelMedium,
          color = colors.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        if (job.status != JobStatus.ARCHIVED) {
          IconButton(onClick = onArchive, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Outlined.Archive, stringResource(Res.string.jobs_action_archive))
          }
        }
        IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
          Icon(Icons.Outlined.DeleteOutline, stringResource(Res.string.jobs_action_delete))
        }
      }
    }
  }
}

/** Location, salary and work mode, wrapping a whole item at a time. */
@Composable
internal fun JobMeta(job: Job, modifier: Modifier = Modifier) {
  val items = buildList {
    job.location?.takeIf { it.isNotBlank() }?.let { add(Icons.Outlined.Place to it) }
    job.salary?.takeIf { it.isNotBlank() }?.let { add(Icons.Outlined.Payments to it) }
  }
  val workMode = job.workMode?.let { stringResource(it.label) }
  if (items.isEmpty() && workMode == null) return
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items.forEach { (icon, text) -> MetaItem(icon, text) }
    workMode?.let { MetaItem(Icons.Outlined.Work, it) }
  }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      icon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(4.dp))
    Text(text, style = MaterialTheme.typography.bodySmall)
  }
}

/**
 * The company logo when the board gave one, else the company's initial on a cookie shape.
 * Decorative: the company name is always next to it.
 */
@Composable
internal fun CompanyLogo(job: Job, size: Dp, modifier: Modifier = Modifier) {
  val shape = MaterialShapes.Cookie9Sided.toShape()
  Box(modifier.size(size).clip(shape), contentAlignment = Alignment.Center) {
    val logo = job.companyLogo
    if (logo.isNullOrBlank()) {
      Monogram(job.companyName)
    } else {
      SubcomposeAsyncImage(
        model = logo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        loading = { Monogram(job.companyName) },
        error = { Monogram(job.companyName) },
        modifier = Modifier.size(size),
      )
    }
  }
}

@Composable
private fun Monogram(name: String) {
  Box(
    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.tertiaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      name.trim().firstOrNull()?.uppercase() ?: "?",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onTertiaryContainer,
    )
  }
}

/** A small board logo next to the board name, when the board has one bundled. */
@Composable
internal fun SiteLogo(logoKey: String?, modifier: Modifier = Modifier) {
  val resource = siteLogo(logoKey) ?: return
  Image(
    painterResource(resource),
    contentDescription = null,
    modifier = modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
  )
}

/**
 * The logo's size on the card and in the detail; a logo flying between them scales by their ratio.
 */
internal val CardLogo = 48.dp
internal val DetailLogo = 64.dp

/** The title on the card (titleMedium) over the detail's (headlineSmall), or the inverse. */
@Composable
@ReadOnlyComposable
internal fun titleRatio(detail: Boolean): Float {
  val card = MaterialTheme.typography.titleMedium.fontSize.value
  val big = MaterialTheme.typography.headlineSmall.fontSize.value
  return if (detail) big / card else card / big
}

/** The company name on the card (bodyMedium) over the detail's (titleMedium), or the inverse. */
@Composable
@ReadOnlyComposable
internal fun companyRatio(detail: Boolean): Float {
  val card = MaterialTheme.typography.bodyMedium.fontSize.value
  val big = MaterialTheme.typography.titleMedium.fontSize.value
  return if (detail) big / card else card / big
}
