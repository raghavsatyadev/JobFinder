@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.raghavsatyadev.jobfinder.ui.ats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.ats_category_experience
import io.github.raghavsatyadev.jobfinder.resources.ats_category_hard_skill
import io.github.raghavsatyadev.jobfinder.resources.ats_category_role
import io.github.raghavsatyadev.jobfinder.resources.ats_category_soft_skill
import io.github.raghavsatyadev.jobfinder.resources.ats_disqualifiers_title
import io.github.raghavsatyadev.jobfinder.resources.ats_failed
import io.github.raghavsatyadev.jobfinder.resources.ats_fixes_title
import io.github.raghavsatyadev.jobfinder.resources.ats_formatting_title
import io.github.raghavsatyadev.jobfinder.resources.ats_keyword_exact
import io.github.raghavsatyadev.jobfinder.resources.ats_keyword_missing
import io.github.raghavsatyadev.jobfinder.resources.ats_keyword_partial
import io.github.raghavsatyadev.jobfinder.resources.ats_keyword_partial_label
import io.github.raghavsatyadev.jobfinder.resources.ats_keywords_title
import io.github.raghavsatyadev.jobfinder.resources.ats_load_failed
import io.github.raghavsatyadev.jobfinder.resources.ats_match
import io.github.raghavsatyadev.jobfinder.resources.ats_no_description
import io.github.raghavsatyadev.jobfinder.resources.ats_no_resume
import io.github.raghavsatyadev.jobfinder.resources.ats_not_shortlisted
import io.github.raghavsatyadev.jobfinder.resources.ats_overall
import io.github.raghavsatyadev.jobfinder.resources.ats_pill
import io.github.raghavsatyadev.jobfinder.resources.ats_pill_description
import io.github.raghavsatyadev.jobfinder.resources.ats_queued
import io.github.raghavsatyadev.jobfinder.resources.ats_retry
import io.github.raghavsatyadev.jobfinder.resources.ats_score_achievements
import io.github.raghavsatyadev.jobfinder.resources.ats_score_education
import io.github.raghavsatyadev.jobfinder.resources.ats_score_experience
import io.github.raghavsatyadev.jobfinder.resources.ats_score_formatting
import io.github.raghavsatyadev.jobfinder.resources.ats_score_keywords
import io.github.raghavsatyadev.jobfinder.resources.ats_score_trajectory
import io.github.raghavsatyadev.jobfinder.resources.ats_score_value
import io.github.raghavsatyadev.jobfinder.resources.ats_scores_title
import io.github.raghavsatyadev.jobfinder.resources.ats_shortlisted
import io.github.raghavsatyadev.jobfinder.resources.improve_open
import io.github.raghavsatyadev.jobfinder.resources.offline_later
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_images
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_multi_column
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_no_dates
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_no_education_section
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_no_email
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_no_experience_section
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_no_phone
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_no_skills_section
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_odd_characters
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_tables
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_too_long
import io.github.raghavsatyadev.jobfinder.resources.resume_flag_too_short
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.AtsState
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.AtsSummary
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordCategory
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import io.github.raghavsatyadev.jobfinder.ui.components.LocalComputerOffline
import io.github.raghavsatyadev.jobfinder.ui.components.StatusPill
import io.github.raghavsatyadev.jobfinder.ui.components.StatusTone
import io.github.raghavsatyadev.jobfinder.ui.components.WavyProgress
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One decimal, as the scores are given: `7.6`, `10.0`. */
fun oneDecimal(value: Double): String = ((value * 10).roundToInt() / 10.0).toString()

/** Shortlisted is good news, a match under 40% screens the resume out, the rest sits between. */
fun AtsSummary.tone(): StatusTone =
  when {
    shortlisted -> StatusTone.Positive
    atsMatch < 40 -> StatusTone.Negative
    else -> StatusTone.Neutral
  }

