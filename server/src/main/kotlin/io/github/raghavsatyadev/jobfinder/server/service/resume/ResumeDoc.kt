package io.github.raghavsatyadev.jobfinder.server.service.resume

import io.github.raghavsatyadev.jobfinder.shared.model.PdfSection

/** A resume laid out for the PDF: a header, then sections of entries. */
data class ResumeDoc(
  val name: String,
  /** Email, phone, place and links, shown on one line under the name. */
  val contact: List<String>,
  val sections: List<DocSection>,
) {
  fun section(kind: PdfSection): DocSection? = sections.firstOrNull { it.kind == kind }

  /** All the text, as an ATS would read it. */
  val text: String
    get() = buildString {
      appendLine(name)
      appendLine(contact.joinToString(" | "))
      for (section in sections) {
        appendLine(section.title)
        for (entry in section.entries) appendLine(entry.text)
      }
    }
}

data class DocSection(val kind: PdfSection, val title: String, val entries: List<DocEntry>)

/**
 * A role, a project, a school or a plain block: a bold [title] with [dates] on the right, a
 * [subtitle] with [place] on the right, then [lines] as written and [bullets].
 */
data class DocEntry(
  val title: String = "",
  val dates: String = "",
  val subtitle: String = "",
  val place: String = "",
  val lines: List<String> = emptyList(),
  val bullets: List<String> = emptyList(),
) {
  val text: String
    get() =
      (listOf("$title $dates", "$subtitle $place") + lines + bullets)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

/** Builds a [ResumeDoc] from the uploaded resume's text or from the LinkedIn data. */
object ResumeDocs {
  private const val MONTH = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?"
  private const val DATE = "(?:$MONTH\\s+)?(?:19|20)\\d{2}|(?:0?[1-9]|1[0-2])/(?:19|20)\\d{2}"
  private const val END = "$DATE|present|current|now|today"

  /** A date or a date range at the end of a line, such as `Jan 2020 – Present`. */
  private val TRAILING_DATES =
    Regex("\\s*((?:$DATE)(?:\\s*(?:-|–|—|to)\\s*(?:$END))?)\\s*$", RegexOption.IGNORE_CASE)
  private val BULLET = Regex("^\\s*[•●▪‣◦○■□➢►✓\\-*–]\\s+")

  private val HEADINGS: List<Pair<Regex, PdfSection>> =
    listOf(
        "(professional |career |executive )?(summary|profile|about( me)?|objective)" to
          PdfSection.SUMMARY,
        "(work |professional |relevant )?(experience|employment( history)?|work history)" to
          PdfSection.EXPERIENCE,
        "(technical |core |key |relevant )?(skills|technologies|tech stack|competencies)" +
          "( (and|&) (tools|technologies|interests))?" to PdfSection.SKILLS,
        "(personal |key |selected |side )?projects" to PdfSection.PROJECTS,
        "(education|academic background|academics|qualifications)" to PdfSection.EDUCATION,
        "(certifications?|certificates|licenses( (and|&) certifications)?)" to
          PdfSection.CERTIFICATIONS,
        "languages" to PdfSection.LANGUAGES,
        "(honors|honours|awards|achievements)( (and|&) awards)?" to PdfSection.HONORS,
        "(courses|coursework|relevant coursework|training)" to PdfSection.COURSES,
        "(publications|patents|volunteering|volunteer( experience| work)?|leadership|activities|" +
          "extracurricular activities|interests|hobbies|references|open source( contributions)?)" to
          PdfSection.OTHER,
      )
      .map { (pattern, kind) -> Regex("^$pattern:?$", RegexOption.IGNORE_CASE) to kind }

  /** The sections the text is split into by its headings; `null` when it has none. */
  fun fromText(text: String): ResumeDoc? {
    val lines = text.lines().map { it.trim() }
    val starts = lines.indices.filter { heading(lines[it]) != null }
    if (starts.isEmpty()) return null
    val header = lines.take(starts.first()).filter { it.isNotEmpty() }
    val sections = starts.mapIndexed { i, start ->
      val kind = heading(lines[start])!!
      val body = lines.subList(start + 1, starts.getOrElse(i + 1) { lines.size })
      DocSection(kind, title(lines[start]), entries(kind, body))
    }
    return ResumeDoc(
      name = header.firstOrNull().orEmpty(),
      contact =
        header.drop(1).flatMap { it.split(Regex("\\s+[|•·]\\s+")) }.filter { it.isNotBlank() },
      sections = sections.filter { it.entries.isNotEmpty() },
    )
  }

  /** The section [line] heads; only known headings count, so a title in capitals is not one. */
  fun heading(line: String): PdfSection? =
    if (line.isEmpty() || line.length > 45) null
    else HEADINGS.firstOrNull { it.first.matches(line) }?.second

  private fun title(line: String): String =
    line.trimEnd(':').lowercase().split(' ').joinToString(" ") { word ->
      if (word in setOf("and", "of")) word else word.replaceFirstChar { it.uppercase() }
    }

  private fun entries(kind: PdfSection, body: List<String>): List<DocEntry> =
    when (kind) {
      PdfSection.SUMMARY -> paragraphs(body).map { DocEntry(lines = listOf(it)) }
      PdfSection.SKILLS,
      PdfSection.LANGUAGES,
      PdfSection.COURSES ->
        listOf(DocEntry(lines = listLines(body))).filter { it.lines.isNotEmpty() }
      else -> blocks(body)
    }

  /** Lines joined into paragraphs at blank lines. */
  private fun paragraphs(body: List<String>): List<String> =
    body
      .joinToString("\n")
      .split(Regex("\n\\s*\n"))
      .map { it.replace(Regex("\\s*\n\\s*"), " ").trim() }
      .filter { it.isNotEmpty() }

  /** One line per row, such as `Languages: Kotlin, Java`; a row that wrapped is joined again. */
  private fun listLines(body: List<String>): List<String> {
    val out = mutableListOf<String>()
    for (raw in body) {
      val line = raw.replace(BULLET, "").trim()
      if (line.isEmpty()) continue
      val last = out.lastOrNull()
      if (last != null && ':' !in line && (last.endsWith(',') || line.first().isLowerCase())) {
        out[out.lastIndex] = "$last $line"
      } else out += line
    }
    return out
  }

  /** Entries of heading lines, then bullets; a heading line after bullets starts the next one. */
  private fun blocks(body: List<String>): List<DocEntry> {
    val heads = mutableListOf<MutableList<String>>()
    val bullets = mutableListOf<MutableList<String>>()
    fun start() {
      heads += mutableListOf<String>()
      bullets += mutableListOf<String>()
    }
    for (line in body) {
      if (line.isEmpty()) continue
      if (heads.isEmpty()) start()
      val current = bullets.last()
      when {
        BULLET.containsMatchIn(line) -> current += line.replace(BULLET, "").trim()
        current.isNotEmpty() && continues(current.last(), line) ->
          current[current.lastIndex] = current.last() + " " + line
        else -> {
          if (current.isNotEmpty()) start()
          heads.last() += line
        }
      }
    }
    return heads.indices.map { entry(heads[it], bullets[it]) }
  }

  /** Whether [line] goes on from the bullet [previous] rather than starting a new entry. */
  private fun continues(previous: String, line: String): Boolean {
    if (line.first().isLowerCase() || line.first() in "(&,") return true
    if (previous.trimEnd().lastOrNull() in setOf('.', '!', '?', ';')) return false
    return !TRAILING_DATES.containsMatchIn(line) && line.length > 50
  }

  private fun entry(heads: List<String>, bullets: List<String>): DocEntry {
    if (heads.isEmpty()) return DocEntry(bullets = bullets)
    val (title, titleDates) = splitDates(heads[0])
    val (subtitle, subDates) = heads.getOrNull(1)?.let { splitDates(it) } ?: ("" to "")
    return DocEntry(
      title = title,
      dates = titleDates.ifEmpty { subDates },
      subtitle = subtitle,
      place = if (titleDates.isNotEmpty()) subDates else "",
      lines = heads.drop(2),
      bullets = bullets,
    )
  }

  /** [line] without the dates at its end, and those dates. */
  fun splitDates(line: String): Pair<String, String> {
    val match = TRAILING_DATES.find(line) ?: return line to ""
    val rest = line.substring(0, match.range.first).trim().trimEnd(',', '|', '·', '-', '–')
    return if (rest.isEmpty()) line to "" else rest.trim() to match.groupValues[1].trim()
  }

  fun fromLinkedIn(p: LinkedInProfile): ResumeDoc {
    val sections = buildList {
      val summary = p.summary.ifEmpty { p.headline }
      if (summary.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.SUMMARY,
            "Summary",
            paragraphs(summary.lines()).map { DocEntry(lines = listOf(it)) },
          )
        )
      }
      if (p.positions.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.EXPERIENCE,
            "Experience",
            p.positions.map {
              DocEntry(
                title = it.title,
                dates = range(it.startedOn, it.finishedOn, ongoing = true),
                subtitle = it.company,
                place = it.location,
                bullets = bulletsOf(it.description),
              )
            },
          )
        )
      }
      if (p.skills.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.SKILLS,
            "Skills",
            listOf(DocEntry(lines = listOf(p.skills.joinToString(", ")))),
          )
        )
      }
      if (p.projects.isNotEmpty()) {
        add(DocSection(PdfSection.PROJECTS, "Projects", p.projects.map { project(it) }))
      }
      if (p.education.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.EDUCATION,
            "Education",
            p.education.map {
              DocEntry(
                title = it.school,
                dates = range(it.startDate, it.endDate, ongoing = false),
                subtitle = it.degree,
                bullets = bulletsOf(it.notes) + bulletsOf(it.activities),
              )
            },
          )
        )
      }
      if (p.certifications.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.CERTIFICATIONS,
            "Certifications",
            listOf(DocEntry(lines = p.certifications.map { certification(it) })),
          )
        )
      }
      if (p.languages.isNotEmpty()) {
        val text =
          p.languages.joinToString(", ") {
            if (it.proficiency.isEmpty()) it.name else "${it.name} (${it.proficiency})"
          }
        add(DocSection(PdfSection.LANGUAGES, "Languages", listOf(DocEntry(lines = listOf(text)))))
      }
      if (p.honors.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.HONORS,
            "Honors and Awards",
            p.honors.map {
              DocEntry(title = it.title, dates = it.issuedOn, bullets = bulletsOf(it.description))
            },
          )
        )
      }
      if (p.courses.isNotEmpty()) {
        add(
          DocSection(
            PdfSection.COURSES,
            "Courses",
            listOf(DocEntry(lines = listOf(p.courses.joinToString(", ")))),
          )
        )
      }
    }
    return ResumeDoc(p.name, contactOf(p), sections)
  }

  fun contactOf(p: LinkedInProfile): List<String> =
    listOf(p.email, p.phone, p.location).filter { it.isNotEmpty() } + p.websites.take(2)

  fun project(it: LinkedInProfile.Project): DocEntry =
    DocEntry(
      title = it.title,
      dates = range(it.startedOn, it.finishedOn, ongoing = false),
      subtitle = it.url,
      bullets = bulletsOf(it.description),
    )

  fun certification(it: LinkedInProfile.Certification): String =
    listOf(it.name, it.authority, it.startedOn).filter { s -> s.isNotEmpty() }.joinToString(", ")

  private fun range(start: String, end: String, ongoing: Boolean): String =
    when {
      start.isEmpty() -> end
      end.isEmpty() -> if (ongoing) "$start – Present" else start
      else -> "$start – $end"
    }

  /** A LinkedIn description as bullets: its lines, or its sentences when it is one paragraph. */
  fun bulletsOf(description: String): List<String> {
    val lines = description.lines().map { it.replace(BULLET, "").trim() }.filter { it.isNotEmpty() }
    if (lines.size != 1 || lines[0].length < 220) return lines
    return lines[0]
      .split(Regex("(?<=[.!?])\\s+(?=[A-Z])"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }
}
