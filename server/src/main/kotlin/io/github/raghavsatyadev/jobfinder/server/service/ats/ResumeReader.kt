package io.github.raghavsatyadev.jobfinder.server.service.ats

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.model.ResumeFlag
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.text.PDFTextStripper
import org.jsoup.parser.Parser

/** What was read from a resume file. */
data class ResumeText(
  val text: String,
  val pages: Int?,
  val words: Int,
  val flags: List<ResumeFlag>,
)

/**
 * Reads the text of a PDF, DOCX or plain-text resume and audits its formatting the way an ATS
 * parser sees it: missing sections, contact details and dates, tables, images, columns and glyphs
 * that extract as junk. Only the text is kept.
 */
object ResumeReader {
  /** Resumes with fewer words are scanned images or empty. */
  private const val MIN_WORDS = 50
  private const val SHORT_WORDS = 200
  private const val LONG_WORDS = 1_200
  private const val MAX_PAGES = 2
  /** Kept text; far more than a two-page resume. */
  const val MAX_CHARS = 40_000

  fun read(fileName: String, bytes: ByteArray): ResumeText {
    val structure =
      when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> readPdf(bytes)
        "docx" -> readDocx(bytes)
        "txt",
        "md" -> Structure(bytes.decodeToString(), pages = null)
        else -> unreadable("Upload a PDF, DOCX or text file.")
      }
    val text = clean(structure.text).take(MAX_CHARS)
    val words = countWords(text)
    if (words < MIN_WORDS) {
      unreadable("No text could be read from this file. Is it a scanned image? Upload a text PDF.")
    }
    return ResumeText(text, structure.pages, words, audit(text, words, structure))
  }

  private class Structure(
    val text: String,
    val pages: Int?,
    val tables: Boolean = false,
    val images: Boolean = false,
    val columns: Boolean = false,
  )

  private fun readPdf(bytes: ByteArray): Structure =
    try {
      Loader.loadPDF(bytes).use { doc ->
        val images =
          doc.pages.any { page ->
            val resources = page.resources ?: return@any false
            resources.xObjectNames.any { resources.getXObject(it) is PDImageXObject }
          }
        Structure(PDFTextStripper().getText(doc), doc.numberOfPages, images = images)
      }
    } catch (e: ApiException) {
      throw e
    } catch (_: Exception) {
      unreadable("This PDF could not be read. Is it password protected?")
    }

  private fun readDocx(bytes: ByteArray): Structure {
    var document: String? = null
    var images = false
    try {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          when {
            entry.name == "word/document.xml" -> document = zip.readBytes().decodeToString()
            entry.name.startsWith("word/media/") -> images = true
          }
        }
      }
    } catch (_: Exception) {
      unreadable("This DOCX file could not be read.")
    }
    val xml = document ?: unreadable("This DOCX file has no document in it.")
    val columns = Regex("<w:cols\\b[^>]*w:num=\"([2-9])\"").containsMatchIn(xml)
    return Structure(docxText(xml), null, tables = "<w:tbl>" in xml, images = images, columns)
  }

  /** The text of `word/document.xml`: a line per paragraph, tabs kept. */
  fun docxText(xml: String): String =
    xml
      .replace(Regex("<w:tab/>"), "\t")
      .replace(Regex("<w:br/>"), "\n")
      .replace(Regex("</w:p>"), "\n")
      .replace(Regex("<[^>]+>"), "")
      .let { Parser.unescapeEntities(it, false) }

  private fun clean(text: String): String =
    text
      .replace("\r\n", "\n")
      .lines()
      .joinToString("\n") { it.trimEnd() }
      .replace(Regex("\n{3,}"), "\n\n")
      .trim()

  private fun countWords(text: String): Int =
    text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

  private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
  private val EMAIL = Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+")
  private val PHONE = Regex("\\+?\\d[\\d ().-]{7,}\\d")
  private val YEAR = Regex("\\b(19|20)\\d{2}\\b")
  private val EXPERIENCE =
    Regex("^\\W*(work |professional )?(experience|employment|work history)\\b", OPTIONS)
  private val EDUCATION = Regex("^\\W*(education|academic|qualifications)\\b", OPTIONS)
  private val SKILLS =
    Regex("^\\W*(technical |core |key )?(skills|technologies|tech stack)\\b", OPTIONS)

  private fun audit(text: String, words: Int, structure: Structure): List<ResumeFlag> = buildList {
    if (words < SHORT_WORDS) add(ResumeFlag.TOO_SHORT)
    if (words > LONG_WORDS || (structure.pages ?: 0) > MAX_PAGES) add(ResumeFlag.TOO_LONG)
    if (!EMAIL.containsMatchIn(text)) add(ResumeFlag.NO_EMAIL)
    if (!PHONE.containsMatchIn(text)) add(ResumeFlag.NO_PHONE)
    if (!EXPERIENCE.containsMatchIn(text)) add(ResumeFlag.NO_EXPERIENCE_SECTION)
    if (!EDUCATION.containsMatchIn(text)) add(ResumeFlag.NO_EDUCATION_SECTION)
    if (!SKILLS.containsMatchIn(text)) add(ResumeFlag.NO_SKILLS_SECTION)
    if (!YEAR.containsMatchIn(text)) add(ResumeFlag.NO_DATES)
    if (text.count { it in ''..'' || it == '�' } > 3) add(ResumeFlag.ODD_CHARACTERS)
    if (structure.tables) add(ResumeFlag.TABLES)
    if (structure.images) add(ResumeFlag.IMAGES)
    if (structure.columns) add(ResumeFlag.MULTI_COLUMN)
  }

  private fun unreadable(message: String): Nothing =
    throw ApiException(ApiErrorCode.BAD_REQUEST, message)
}
