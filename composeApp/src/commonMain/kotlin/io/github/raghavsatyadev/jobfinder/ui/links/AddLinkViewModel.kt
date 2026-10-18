package io.github.raghavsatyadev.jobfinder.ui.links

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The add-search screen. */
@Immutable
data class AddLinkUiState(
  val title: String = "",
  val url: String = "",
  val titleMissing: Boolean = false,
  val urlError: UrlError? = null,
  /** The page is being loaded and checked for a job list. */
  val saving: Boolean = false,
  /**
   * The page showed no job list. Set, the next save forces it ("save anyway"). Holds the server's
   * reason, or an empty string when it gave none.
   */
  val conflictMessage: String? = null,
  val saveFailed: Boolean = false,
  val saveErrorMessage: String? = null,
  /** The saved search, once saving worked. */
  val created: CreateLinkResponse? = null,
  /** Boards to search on, by name. */
  val sites: ImmutableList<Site> = persistentListOf(),
  /** Every board, custom ones too: tells whether [url] is on a board the app has no parser for. */
  val allSites: ImmutableList<Site> = persistentListOf(),
  /** The name the user gives a custom board; blank takes [customBoard]. */
  val siteName: String = "",
  /** The desktop add-search browser is open. */
  val browsing: Boolean = false,
) {
  /** The board's name when [url] is on a board without a parser (asked for), else `null`. */
  val customBoard: String?
    get() = allSites.customBoardName(url)
}

sealed interface AddLinkEvent {
  data class TitleChanged(val title: String) : AddLinkEvent

  data class UrlChanged(val url: String) : AddLinkEvent

  data class SiteNameChanged(val name: String) : AddLinkEvent

  data object Save : AddLinkEvent

  /** Opens the desktop browser to pick a search page. */
  data object Browse : AddLinkEvent

  data class Picked(val url: String, val title: String) : AddLinkEvent

  data object CancelBrowse : AddLinkEvent

  /** Clears the form after a save, for the next search. */
  data object AddAnother : AddLinkEvent
}

/** Adds a search: checks the URL, loads the page, and offers "save anyway" when no jobs show. */
class AddLinkViewModel(private val scan: ScanRepository, private val sites: SitesRepository) :
  ViewModel() {
  private val _state = MutableStateFlow(AddLinkUiState())
  val state: StateFlow<AddLinkUiState> = _state.asStateFlow()

  init {
    viewModelScope.launch {
      try {
        val all = sites.listSites()
        _state.update {
          it.copy(sites = all.supported().toImmutableList(), allSites = all.toImmutableList())
        }
      } catch (e: Exception) {
        // The board list is only a hint; the form works without it.
        e.rethrowIfCancelled()
      }
    }
  }

  fun onEvent(event: AddLinkEvent) {
    when (event) {
      is AddLinkEvent.TitleChanged ->
        _state.update { it.copy(title = event.title, titleMissing = false, saveFailed = false) }
      is AddLinkEvent.UrlChanged ->
        _state.update {
          // Another page has to be checked again: "save anyway" was for the old one.
          it.copy(url = event.url, urlError = null, conflictMessage = null, saveFailed = false)
        }
      is AddLinkEvent.SiteNameChanged -> _state.update { it.copy(siteName = event.name) }
      AddLinkEvent.Save -> save()
      AddLinkEvent.Browse -> _state.update { it.copy(browsing = true) }
      is AddLinkEvent.Picked ->
        _state.update {
          it.copy(
            browsing = false,
            url = event.url,
            title = it.title.ifBlank { event.title.trim() },
            urlError = null,
            titleMissing = false,
            conflictMessage = null,
            saveFailed = false,
          )
        }
      AddLinkEvent.CancelBrowse -> _state.update { it.copy(browsing = false) }
      AddLinkEvent.AddAnother ->
        _state.update { AddLinkUiState(sites = it.sites, allSites = it.allSites) }
    }
  }

  private fun save() {
    val current = _state.value
    if (current.saving) return
    val title = current.title.trim()
    val url = current.url.trim()
    val urlError = LinkUrls.validate(url)
    if (title.isEmpty() || urlError != null) {
      _state.update { it.copy(titleMissing = title.isEmpty(), urlError = urlError) }
      return
    }
    val force = current.conflictMessage != null
    _state.update { it.copy(saving = true, saveFailed = false) }
    viewModelScope.launch {
      try {
        val siteName =
          current.siteName.trim().ifEmpty { null }?.takeIf { current.customBoard != null }
        val created = scan.createLink(title, url, force, siteName)
        _state.update { it.copy(saving = false, created = created, conflictMessage = null) }
      } catch (e: Exception) {
        e.rethrowIfCancelled()
        if (e is ApiFailure && e.code == ApiErrorCode.CONFLICT) {
          _state.update { it.copy(saving = false, conflictMessage = e.message.orEmpty()) }
        } else {
          _state.update {
            it.copy(saving = false, saveFailed = true, saveErrorMessage = e.userMessage())
          }
        }
      }
    }
  }
}
