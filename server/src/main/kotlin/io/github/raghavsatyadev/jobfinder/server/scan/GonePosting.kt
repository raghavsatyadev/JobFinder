package io.github.raghavsatyadev.jobfinder.server.scan

import org.jsoup.Jsoup

/**
 * Tells a job page that says the posting is gone (removed, expired, filled) from a live one. A live
 * page may mention these words in a sidebar, so the words count only in the title, or on a short
 * page, or after an HTTP 404 or 410.
 */
object GonePosting {
  fun matches(page: PageRead): Boolean {
    if (page.httpStatus == HTTP_NOT_FOUND || page.httpStatus == HTTP_GONE) return true
    if (says(page.title.lowercase())) return true
    val text = runCatching { Jsoup.parse(page.html).body()?.text().orEmpty() }.getOrDefault("")
    return text.length <= SHORT_PAGE_CHARS && says(text.lowercase())
  }

  /** `true` when [text] (lowercase) says the posting is gone. */
  fun says(text: String): Boolean = PHRASES.any { it in text }

  /** The phrases as SQL LIKE patterns, to find stored descriptions of gone postings. */
  val likePatterns: List<String>
    get() = PHRASES.map { "%$it%" }

  private val PHRASES =
    listOf(
      "job posting has been removed",
      "job id provided may not be valid",
      "this job has expired",
      "this job is no longer available",
      "job is no longer available",
      "job posting is no longer available",
      "position is no longer available",
      "position has been filled",
      "job listing has expired",
      "this job has been closed",
      "the job you are looking for is no longer",
    )

  private const val HTTP_NOT_FOUND = 404
  private const val HTTP_GONE = 410
  /** A gone-posting page with its menus and footer; job pages with a description are longer. */
  private const val SHORT_PAGE_CHARS = 4_000
}
