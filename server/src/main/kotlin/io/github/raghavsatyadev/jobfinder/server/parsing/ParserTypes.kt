package io.github.raghavsatyadev.jobfinder.server.parsing

import io.github.raghavsatyadev.jobfinder.shared.model.WorkMode

/**
 * A job card read from a search-results page (the old `ParsedJob`). The scanner adds the link id,
 * status and timestamps when it stores it. The old type also had `labels`, always `[]` from every
 * parser, so it is left out.
 */
data class ParsedJob(
  val siteId: Long,
  val externalId: String,
  val externalUrl: String,
  val title: String,
  val companyName: String,
  val companyLogo: String? = null,
  val location: String? = null,
  val salary: String? = null,
  /** The old `jobType`. */
  val workMode: WorkMode? = null,
  /** The old code stored `tags ?? []`, so "no tags" is an empty list. */
  val tags: List<String> = emptyList(),
  /** Only a few boards (Robert Half, FlexJobs) put the description on the list page. */
  val description: String? = null,
)

/** Tokens spent by an LLM-backed parser (the custom parser). No cost: everything is local. */
data class LlmUsage(val inputTokens: Long, val outputTokens: Long)

/** The old `JobSiteParseResult`, plus the dispatcher outcome. */
data class JobListParseResult(
  val jobs: List<ParsedJob>,
  /** The results container (or a "no results" marker) was found on the page. */
  val listFound: Boolean,
  /** Job cards found in the container, parsed or not. */
  val elementsCount: Int,
  val llmUsage: LlmUsage? = null,
  /** The dispatcher did not parse at all (deprecated board); not a failure. */
  val skipped: Boolean = false,
  /** Why the dispatcher could not parse (no parser for the board); counts as a failure. */
  val error: String? = null,
) {
  /**
   * The old `parseJobsListUrl` rule: no list on the page, or cards found but none parsed. The
   * scanner then bumps `scrape_failure_count` and may save an html dump.
   */
  val parseFailed: Boolean
    get() = !skipped && (error != null || !listFound || (elementsCount > 0 && jobs.isEmpty()))

  companion object {
    /** No results container on the page. */
    val NOT_FOUND = JobListParseResult(jobs = emptyList(), listFound = false, elementsCount = 0)

    /** The board's "no results" marker: an empty list, not a failure. */
    val EMPTY = JobListParseResult(jobs = emptyList(), listFound = true, elementsCount = 0)

    /** A deprecated board: the old backend returned no jobs and no failure. */
    val SKIPPED =
      JobListParseResult(jobs = emptyList(), listFound = true, elementsCount = 0, skipped = true)
  }
}

/** The old `JobDescriptionUpdates`: fields a job page can add to a stored job. */
data class JobDescriptionUpdates(
  /** Markdown. */
  val description: String? = null,
  val salary: String? = null,
  /** `null` when the page adds no tags (the old code returned `undefined`). */
  val tags: List<String>? = null,
) {
  companion object {
    val NONE = JobDescriptionUpdates()
  }
}

/**
 * Data the browser captured besides the DOM (the old `WebPageRuntimeData`). LinkedIn removes its
 * `<script id="rehydrate-data">` during hydration, so the fetcher grabs the raw script text from
 * the network response and passes it here.
 */
data class PageRuntimeData(val linkedInComoRehydration: String? = null)

/** What a list parser gets. */
data class ListParseInput(
  val siteId: Long,
  val html: String,
  /** The search URL that was loaded (only the custom LLM parser reads it). */
  val url: String = "",
  val runtimeData: PageRuntimeData? = null,
)

/** Reads job cards from a board's search-results HTML. Pure and synchronous: no I/O. */
fun interface JobListParser {
  fun parse(input: ListParseInput): JobListParseResult
}

/** Reads the description (and sometimes salary and tags) from a board's job page HTML. */
fun interface JobDescriptionParser {
  fun parse(html: String): JobDescriptionUpdates
}
