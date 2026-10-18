package io.github.raghavsatyadev.jobfinder.server.service.ats

import io.github.raghavsatyadev.jobfinder.shared.model.AtsKeyword
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordCategory
import io.github.raghavsatyadev.jobfinder.shared.model.KeywordMatch

/** A keyword the model took from the job description, with the synonyms that earn half credit. */
data class JobKeyword(val term: String, val category: KeywordCategory, val synonyms: List<String>)

/**
 * The reproducible part of the review: whether each keyword is in the resume. Conservative like a
 * real ATS: exact means the term itself (case, plural, hyphen and slash variants); partial means a
 * synonym; anything else is missing.
 */
object KeywordMatcher {
  /** Most keywords a review keeps. */
  const val MAX_KEYWORDS = 40

  /**
   * Keeps the keywords that really are in [jobText] (the model may invent some), once each, then
   * matches them against [resume].
   */
  fun match(keywords: List<JobKeyword>, jobText: String, resume: String): List<AtsKeyword> {
    val job = normalize(jobText)
    val cv = normalize(resume)
    val seen = mutableSetOf<String>()
    return keywords
      .asSequence()
      .filter { it.term.isNotBlank() }
      .filter { count(job, normalize(it.term)) > 0 }
      .filter { seen.add(normalize(it.term)) }
      .take(MAX_KEYWORDS)
      .map { keyword ->
        val exact = count(cv, normalize(keyword.term))
        if (exact > 0) {
          AtsKeyword(keyword.term, keyword.category, KeywordMatch.EXACT, count = exact)
        } else {
          val synonym =
            keyword.synonyms
              .filter { it.isNotBlank() }
              .map { it to count(cv, normalize(it)) }
              .firstOrNull { it.second > 0 }
          if (synonym != null) {
            AtsKeyword(
              keyword.term,
              keyword.category,
              KeywordMatch.PARTIAL,
              matchedAs = synonym.first,
              count = synonym.second,
            )
          } else {
            AtsKeyword(keyword.term, keyword.category, KeywordMatch.MISSING)
          }
        }
      }
      .toList()
  }

  /** `(exact + 0.5 × partial) / total`, as a whole percentage. */
  fun matchPercent(keywords: List<AtsKeyword>): Int {
    if (keywords.isEmpty()) return 0
    val credit = keywords.sumOf {
      when (it.match) {
        KeywordMatch.EXACT -> 1.0
        KeywordMatch.PARTIAL -> 0.5
        KeywordMatch.MISSING -> 0.0
      }
    }
    return Math.round(credit * 100 / keywords.size).toInt()
  }

  /** Lower case, with `-`, `_` and `/` as spaces and runs of spaces collapsed. */
  fun normalize(text: String): String =
    text.lowercase().replace(Regex("[\\-_/]"), " ").replace(Regex("\\s+"), " ").trim()

  /** Times [term] appears in [text] as whole words, allowing a plural `s` or `es`. */
  fun count(text: String, term: String): Int {
    if (term.isEmpty()) return 0
    val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?:e?s)?(?![\\p{L}\\p{N}+#])")
    return pattern.findAll(text).count()
  }
}
