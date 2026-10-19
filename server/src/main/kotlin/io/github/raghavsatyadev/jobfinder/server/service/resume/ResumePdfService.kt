package io.github.raghavsatyadev.jobfinder.server.service.resume

import io.github.raghavsatyadev.jobfinder.server.db.JobsDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeProfileDao
import io.github.raghavsatyadev.jobfinder.server.db.ResumeProfileEntity
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.ats.KeywordMatcher
import io.github.raghavsatyadev.jobfinder.server.service.ats.ResumeReader
import io.github.raghavsatyadev.jobfinder.server.service.notFound
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.AtsReview
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch
import io.github.raghavsatyadev.jobfinder.shared.model.LinkedInSummary
import io.github.raghavsatyadev.jobfinder.shared.model.MakeResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdf
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfPrefs
import io.github.raghavsatyadev.jobfinder.shared.model.ResumePdfStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumeImproveRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import kotlin.io.encoding.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Makes resume PDFs from the uploaded resume, the LinkedIn data export and the user's PDF
 * preferences. A job's PDF is fitted to the job by [ResumeTailor] with the edits the user ticked,
 * then read back like an ATS reads it to give its keyword match.
 */
class ResumePdfService(
  private val profiles: ResumeProfileDao,
  private val resumes: ResumeDao,
  private val jobs: JobsDao,
  /** The job's ATS review against the current resume, or `null` when it has none yet. */
  private val review: suspend (Long) -> AtsReview?,
  private val improve: ResumeImproveRepository? = null,
  private val clock: () -> Long = System::currentTimeMillis,
) : ResumePdfRepository {
  override suspend fun status(): ResumePdfStatus {
    val row = profiles.get()
    val linkedIn = row?.linkedIn()
    return ResumePdfStatus(
      prefs = row.prefs(),
      linkedIn =
        linkedIn?.let {
          LinkedInSummary(
            importedAt = row.linkedinImportedAt ?: 0,
            name = it.name.ifEmpty { null },
            positions = it.positions.size,
            skills = it.skills.size,
            projects = it.projects.size,
            education = it.education.size,
            certifications = it.certifications.size,
          )
        },
      hasResume = resumes.get() != null,
    )
  }

  override suspend fun savePrefs(prefs: ResumePdfPrefs): ResumePdfStatus {
    val checked = prefs.copy(maxPages = prefs.maxPages.coerceIn(1, 2), order = prefs.fullOrder)
    val row = profiles.get() ?: ResumeProfileEntity()
    profiles.upsert(row.copy(prefs = ApiJson.encodeToString(checked)))
    return status()
  }

  override suspend fun importLinkedIn(fileName: String, bytes: ByteArray): ResumePdfStatus {
    if (bytes.size > ResumePdfRepository.MAX_EXPORT_BYTES) {
      throw ApiException(ApiErrorCode.BAD_REQUEST, "This file is too large for a LinkedIn export.")
    }
    val profile = withContext(Dispatchers.IO) { LinkedInImport.read(bytes) }
    val row = profiles.get() ?: ResumeProfileEntity()
    profiles.upsert(
      row.copy(linkedin = ApiJson.encodeToString(profile), linkedinImportedAt = clock())
    )
    return status()
  }

  override suspend fun deleteLinkedIn(): ResumePdfStatus {
    profiles.get()?.let { profiles.upsert(it.copy(linkedin = null, linkedinImportedAt = null)) }
    return status()
  }

  override suspend fun makePdf(request: MakeResumePdf): ResumePdf {
    val row = profiles.get()
    val prefs = row.prefs()
    val linkedIn = row?.linkedIn()
    val jobId = request.jobId
    val job = jobId?.let { jobs.get(it) ?: notFound("Job") }
    val review = jobId?.let { review(it) }
    val text =
      if (jobId != null && request.edits.isNotEmpty() && improve != null) {
        // The plan is gone when the resume changed or the app restarted: the resume as it is.
        try {
          improve.improvedResume(jobId, request.edits).text
        } catch (_: ApiException) {
          resumes.get()?.text
        }
      } else resumes.get()?.text
    val base =
      text?.let { ResumeDocs.fromText(it) }?.let { withContact(it, linkedIn) }
        ?: linkedIn?.let { ResumeDocs.fromLinkedIn(it) }
        ?: throw ApiException(
          ApiErrorCode.BAD_REQUEST,
          if (text == null) "Upload your resume or import your LinkedIn data first."
          else "The resume's sections (Experience, Skills, Education) could not be found.",
        )
    val tailored =
      if (review != null) {
        ResumeTailor.tailor(base, review.keywords, linkedIn.takeIf { prefs.useLinkedIn })
      } else Tailored(base, emptyList())
    val terms = review?.keywords.orEmpty().map { KeywordMatcher.normalize(it.term) }
    val pdf =
      withContext(Dispatchers.Default) {
        fit(ResumeTailor.arrange(tailored.doc, prefs), prefs, terms)
      }
    val read = ResumeReader.read("resume.pdf", pdf.bytes)
    return ResumePdf(
      fileName = fileName(base.name, job?.companyName),
      data = Base64.encode(pdf.bytes),
      pages = pdf.pages,
      match = review?.let { KeywordMatcher.matchPercent(rematch(it.keywords, read.text)) },
      baseMatch = review?.let { KeywordMatcher.matchPercent(it.keywords) },
      fromLinkedIn = tailored.fromLinkedIn,
      flags = read.flags,
    )
  }

  /** The LinkedIn skills, roles and projects as text; empty without an import. */
  suspend fun linkedInText(): String {
    val p = profiles.get()?.linkedIn() ?: return ""
    return (p.skills +
        p.positions.map { "${it.title} ${it.description}" } +
        p.projects.map { "${it.title} ${it.description}" } +
        p.certifications.map { it.name })
      .joinToString("\n")
  }

  /** The PDF of [doc], with bullets left out until it is at most the page limit. */
  private fun fit(doc: ResumeDoc, prefs: ResumePdfPrefs, terms: List<String>): PdfFile {
    var current = doc
    var pdf = ResumePdfWriter(prefs.pageSize).write(current)
    var tries = 0
    while (pdf.pages > prefs.maxPages && tries++ < MAX_SHORTEN) {
      current = ResumeTailor.shorten(current, terms) ?: break
      pdf = ResumePdfWriter(prefs.pageSize).write(current)
    }
    return pdf
  }

  /** A resume without a contact line gets the LinkedIn name and contact details. */
  private fun withContact(doc: ResumeDoc, linkedIn: LinkedInProfile?): ResumeDoc =
    if (linkedIn == null || doc.contact.isNotEmpty()) doc
    else
      doc.copy(name = doc.name.ifEmpty { linkedIn.name }, contact = ResumeDocs.contactOf(linkedIn))

  private fun ResumeProfileEntity.linkedIn(): LinkedInProfile? = linkedin?.let {
    runCatching { ApiJson.decodeFromString<LinkedInProfile>(it) }.getOrNull()
  }

  private fun ResumeProfileEntity?.prefs(): ResumePdfPrefs =
    this?.prefs?.let { runCatching { ApiJson.decodeFromString<ResumePdfPrefs>(it) }.getOrNull() }
      ?: ResumePdfPrefs()

  companion object {
    /** Most bullets left out to fit the page limit. */
    private const val MAX_SHORTEN = 60

    /** [keywords] matched again against [text]: the term itself, or the synonym found before. */
    fun rematch(keywords: List<AtsKeyword>, text: String): List<AtsKeyword> {
      val cv = KeywordMatcher.normalize(text)
      return keywords.map {
        val exact = KeywordMatcher.count(cv, KeywordMatcher.normalize(it.term))
        val synonym =
          it.matchedAs?.let { s -> KeywordMatcher.count(cv, KeywordMatcher.normalize(s)) }
        when {
          exact > 0 -> it.copy(match = KeywordMatch.EXACT, count = exact)
          synonym != null && synonym > 0 -> it.copy(match = KeywordMatch.PARTIAL, count = synonym)
          else -> it.copy(match = KeywordMatch.MISSING, count = 0)
        }
      }
    }

    fun fileName(name: String, company: String?): String {
      return listOfNotNull(name.ifBlank { null }, "Resume", company?.let { "for $it" })
        .joinToString(" ")
        .replace(Regex("[\\\\/:*?\"<>|]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(100) + ".pdf"
    }
  }
}
