package io.github.raghavsatyadev.jobfinder.server.service.resume

import io.github.raghavsatyadev.jobfinder.shared.model.PdfPageSize
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font

/** A written PDF and its page count. */
class PdfFile(val bytes: ByteArray, val pages: Int)

/**
 * Writes a [ResumeDoc] as an ATS-friendly PDF: one column, real text in an embedded font (Carlito,
 * SIL Open Font License), standard headings, dates on the right of each role, no tables, images or
 * icons.
 */
class ResumePdfWriter(private val pageSize: PdfPageSize) {
  private lateinit var doc: PDDocument
  private lateinit var regular: PDFont
  private lateinit var bold: PDFont
  private var stream: PDPageContentStream? = null
  private var y = 0f
  private var pages = 0
  private val box = if (pageSize == PdfPageSize.A4) PDRectangle.A4 else PDRectangle.LETTER
  private val left = MARGIN_X
  private val right = box.width - MARGIN_X
  private val width = right - left
  /** Characters the font has, by code point. */
  private val glyphs = mutableMapOf<Pair<PDFont, Int>, Boolean>()

  fun write(resume: ResumeDoc): PdfFile =
    PDDocument().use { document ->
      doc = document
      regular = font("Carlito-Regular.ttf")
      bold = font("Carlito-Bold.ttf")
      document.documentInformation.apply {
        title = listOf(resume.name, "Resume").filter { it.isNotBlank() }.joinToString(" - ")
        author = resume.name
        creator = "JobFinder"
      }
      newPage()
      header(resume)
      for (section in resume.sections) section(section)
      stream?.close()
      val out = ByteArrayOutputStream()
      document.save(out)
      PdfFile(out.toByteArray(), pages)
    }

  private fun font(file: String): PDFont {
    val bytes =
      javaClass.getResourceAsStream("/fonts/$file")?.use { it.readBytes() }
        ?: error("Missing font $file")
    return PDType0Font.load(doc, ByteArrayInputStream(withoutLigatures(bytes)))
  }

  private fun newPage() {
    stream?.close()
    val page = PDPage(box)
    doc.addPage(page)
    stream = PDPageContentStream(doc, page)
    pages++
    y = box.height - MARGIN_TOP
  }

  /** Room for [lines] lines of [size] text, on a new page when this one is full. */
  private fun room(size: Float, lines: Int = 1) {
    if (y - size * LEADING * lines < MARGIN_BOTTOM) newPage()
  }

  private fun header(resume: ResumeDoc) {
    if (resume.name.isNotBlank()) {
      y -= NAME
      centered(resume.name, bold, NAME)
      y -= NAME * 0.45f
    }
    if (resume.contact.isNotEmpty()) {
      for (line in wrap(resume.contact.joinToString("  |  "), regular, SMALL, width)) {
        y -= SMALL * LEADING
        centered(line, regular, SMALL)
      }
    }
    y -= 4f
  }

  private fun section(section: DocSection) {
    room(HEADING, 4)
    y -= HEADING * 1.5f
    text(section.title.uppercase(), bold, HEADING, left)
    y -= 3.5f
    stream!!.apply {
      setLineWidth(0.6f)
      moveTo(left, y)
      lineTo(right, y)
      stroke()
    }
    y -= 2f
    for (entry in section.entries) entry(entry)
  }

  private fun entry(entry: DocEntry) {
    if (entry.title.isNotEmpty() || entry.dates.isNotEmpty()) {
      room(BODY, 3)
      y -= 3f
      row(entry.title, bold, entry.dates, BODY)
    }
    if (entry.subtitle.isNotEmpty() || entry.place.isNotEmpty()) {
      row(entry.subtitle, regular, entry.place, BODY)
    }
    for (line in entry.lines) paragraph(line, left)
    for (bullet in entry.bullets) paragraph(bullet, left + BULLET_INDENT, bullet = true)
  }

