package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.ui.platform.DesktopFeatures
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The edit dialog of one search. */
@Immutable
data class LinkEditState(
  val link: Link,
  val title: String = link.title,
  val url: String = link.url,
  val titleMissing: Boolean = false,
  val urlError: UrlError? = null,
  val saving: Boolean = false,
  val saveFailed: Boolean = false,
  /** The server's reason for a failed save, or `null` for the generic message. */
  val saveErrorMessage: String? = null,
)

/** The Searches screen. [links] is `null` until the first load finishes. */
@Immutable
data class LinksUiState(
  val links: ImmutableList<Link>? = null,
  val sites: ImmutableMap<Long, Site> = persistentMapOf(),
  val supportedSites: ImmutableList<Site> = persistentListOf(),
  val loading: Boolean = true,
  /** The first load failed: nothing to show but the error. */
  val loadFailed: Boolean = false,
  val loadErrorMessage: String? = null,
  /** A reload failed while older links are shown. */
  val stale: Boolean = false,
  val scan: ScanState = ScanState(),
  /** Searches being scanned one by one, from "scan again". */
  val scanningLinkIds: ImmutableSet<Long> = persistentSetOf(),
  /** The time relative times are shown against, epoch milliseconds. */
  val now: Long = 0,
  /** The desktop can open a search page in its sign-in browser. */
  val canOpenPages: Boolean = false,
  val edit: LinkEditState? = null,
  val pendingDelete: Link? = null,
  /** The broken search whose explanation is open. */
  val explaining: Link? = null,
  /** How the list is narrowed, ordered and grouped. */
  val view: LinksView = LinksView(),
) {
  /** [links] as [view] shows them; `null` until the first load. */
  val groups: List<LinkGroup>? by lazy { links?.let { view.arrange(it, sites) } }

  /** Boards that have searches, by name: the board filter's choices. */
  val boards: List<Site> by lazy {
    links
      .orEmpty()
      .map { it.siteId }
      .distinct()
      .mapNotNull { sites[it] }
      .sortedBy { it.name.lowercase() }
  }
}

sealed interface LinksEvent {
  data object Retry : LinksEvent

  data class StartEdit(val link: Link) : LinksEvent

  data class EditTitle(val title: String) : LinksEvent

  data class EditUrl(val url: String) : LinksEvent

  data object SaveEdit : LinksEvent

  data object CancelEdit : LinksEvent

  data class AskDelete(val link: Link) : LinksEvent

  data object ConfirmDelete : LinksEvent

  data object CancelDelete : LinksEvent

  data class Explain(val link: Link) : LinksEvent

  data object CloseExplain : LinksEvent

  /** Scans one search now: the retry of a broken search. */
  data class ScanLink(val link: Link) : LinksEvent

  /** Opens the page in the desktop sign-in browser, to sign in or solve a captcha. */
  data class OpenPage(val link: Link) : LinksEvent

  /** A new search text, board choice, order or grouping. */
  data class ChangeView(val view: LinksView) : LinksEvent
}

/** One-shot messages, shown as snackbars. */
sealed interface LinksEffect {
  data class Deleted(val title: String) : LinksEffect

  data class Updated(val title: String) : LinksEffect

  data class ScanFinished(val title: String, val newJobs: Int) : LinksEffect

  /** [message] is the server's reason, or `null` for the generic message. */
  data class Failed(val message: String?) : LinksEffect
}

