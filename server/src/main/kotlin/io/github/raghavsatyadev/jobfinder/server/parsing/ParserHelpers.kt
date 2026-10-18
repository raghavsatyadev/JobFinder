package io.github.raghavsatyadev.jobfinder.server.parsing

import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode

/**
 * Small text helpers shared by the site parsers (old `parserHelpers.ts` and `jobListParser.ts`).
 */
object ParserHelpers {
  /** JS `\s`: WhiteSpace and LineTerminator code points, for regex character classes. */
  const val JS_SPACE_CLASS =
    "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

  private val SALARY_JUNK = Regex("[^0-9$€£\\-kK$JS_SPACE_CLASS]")
  private val LOCATION_JUNK = Regex("[^a-zA-Z0-9,$JS_SPACE_CLASS]")
  private val SALARY_PATTERN =
    Regex(
      "(?:[$€£]|\\b(?:usd|eur|gbp|salary)\\b|\\d+[$JS_SPACE_CLASS]*k\\b)",
      RegexOption.IGNORE_CASE,
    )
  private val SALARY_PHRASES = listOf("per year", "annually", "per annum", "per hour")
  private val DIGIT = Regex("\\d")

  /** Old `parseSalary`: keeps digits, currency symbols, dashes, spaces and `k`. */
  fun parseSalary(salary: String?): String? {
    if (salary.isNullOrEmpty()) return null
    return SALARY_JUNK.replace(salary.jsTrim(), "").jsTrim()
  }

  /** Old `parseLocation`: keeps letters, digits, spaces and commas (no final trim, as before). */
  fun parseLocation(location: String?): String? {
    if (location.isNullOrEmpty()) return null
    return LOCATION_JUNK.replace(location.jsTrim(), "")
  }

  /** Old `parserHelpers.isSalaryText`: currency or salary words, or a number with a pay period. */
  fun isSalaryText(text: String): Boolean {
    if (matchesSalaryPattern(text)) return true
    val lower = text.lowercase()
    return DIGIT.containsMatchIn(text) && SALARY_PHRASES.any { it in lower }
  }

  /** The regex half of [isSalaryText] (Hiring Cafe used only this part). */
  fun matchesSalaryPattern(text: String): Boolean = SALARY_PATTERN.containsMatchIn(text)

  private val WORK_MODE_MARKERS =
    listOf("(remote)", "(on-site)", "(hybrid)").map {
      Regex(Regex.escape(it), RegexOption.IGNORE_CASE)
    }

  /** Removes the first `(Remote)`, `(On-site)` and `(Hybrid)` marker (LinkedIn locations). */
  fun stripWorkModeMarkers(location: String): String =
    WORK_MODE_MARKERS.fold(location) { acc, regex -> regex.replaceFirst(acc, "") }

  /** `remote` anywhere wins, then `hybrid`, otherwise on-site. */
  fun workModeFromText(text: String?): WorkMode {
    val lower = text?.lowercase().orEmpty()
    return when {
      "remote" in lower -> WorkMode.REMOTE
      "hybrid" in lower -> WorkMode.HYBRID
      else -> WorkMode.ONSITE
    }
  }
}