  /** [main] on the left, [side] on the right; [main] wraps when both don't fit. */
  private fun row(main: String, font: PDFont, side: String, size: Float) {
    val sideWidth = if (side.isEmpty()) 0f else measure(side, regular, size) + 12f
    val lines = wrap(main, font, size, width - sideWidth).ifEmpty { listOf("") }
    lines.forEachIndexed { i, line ->
      room(size)
      y -= size * LEADING
      text(line, font, size, left)
      if (i == 0 && side.isNotEmpty()) {
        text(side, regular, size, right - measure(side, regular, size))
      }
    }
  }

  /** [text] wrapped at [indent]; a [bullet] gets a bullet sign on its first line. */
  private fun paragraph(text: String, indent: Float, bullet: Boolean = false) {
    wrap(text, regular, BODY, right - indent).forEachIndexed { i, line ->
      room(BODY)
      y -= BODY * LEADING
      if (bullet && i == 0) text("•", regular, BODY, left + 2f)
      text(line, regular, BODY, indent)
    }
  }

  private fun centered(line: String, font: PDFont, size: Float) {
    text(line, font, size, (box.width - measure(line, font, size)) / 2)
  }

  private fun text(line: String, font: PDFont, size: Float, x: Float) {
    val safe = printable(line, font)
    if (safe.isBlank()) return
    stream!!.apply {
      beginText()
      setFont(font, size)
      newLineAtOffset(x, y)
      showText(safe)
      endText()
    }
  }

  private fun measure(line: String, font: PDFont, size: Float): Float =
    font.getStringWidth(printable(line, font)) / 1000f * size

  /** Words of [text] in lines no wider than [max]. */
  private fun wrap(text: String, font: PDFont, size: Float, max: Float): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
      val next = if (line.isEmpty()) word else "$line $word"
      if (line.isNotEmpty() && measure(next, font, size) > max) {
        lines += line
        line = word
      } else line = next
    }
    if (line.isNotEmpty()) lines += line
    return lines
  }

  /** [line] with what the font can't show replaced (₹ as "INR ") or left out. */
  private fun printable(line: String, font: PDFont): String = buildString {
    line.codePoints().forEach { cp ->
      when {
        cp == '\t'.code -> append(' ')
        cp < 0x20 -> Unit
        cp in FALLBACK -> append(FALLBACK[cp])
        has(font, cp) -> appendCodePoint(cp)
      }
    }
  }

  private fun has(font: PDFont, cp: Int): Boolean =
    glyphs.getOrPut(font to cp) {
      try {
        font.encode(String(Character.toChars(cp)))
        true
      } catch (_: Exception) {
        false
      }
    }

  private companion object {
    /**
     * [ttf] with its GSUB table hidden. PDFBox would set "ti" or "fi" as one ligature glyph, which
     * many ATS parsers read as nothing ("Mul pla orm"); renaming the table's tag leaves plain
     * glyphs.
     */
    fun withoutLigatures(ttf: ByteArray): ByteArray {
      val out = ttf.copyOf()
      val tables = ((out[4].toInt() and 0xFF) shl 8) or (out[5].toInt() and 0xFF)
      for (i in 0 until tables) {
        val at = 12 + i * 16
        if (at + 4 <= out.size && String(out, at, 4, Charsets.ISO_8859_1) == "GSUB") {
          "xsub".toByteArray(Charsets.ISO_8859_1).copyInto(out, at)
        }
      }
      return out
    }

    const val MARGIN_X = 46f
    const val MARGIN_TOP = 40f
    const val MARGIN_BOTTOM = 40f
    const val LEADING = 1.22f
    const val NAME = 20f
    const val HEADING = 11.5f
    const val BODY = 10.5f
    const val SMALL = 9.5f
    const val BULLET_INDENT = 12f

    val FALLBACK =
      mapOf(
        0x20B9 to "INR ",
        0x2010 to "-",
        0x2011 to "-",
        0x2012 to "-",
        0x2013 to "-",
        0x2014 to "-",
        0x2018 to "'",
        0x2019 to "'",
        0x201C to "\"",
        0x201D to "\"",
        0x2026 to "...",
        0x00A0 to " ",
      )
  }
}