/** Saved searches: list, scan state, broken-search warnings, edit and delete. */
class LinksViewModel(
  private val links: LinksRepository,
  private val sites: SitesRepository,
  private val scan: ScanRepository,
  private val desktop: DesktopFeatures?,
  private val now: () -> Long,
  private val viewStore: LinksViewStore = NoLinksViewStore,
) : ViewModel() {
  private val _state =
    MutableStateFlow(
      LinksUiState(
        now = now(),
        scan = scan.state.value,
        canOpenPages = desktop != null,
        view = viewStore.load(),
      )
    )
  val state: StateFlow<LinksUiState> = _state.asStateFlow()

  private val _effects = Channel<LinksEffect>(Channel.BUFFERED)
  val effects: Flow<LinksEffect> = _effects.receiveAsFlow()

  init {
    load()
    viewModelScope.launch {
      scan.state.collect { scanState ->
        val wasScanning = _state.value.scan.scanning
        _state.update { it.copy(scan = scanState, now = now()) }
        // A finished scan changed every "last checked" time and maybe the failure counts.
        if (wasScanning && !scanState.scanning) load()
      }
    }
    viewModelScope.launch {
      scan.events.collect {
        if (it is ServerEvent.BrokenLinks || it is ServerEvent.CopyRefreshed) load()
      }
    }
  }

  fun onEvent(event: LinksEvent) {
    when (event) {
      LinksEvent.Retry -> load()
      is LinksEvent.StartEdit -> _state.update { it.copy(edit = LinkEditState(event.link)) }
      is LinksEvent.EditTitle ->
        updateEdit { it.copy(title = event.title, titleMissing = false, saveFailed = false) }
      is LinksEvent.EditUrl ->
        updateEdit { it.copy(url = event.url, urlError = null, saveFailed = false) }
      LinksEvent.SaveEdit -> saveEdit()
      LinksEvent.CancelEdit -> _state.update { it.copy(edit = null) }
      is LinksEvent.AskDelete -> _state.update { it.copy(pendingDelete = event.link) }
      LinksEvent.ConfirmDelete -> confirmDelete()
      LinksEvent.CancelDelete -> _state.update { it.copy(pendingDelete = null) }
      is LinksEvent.Explain -> _state.update { it.copy(explaining = event.link) }
      LinksEvent.CloseExplain -> _state.update { it.copy(explaining = null) }
      is LinksEvent.ScanLink -> scanLink(event.link)
      is LinksEvent.OpenPage -> {
        _state.update { it.copy(explaining = null) }
        desktop?.openLoginBrowser(event.link.url)
      }
      is LinksEvent.ChangeView -> {
        _state.update { it.copy(view = event.view) }
        viewStore.save(event.view)
      }
    }
  }

  private fun load() {
    _state.update { it.copy(loading = true) }
    viewModelScope.launch {
      try {
        // Every time: a new search may have added a custom board.
        val siteList = sites.listSites()
        val list = links.listLinks()
        _state.update { s ->
          s.copy(
            links = list.toImmutableList(),
            sites = siteList.associateBy { it.id }.toImmutableMap(),
            supportedSites = siteList.supported().toImmutableList(),
            loading = false,
            loadFailed = false,
            loadErrorMessage = null,
            stale = false,
            now = now(),
          )
        }
      } catch (e: Exception) {
        e.rethrowIfCancelled()
        _state.update { s ->
          if (s.links == null) {
            s.copy(loading = false, loadFailed = true, loadErrorMessage = e.userMessage())
          } else {
            s.copy(loading = false, stale = true)
          }
        }
      }
    }
  }

  private fun updateEdit(change: (LinkEditState) -> LinkEditState) {
    _state.update { s -> s.copy(edit = s.edit?.let(change)) }
  }

  private fun saveEdit() {
    val edit = _state.value.edit ?: return
    if (edit.saving) return
    val title = edit.title.trim()
    val url = edit.url.trim()
    val urlError = LinkUrls.validate(url, originalUrl = edit.link.url)
    if (title.isEmpty() || urlError != null) {
      updateEdit { it.copy(titleMissing = title.isEmpty(), urlError = urlError) }
      return
    }
    updateEdit { it.copy(saving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        val updated = links.updateLink(edit.link.id, title, url)
        if (updated == null) {
          // Deleted elsewhere meanwhile.
          _state.update { it.copy(edit = null) }
          _effects.send(LinksEffect.Failed(null))
          load()
          return@launch
        }
        _state.update { s ->
          s.copy(
            edit = null,
            links = s.links?.map { if (it.id == updated.id) updated else it }?.toImmutableList(),
          )
        }
        _effects.send(LinksEffect.Updated(updated.title))
      } catch (e: Exception) {
        e.rethrowIfCancelled()
        updateEdit {
          it.copy(saving = false, saveFailed = true, saveErrorMessage = e.userMessage())
        }
      }
    }
  }

  private fun confirmDelete() {
    val link = _state.value.pendingDelete ?: return
    _state.update { it.copy(pendingDelete = null) }
    viewModelScope.launch {
      try {
        links.deleteLink(link.id)
        _state.update { s ->
          s.copy(links = s.links?.filterNot { it.id == link.id }?.toImmutableList())
        }
        _effects.send(LinksEffect.Deleted(link.title))
      } catch (e: Exception) {
        e.rethrowIfCancelled()
        _effects.send(LinksEffect.Failed(e.userMessage()))
      }
    }
  }

  private fun scanLink(link: Link) {
    if (link.id in _state.value.scanningLinkIds) return
    _state.update {
      it.copy(
        explaining = null,
        scanningLinkIds = (it.scanningLinkIds + link.id).toPersistentSet(),
      )
    }
    viewModelScope.launch {
      try {
        val newJobs = scan.scanLink(link.id)
        _effects.send(LinksEffect.ScanFinished(link.title, newJobs.size))
        load()
      } catch (e: Exception) {
        e.rethrowIfCancelled()
        _effects.send(LinksEffect.Failed(e.userMessage()))
      } finally {
        _state.update {
          it.copy(scanningLinkIds = (it.scanningLinkIds - link.id).toPersistentSet())
        }
      }
    }
  }
}