/** The job card's score: keyword match and overall. */
@Composable
fun AtsPill(summary: AtsSummary, modifier: Modifier = Modifier) {
  val verdict =
    stringResource(
      if (summary.shortlisted) Res.string.ats_shortlisted else Res.string.ats_not_shortlisted
    )
  val description =
    stringResource(
      Res.string.ats_pill_description,
      summary.atsMatch,
      oneDecimal(summary.overall),
      verdict,
    )
  StatusPill(
    stringResource(Res.string.ats_pill, summary.atsMatch, oneDecimal(summary.overall)),
    summary.tone(),
    modifier.clearAndSetSemantics { contentDescription = description },
  )
}

/**
 * The job detail's ATS review, or why there is none yet. [onImprove] opens the resume fix plan;
 * `null` where no computer writes one.
 */
@Composable
fun AtsSection(
  status: AtsStatus?,
  loadFailed: Boolean,
  onRetry: () -> Unit,
  onImprove: (() -> Unit)? = null,
) {
  when {
    loadFailed && LocalComputerOffline.current -> Notice(stringResource(Res.string.offline_later))
    loadFailed -> Notice(stringResource(Res.string.ats_load_failed), onRetry)
    status == null -> Progress()
    else ->
      when (status.state) {
        AtsState.NO_RESUME -> Notice(stringResource(Res.string.ats_no_resume))
        AtsState.NO_DESCRIPTION -> Notice(stringResource(Res.string.ats_no_description))
        AtsState.QUEUED -> {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Notice(stringResource(Res.string.ats_queued))
            Progress()
          }
        }
        AtsState.FAILED -> Notice(stringResource(Res.string.ats_failed), onRetry)
        AtsState.READY ->
          status.review?.let {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Review(it)
              if (onImprove != null) {
                FilledTonalButton(onClick = onImprove, shapes = ButtonDefaults.shapes()) {
                  Text(stringResource(Res.string.improve_open))
                }
              }
            }
          } ?: Progress()
      }
  }
}

@Composable
private fun Progress() {
  WavyProgress()
}

@Composable
private fun Notice(text: String, onRetry: (() -> Unit)? = null) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (onRetry != null) {
      FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.ats_retry))
      }
    }
  }
}

