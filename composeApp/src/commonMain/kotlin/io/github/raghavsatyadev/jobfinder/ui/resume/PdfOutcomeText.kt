package io.github.raghavsatyadev.jobfinder.ui.resume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.jobfinder.resources.Res
import io.github.raghavsatyadev.jobfinder.resources.pdf_flags
import io.github.raghavsatyadev.jobfinder.resources.pdf_from_linkedin
import io.github.raghavsatyadev.jobfinder.resources.pdf_saved
import io.github.raghavsatyadev.jobfinder.resources.pdf_saved_match
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_certifications
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_courses
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_education
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_experience
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_honors
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_languages
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_other
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_projects
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_skills
import io.github.raghavsatyadev.jobfinder.resources.pdf_section_summary
import io.github.raghavsatyadev.jobfinder.shared.model.PdfSection
import io.github.raghavsatyadev.jobfinder.ui.ats.text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val PdfSection.label: StringResource
  get() =
    when (this) {
      PdfSection.SUMMARY -> Res.string.pdf_section_summary
      PdfSection.EXPERIENCE -> Res.string.pdf_section_experience
      PdfSection.SKILLS -> Res.string.pdf_section_skills
      PdfSection.PROJECTS -> Res.string.pdf_section_projects
      PdfSection.EDUCATION -> Res.string.pdf_section_education
      PdfSection.CERTIFICATIONS -> Res.string.pdf_section_certifications
      PdfSection.LANGUAGES -> Res.string.pdf_section_languages
      PdfSection.HONORS -> Res.string.pdf_section_honors
      PdfSection.COURSES -> Res.string.pdf_section_courses
      PdfSection.OTHER -> Res.string.pdf_section_other
    }

/**
 * What a saved PDF came to: its pages and match, what LinkedIn added, and what an ATS may trip on.
 */
@Composable
fun PdfOutcomeText(outcome: PdfOutcome) {
  when (outcome) {
    is PdfOutcome.Failed ->
      Text(
        outcome.message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    is PdfOutcome.Saved -> {
      val pdf = outcome.pdf
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val match = pdf.match
        val base = pdf.baseMatch
        Text(
          if (match != null && base != null) {
            stringResource(Res.string.pdf_saved_match, pdf.pages, match, base)
          } else stringResource(Res.string.pdf_saved, pdf.pages),
          style = MaterialTheme.typography.bodyMedium,
        )
        if (pdf.fromLinkedIn.isNotEmpty()) {
          Muted(stringResource(Res.string.pdf_from_linkedin, pdf.fromLinkedIn.joinToString("; ")))
        }
        if (pdf.flags.isNotEmpty()) {
          val flags = pdf.flags.map { stringResource(it.text) }
          Muted(stringResource(Res.string.pdf_flags, flags.joinToString("; ")))
        }
      }
    }
  }
}
