package io.github.raghavsatyadev.jobfinder.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A resume section the PDF can show, in the order the user picks. */
@Serializable
enum class PdfSection {
  @SerialName("summary") SUMMARY,
  @SerialName("experience") EXPERIENCE,
  @SerialName("skills") SKILLS,
  @SerialName("projects") PROJECTS,
  @SerialName("education") EDUCATION,
  @SerialName("certifications") CERTIFICATIONS,
  @SerialName("languages") LANGUAGES,
  @SerialName("honors") HONORS,
  @SerialName("courses") COURSES,
  /** Any other heading of the uploaded resume. */
  @SerialName("other") OTHER,
}

@Serializable
enum class PdfPageSize {
  @SerialName("letter") LETTER,
  @SerialName("a4") A4,
}

/** How the resume PDFs look: one column, the sections in [order], none of [hidden]. */
@Serializable
data class ResumePdfPrefs(
  val pageSize: PdfPageSize = PdfPageSize.A4,
  /** 1 or 2: bullets least related to the job are left out until it fits. */
  val maxPages: Int = 2,
  val order: List<PdfSection> = DEFAULT_ORDER,
  val hidden: Set<PdfSection> = emptySet(),
  /** A job's PDF may bring in LinkedIn skills, bullets and projects that hit the job's words. */
  val useLinkedIn: Boolean = true,
) {
  /** [order] with any section it lacks added at the end, so new sections are never lost. */
  val fullOrder: List<PdfSection>
    get() = (order + PdfSection.entries).distinct()

  companion object {
    val DEFAULT_ORDER =
      listOf(
        PdfSection.SUMMARY,
        PdfSection.EXPERIENCE,
        PdfSection.SKILLS,
        PdfSection.PROJECTS,
        PdfSection.EDUCATION,
        PdfSection.CERTIFICATIONS,
        PdfSection.LANGUAGES,
        PdfSection.HONORS,
        PdfSection.COURSES,
        PdfSection.OTHER,
      )
  }
}

/** What the LinkedIn data export brought in; the archive itself is not kept. */
@Serializable
data class LinkedInSummary(
  val importedAt: Long,
  val name: String? = null,
  val positions: Int = 0,
  val skills: Int = 0,
  val projects: Int = 0,
  val education: Int = 0,
  val certifications: Int = 0,
)

/** The resume PDF settings and what they work from. */
@Serializable
data class ResumePdfStatus(
  val prefs: ResumePdfPrefs = ResumePdfPrefs(),
  /** `null` until a LinkedIn data export is imported. */
  val linkedIn: LinkedInSummary? = null,
  /** An uploaded resume is there to build from. */
  val hasResume: Boolean = false,
)

/** `POST /resume/pdf`: a PDF for [jobId] (`null`: the general one) with [edits] from its plan. */
@Serializable data class MakeResumePdf(val jobId: Long? = null, val edits: List<Int> = emptyList())

/** A resume PDF, checked by reading it back the way an ATS does. */
@Serializable
data class ResumePdf(
  val fileName: String,
  /** The file, base64. */
  val data: String,
  val pages: Int,
  /** The keyword match of the PDF's text against the job's review; `null` for the general one. */
  val match: Int? = null,
  /** The match of the uploaded resume, to compare. */
  val baseMatch: Int? = null,
  /** What the PDF brought in from LinkedIn, such as "Skill: GraphQL". */
  val fromLinkedIn: List<String> = emptyList(),
  /** Problems an ATS would see in the PDF; empty when it reads clean. */
  val flags: List<ResumeFlag> = emptyList(),
)