@Composable
private fun Review(review: AtsReview) {
  val summary = review.summary
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      itemVerticalAlignment = Alignment.CenterVertically,
    ) {
      StatusPill(
        stringResource(
          if (summary.shortlisted) Res.string.ats_shortlisted else Res.string.ats_not_shortlisted
        ),
        summary.tone(),
      )
      Text(
        stringResource(Res.string.ats_overall, oneDecimal(summary.overall)),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        stringResource(Res.string.ats_match, summary.atsMatch),
        style = MaterialTheme.typography.titleMedium,
      )
    }
    if (review.assessment.isNotBlank()) {
      Text(review.assessment, style = MaterialTheme.typography.bodyLarge)
    }
    if (review.disqualifiers.isNotEmpty()) {
      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            stringResource(Res.string.ats_disqualifiers_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          review.disqualifiers.forEach {
            Text("• $it", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    }
    Scores(review)
    Keywords(review.keywords)
    if (review.formatting.isNotEmpty()) {
      SubTitle(stringResource(Res.string.ats_formatting_title))
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        review.formatting.forEach {
          Text("• ${stringResource(it.text)}", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    if (review.fixes.isNotEmpty()) {
      SubTitle(stringResource(Res.string.ats_fixes_title))
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        review.fixes.forEachIndexed { index, fix ->
          Text("${index + 1}. $fix", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
  }
}

@Composable
private fun SubTitle(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.titleSmall,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.semantics { heading() },
  )
}

@Composable
private fun Scores(review: AtsReview) {
  val s = review.scores
  val rows =
    listOf(
      Res.string.ats_score_keywords to s.keywords,
      Res.string.ats_score_experience to s.experience,
      Res.string.ats_score_achievements to s.achievements,
      Res.string.ats_score_formatting to s.formatting,
      Res.string.ats_score_education to s.education,
      Res.string.ats_score_trajectory to s.trajectory,
    )
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SubTitle(stringResource(Res.string.ats_scores_title))
    rows.forEach { (label, value) ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(label),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.width(168.dp),
        )
        LinearProgressIndicator(
          progress = { (value / 10).toFloat() },
          modifier = Modifier.weight(1f),
          drawStopIndicator = {},
        )
        Spacer(Modifier.width(12.dp))
        Text(
          stringResource(Res.string.ats_score_value, oneDecimal(value)),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

@Composable
private fun Keywords(keywords: List<AtsKeyword>) {
  val found = keywords.count { it.match != KeywordMatch.MISSING }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SubTitle(stringResource(Res.string.ats_keywords_title, found, keywords.size))
    for (category in KeywordCategory.entries) {
      // Missing ones first: they are what to fix.
      val items =
        keywords
          .filter { it.category == category }
          .sortedBy {
            when (it.match) {
              KeywordMatch.MISSING -> 0
              KeywordMatch.PARTIAL -> 1
              KeywordMatch.EXACT -> 2
            }
          }
      if (items.isEmpty()) continue
      Text(
        stringResource(category.label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items.forEach { KeywordChip(it) }
      }
    }
  }
}

@Composable
private fun KeywordChip(keyword: AtsKeyword) {
  val colors = MaterialTheme.colorScheme
  val container: Color
  val content: Color
  val icon: ImageVector
  val description: String
  val label: String
  when (keyword.match) {
    KeywordMatch.EXACT -> {
      container = colors.primaryContainer
      content = colors.onPrimaryContainer
      icon = Icons.Rounded.Check
      description = stringResource(Res.string.ats_keyword_exact, keyword.term, keyword.count)
      label = keyword.term
    }
    KeywordMatch.PARTIAL -> {
      container = colors.tertiaryContainer
      content = colors.onTertiaryContainer
      icon = Icons.Rounded.Remove
      val synonym = keyword.matchedAs.orEmpty()
      description = stringResource(Res.string.ats_keyword_partial, keyword.term, synonym)
      label = stringResource(Res.string.ats_keyword_partial_label, keyword.term, synonym)
    }
    KeywordMatch.MISSING -> {
      container = colors.errorContainer
      content = colors.onErrorContainer
      icon = Icons.Rounded.Close
      description = stringResource(Res.string.ats_keyword_missing, keyword.term)
      label = keyword.term
    }
  }
  Surface(
    shape = MaterialTheme.shapes.small,
    color = container,
    contentColor = content,
    modifier = Modifier.clearAndSetSemantics { contentDescription = description },
  ) {
    Row(
      Modifier.padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(4.dp))
      Text(label, style = MaterialTheme.typography.labelLarge)
    }
  }
}

val KeywordCategory.label: StringResource
  get() =
    when (this) {
      KeywordCategory.HARD_SKILL -> Res.string.ats_category_hard_skill
      KeywordCategory.SOFT_SKILL -> Res.string.ats_category_soft_skill
      KeywordCategory.EXPERIENCE -> Res.string.ats_category_experience
      KeywordCategory.ROLE -> Res.string.ats_category_role
    }

/** What the formatting problem means, in a sentence. */
val ResumeFlag.text: StringResource
  get() =
    when (this) {
      ResumeFlag.TOO_SHORT -> Res.string.resume_flag_too_short
      ResumeFlag.TOO_LONG -> Res.string.resume_flag_too_long
      ResumeFlag.NO_EMAIL -> Res.string.resume_flag_no_email
      ResumeFlag.NO_PHONE -> Res.string.resume_flag_no_phone
      ResumeFlag.NO_EXPERIENCE_SECTION -> Res.string.resume_flag_no_experience_section
      ResumeFlag.NO_EDUCATION_SECTION -> Res.string.resume_flag_no_education_section
      ResumeFlag.NO_SKILLS_SECTION -> Res.string.resume_flag_no_skills_section
      ResumeFlag.NO_DATES -> Res.string.resume_flag_no_dates
      ResumeFlag.ODD_CHARACTERS -> Res.string.resume_flag_odd_characters
      ResumeFlag.TABLES -> Res.string.resume_flag_tables
      ResumeFlag.IMAGES -> Res.string.resume_flag_images
      ResumeFlag.MULTI_COLUMN -> Res.string.resume_flag_multi_column
    }
