package io.github.raghavsatyadev.jobfinder.ui.links

import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.ui.fake.SampleData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/** Fixed states of the Searches screens for previews and screenshot tests. */
object LinksPreviewData {
  /** The sample searches, newest first, as the screen shows them after loading. */
  fun list(
    edit: LinkEditState? = null,
    scanningLinkIds: Set<Long> = emptySet(),
    canOpenPages: Boolean = false,
    explaining: Link? = null,
    scan: ScanState = ScanState(lastFinishedAt = SampleData.NOW - 30 * 60_000),
  ): LinksUiState =
    LinksUiState(
      links = SampleData.links.sortedByDescending { it.id }.toImmutableList(),
      sites = SampleData.sites.associateBy { it.id }.toImmutableMap(),
      supportedSites = SampleData.sites.supported().toImmutableList(),
      loading = false,
      scan = scan,
      scanningLinkIds = persistentSetOf<Long>().addAll(scanningLinkIds),
      now = SampleData.NOW,
      canOpenPages = canOpenPages,
      edit = edit,
      explaining = explaining,
    )

  /** No searches yet: the empty state with the supported boards. */
  fun empty(): LinksUiState = list().copy(links = persistentListOf())

  /** The first load, still running. */
  fun loading(): LinksUiState = LinksUiState(now = SampleData.NOW)

  /** The first load failed with no reason from the server. */
  fun error(): LinksUiState = LinksUiState(now = SampleData.NOW, loading = false, loadFailed = true)

  /** The broken sample search. */
  val brokenLink: Link
    get() = SampleData.links.first { it.isBroken }

  /** The edit dialog of the first search, with its URL moved to another board. */
  fun editOtherBoard(): LinkEditState {
    val link = SampleData.links.first()
    return LinkEditState(
      link = link,
      url = "https://www.indeed.com/jobs?q=android",
      urlError = LinkUrls.validate("https://www.indeed.com/jobs?q=android", link.url),
    )
  }

  /** The add-search form, filled in. */
  fun addLink(
    saving: Boolean = false,
    conflictMessage: String? = null,
    titleMissing: Boolean = false,
    urlError: UrlError? = null,
    title: String = "Android, remote",
    url: String = "https://www.linkedin.com/jobs/search/?keywords=android&f_WT=2",
  ): AddLinkUiState =
    AddLinkUiState(
      title = title,
      url = url,
      saving = saving,
      conflictMessage = conflictMessage,
      titleMissing = titleMissing,
      urlError = urlError,
      sites = SampleData.sites.supported().toImmutableList(),
      allSites = SampleData.sites.toImmutableList(),
    )

  /** The add-search screen after a save that found [newJobs] jobs. */
  fun created(newJobs: Int, parseFailed: Boolean = false): AddLinkUiState =
    addLink()
      .copy(
        created =
          CreateLinkResponse(
            link = SampleData.links.first().copy(id = 9),
            newJobIds = List(newJobs) { 100L + it },
            parseFailed = parseFailed,
          )
      )
}
