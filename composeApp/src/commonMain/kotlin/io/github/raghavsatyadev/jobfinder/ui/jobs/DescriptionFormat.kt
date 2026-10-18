package io.github.raghavsatyadev.jobfinder.ui.jobs

/**
 * Tidies a job description's Markdown, as boards give it, for reading: each paragraph apart, a bold
 * line on its own ("**Qualifications:**") as a heading, run-together fields ("Job Title: … Job
 * Location: …") one per line, and bullets of any kind as one list.
 */
object DescriptionFormat {
  private val LABELS =
    "Job Title|Job Location|Location|Job Description|Description|Job Type|Employment Type|" +
      "Department|Salary|Experience|Shift|Work Mode|Workplace|Reports To|Job ID|Req ID"

  private val LABEL = Regex("""(?<![\w])($LABELS)\s*:""", RegexOption.IGNORE_CASE)

  /** `**Text:**`, `__Text__`, `***Text***` alone on a line. */
  private val EMPHASIS_LINE = Regex("""^(\*{1,3}|_{1,3})\s*([^*_].{0,70}?)\s*:?\s*\1\s*:?$""")
  private val BULLET = Regex("""^\s*([•·▪◦●○■□➢➤►–]|-|\*|\+)\s+""")
  private val NUMBERED = Regex("""^\s*\d{1,2}[.)]\s+""")
  private val EMPHASIS_MARKS = Regex("""\*{1,3}|(?<!\w)_{1,3}|_{1,3}(?!\w)""")
  /** A short "**Label:** value" line. */
  private val FIELD = Regex("""^\*\*[^*]{1,30}:\*\*\s+\S.{0,120}$""")
  private const val MAX_HEADING = 70

  fun tidy(markdown: String): String {
    val lines = mutableListOf<String>()
    for (raw in markdown.replace("\r\n", "\n").replace(' ', ' ').lines()) {
      lines += splitFields(raw.trimEnd())
    }
    val out = StringBuilder()
    var previous = Kind.BLANK
    for (line in lines) {
      val trimmed = line.trim()
      val kind =
        when {
          trimmed.isEmpty() -> Kind.BLANK
          trimmed.startsWith("#") -> Kind.HEADING
          BULLET.containsMatchIn(line) || NUMBERED.containsMatchIn(line) -> Kind.LIST
          FIELD.matches(trimmed) -> Kind.FIELD
          else -> Kind.TEXT
        }
      if (kind == Kind.BLANK) {
        previous = Kind.BLANK
        continue
      }
      val text =
        when (kind) {
          Kind.LIST ->
            if (NUMBERED.containsMatchIn(line)) trimmed
            else "- " + line.replaceFirst(BULLET, "").trim()
          Kind.TEXT -> heading(trimmed) ?: trimmed
          else -> trimmed
        }
      if (text == "-") continue
      val isHeading = text.startsWith("#")
      // A list stays together; everything else is its own block.
      if (out.isNotEmpty()) {
        out.append(
          when {
            kind == Kind.LIST && previous == Kind.LIST -> "\n"
            // Fields read as one block, a line each.
            kind == Kind.FIELD && previous == Kind.FIELD -> "  \n"
            else -> "\n\n"
          }
        )
      }
      out.append(text)
      previous = if (isHeading) Kind.HEADING else kind
    }
    return out.toString()
  }

  /** A short emphasised line on its own reads as a heading. */
  private fun heading(line: String): String? {
    val match = EMPHASIS_LINE.matchEntire(line) ?: return null
    val text = match.groupValues[2].trim().trimEnd(':').trim()
    if (text.isEmpty() || text.length > MAX_HEADING || text.endsWith(".")) return null
    return "### $text"
  }

  /** A line with two or more "Label:" fields becomes one line per field. */
  private fun splitFields(line: String): List<String> {
    val plain = line.replace(EMPHASIS_MARKS, "")
    val labels = LABEL.findAll(plain).toList()
    if (labels.size < 2) return listOf(line)
    val parts = mutableListOf<String>()
    val before = plain.substring(0, labels.first().range.first).trim()
    if (before.isNotEmpty()) parts += before
    labels.forEachIndexed { i, label ->
      val end = labels.getOrNull(i + 1)?.range?.first ?: plain.length
      val value = plain.substring(label.range.last + 1, end).trim()
      val name = label.groupValues[1]
      parts += if (value.isEmpty()) "**$name:**" else "**$name:** $value"
    }
    return parts
  }

  private enum class Kind {
    BLANK,
    TEXT,
    LIST,
    FIELD,
    HEADING,
  }
}
