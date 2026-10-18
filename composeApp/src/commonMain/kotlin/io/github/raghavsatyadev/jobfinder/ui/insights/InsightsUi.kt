@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.insights_dislikes
import io.github.raghavsatyadev.jobfinder.resources.insights_failed
import io.github.raghavsatyadev.jobfinder.resources.insights_likes
import io.github.raghavsatyadev.jobfinder.resources.insights_load_failed
import io.github.raghavsatyadev.jobfinder.resources.insights_no_salary
import io.github.raghavsatyadev.jobfinder.resources.insights_none
import io.github.raghavsatyadev.jobfinder.resources.insights_open_source
import io.github.raghavsatyadev.jobfinder.resources.insights_queued
import io.github.raghavsatyadev.jobfinder.resources.insights_rating
import io.github.raghavsatyadev.jobfinder.resources.insights_rating_description
import io.github.raghavsatyadev.jobfinder.resources.insights_recommend
import io.github.raghavsatyadev.jobfinder.resources.insights_retry
import io.github.raghavsatyadev.jobfinder.resources.insights_reviews
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_experience
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_median
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_nearest
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_range
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_role
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_samples
import io.github.raghavsatyadev.jobfinder.resources.insights_salary_title
import io.github.raghavsatyadev.jobfinder.resources.insights_source_ambitionbox
import io.github.raghavsatyadev.jobfinder.resources.insights_source_glassdoor
import io.github.raghavsatyadev.jobfinder.resources.insights_unavailable
import io.github.raghavsatyadev.jobfinder.resources.offline_later
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyInsights
import io.github.raghavsatyadev.jobfinder.shared.model.CompanyRating
import io.github.raghavsatyadev.jobfinder.shared.model.InsightSource
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsState
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.SalaryRange
import io.github.raghavsatyadev.jobfinder.ui.ats.oneDecimal
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The job detail's company section: ratings per site and the salary for the role. */
@Composable
fun InsightsSection(status: InsightsStatus?, loadFailed: Boolean, onRetry: () -> Unit) {
  val insights = status?.insights
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    when {
      loadFailed && LocalComputerOffline.current -> Notice(stringResource(Res.string.offline_later))
      loadFailed -> Notice(stringResource(Res.string.insights_load_failed), onRetry)
      status == null -> WavyProgress()
      status.state == InsightsState.QUEUED -> {
        Notice(stringResource(Res.string.insights_queued))
        WavyProgress()
      }
      status.state == InsightsState.FAILED ->
        Notice(stringResource(Res.string.insights_failed), onRetry)
      status.state == InsightsState.UNAVAILABLE && insights == null ->
        Notice(stringResource(Res.string.insights_unavailable))
    }
    // Whatever is cached shows, even while a fresher read is queued.
    if (!loadFailed && insights != null) Insights(insights)
  }
}

@Composable
private fun Insights(insights: CompanyInsights) {
  if (insights.ratings.isEmpty() && insights.salary == null) {
    Notice(stringResource(Res.string.insights_none, insights.company))
    return
  }
  insights.ratings.forEach { Rating(it) }
  Salary(insights.salary)
}

private val InsightSource.label: StringResource
  get() =
    when (this) {
      InsightSource.AMBITIONBOX -> Res.string.insights_source_ambitionbox
      InsightSource.GLASSDOOR -> Res.string.insights_source_glassdoor
    }

@Composable
private fun Rating(rating: CompanyRating) {
  val source = stringResource(rating.source.label)
  val value = oneDecimal(rating.rating)
  val description = stringResource(Res.string.insights_rating_description, source, value)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      FlowRow(
        Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          source,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.semantics { heading() },
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        ) {
          Icon(
            Icons.Rounded.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            stringResource(Res.string.insights_rating, value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
          )
        }
        rating.reviews?.let {
          Muted(stringResource(Res.string.insights_reviews, grouped(it.toLong())))
        }
        rating.recommendPercent?.let { Muted(stringResource(Res.string.insights_recommend, it)) }
      }
      OpenSource(rating.url, source)
    }
    if (rating.categories.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        rating.categories.forEach { category ->
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Text(
              "${category.name} ${oneDecimal(category.rating)}",
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    }
    Quotes(Res.string.insights_likes, rating.likes)
    Quotes(Res.string.insights_dislikes, rating.dislikes)
  }
}

@Composable
private fun Quotes(title: StringResource, quotes: List<String>) {
  if (quotes.isEmpty()) return
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      stringResource(title),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    quotes.forEach { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
  }
}

@Composable
private fun Salary(salary: SalaryRange?) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(Res.string.insights_salary_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      salary?.let { OpenSource(it.url, stringResource(it.source.label)) }
    }
    if (salary == null) {
      Muted(stringResource(Res.string.insights_no_salary))
      return
    }
    Text(
      stringResource(
        Res.string.insights_salary_range,
        money(salary.low, salary.currency),
        money(salary.high, salary.currency),
      ),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      stringResource(Res.string.insights_salary_median, money(salary.median, salary.currency)),
      style = MaterialTheme.typography.bodyMedium,
    )
    Text(
      stringResource(Res.string.insights_salary_role, salary.role),
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
    if (!salary.exactRole) {
      Muted(stringResource(Res.string.insights_salary_nearest, stringResource(salary.source.label)))
    }
    val min = salary.minExperience
    val max = salary.maxExperience
    if (min != null && max != null) {
      Muted(stringResource(Res.string.insights_salary_experience, min, max))
    }
    salary.samples?.let { Muted(stringResource(Res.string.insights_salary_samples, it)) }
  }
}

@Composable
private fun OpenSource(url: String, source: String) {
  val uriHandler = LocalUriHandler.current
  IconButton(
    onClick = { runCatching { uriHandler.openUri(url) } },
    shapes = IconButtonDefaults.shapes(),
  ) {
    Icon(
      Icons.AutoMirrored.Outlined.OpenInNew,
      stringResource(Res.string.insights_open_source, source),
    )
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
private fun Notice(text: String, onRetry: (() -> Unit)? = null) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Muted(text)
    if (onRetry != null) {
      FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.insights_retry))
      }
    }
  }
}

/** Rupees in lakhs and crores as Indian sites write them; other currencies with separators. */
fun money(amount: Long, currency: String): String =
  when (currency.uppercase()) {
    "INR" ->
      when {
        amount >= CRORE -> "₹${oneDecimal(amount / CRORE.toDouble())} Cr"
        amount >= LAKH -> "₹${oneDecimal(amount / LAKH.toDouble())} L"
        else -> "₹${grouped(amount)}"
      }
    "USD" -> "$${grouped(amount)}"
    "EUR" -> "€${grouped(amount)}"
    "GBP" -> "£${grouped(amount)}"
    else -> "$currency ${grouped(amount)}"
  }

/** 91683 as "91,683". */
fun grouped(value: Long): String =
  value.toString().reversed().chunked(3).joinToString(",").reversed()

private const val LAKH = 100_000L
private const val CRORE = 10_000_000L
