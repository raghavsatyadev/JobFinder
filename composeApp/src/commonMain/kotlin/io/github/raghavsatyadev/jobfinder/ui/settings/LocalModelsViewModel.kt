package io.github.raghavsatyadev.jobfinder.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.model.ModelCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.HfQuant
import io.github.raghavsatyadev.jobfinder.ui.platform.HfRepo
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModel
import io.github.raghavsatyadev.jobfinder.ui.platform.LocalModels
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelFailure
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelProgress
import io.github.raghavsatyadev.jobfinder.ui.platform.ModelSource
import io.github.raghavsatyadev.jobfinder.ui.platform.PcProfile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the "Add a model" dialog takes a model from. */
enum class AddTab {
  LIBRARY,
  HUGGING_FACE,
  LINK,
}

/** The "Add a model" dialog. */
@Immutable
data class AddModelState(
  val tab: AddTab = AddTab.LIBRARY,
  val libraryName: String = "",
  val hfQuery: String = "",
  val hfSearching: Boolean = false,
  val hfResults: List<HfRepo> = emptyList(),
  /** The search found nothing, or failed. */
  val hfNothing: Boolean = false,
  val hfRepo: String? = null,
  val quants: List<HfQuant> = emptyList(),
  val quant: String? = null,
  val linkUrl: String = "",
  val linkSha: String = "",
) {
  /** What the dialog would add now, or `null` while something is missing. */
  fun source(): ModelSource? =
    when (tab) {
      AddTab.LIBRARY -> libraryName.trim().takeIf { it.isNotEmpty() }?.let(ModelSource::Library)
      AddTab.HUGGING_FACE -> {
        val repo = hfRepo
        val quant = quant
        if (repo != null && quant != null) ModelSource.HuggingFace(repo, quant) else null
      }
      AddTab.LINK ->
        linkUrl
          .trim()
          .takeIf { it.startsWith("https://") || it.startsWith("http://") }
          ?.let { ModelSource.Link(it, linkSha.trim().takeIf(String::isNotEmpty)) }
    }
}

/** A download waiting for the user's yes, with its size when known. */
data class PendingDownload(val source: ModelSource, val sizeBytes: Long?, val freeGb: Double?)

@Immutable
data class LocalModelsUiState(
  val loading: Boolean = true,
  /** `false` when Ollama does not answer. */
  val ollamaRunning: Boolean = true,
  val profile: PcProfile? = null,
  val models: List<LocalModel> = emptyList(),
  val add: AddModelState? = null,
  val confirmDownload: PendingDownload? = null,
  val confirmRemove: String? = null,
  /** What runs now (adding or checking), and how far it is. */
  val progress: ModelProgress? = null,
  /** The last check, shown as a result card until dismissed. */
  val result: ModelCheck? = null,
  val failure: ModelFailure? = null,
) {
  val busy: Boolean
    get() = progress != null && progress !is ModelProgress.Done && progress !is ModelProgress.Failed
}

sealed interface LocalModelsEvent {
  data object Refresh : LocalModelsEvent

  data object OpenAdd : LocalModelsEvent

  data object CloseAdd : LocalModelsEvent

  data class SetTab(val tab: AddTab) : LocalModelsEvent

  data class SetLibraryName(val name: String) : LocalModelsEvent

  data class SetHfQuery(val query: String) : LocalModelsEvent

  data object SearchHf : LocalModelsEvent

  data class PickRepo(val repo: String) : LocalModelsEvent

  data class PickQuant(val quant: String) : LocalModelsEvent

  data class SetLinkUrl(val url: String) : LocalModelsEvent

  data class SetLinkSha(val sha: String) : LocalModelsEvent

  /** Add what the dialog shows, or [source] (the suggested model). Asks first. */
  data class RequestAdd(val source: ModelSource? = null) : LocalModelsEvent

  data object ConfirmDownload : LocalModelsEvent

  data object DismissDownload : LocalModelsEvent

  data class Check(val model: String) : LocalModelsEvent

  data class RequestRemove(val model: String) : LocalModelsEvent

  data object ConfirmRemove : LocalModelsEvent

  data object DismissRemove : LocalModelsEvent

  data object Cancel : LocalModelsEvent

  data object DismissResult : LocalModelsEvent
}

/**
 * Settings → AI model → models on this computer: the PC's tier, what Ollama has, adding a model
 * from the Ollama library, Hugging Face or a link, and the accuracy check. Nothing downloads before
 * the user says yes to its size.
 */
class LocalModelsViewModel(private val models: LocalModels) : ViewModel() {
  private val _state = MutableStateFlow(LocalModelsUiState())
  val state: StateFlow<LocalModelsUiState> = _state.asStateFlow()

  private var work: Job? = null

  init {
    refresh()
    viewModelScope.launch {
      val profile = runCatching { models.profile() }.getOrNull()
      _state.update { it.copy(profile = profile) }
    }
  }

  fun onEvent(event: LocalModelsEvent) {
    when (event) {
      LocalModelsEvent.Refresh -> refresh()
      LocalModelsEvent.OpenAdd -> _state.update { it.copy(add = AddModelState()) }
      LocalModelsEvent.CloseAdd -> _state.update { it.copy(add = null) }
      is LocalModelsEvent.SetTab -> editAdd { it.copy(tab = event.tab) }
      is LocalModelsEvent.SetLibraryName -> editAdd { it.copy(libraryName = event.name) }
      is LocalModelsEvent.SetHfQuery -> editAdd { it.copy(hfQuery = event.query) }
      LocalModelsEvent.SearchHf -> searchHf()
      is LocalModelsEvent.PickRepo -> pickRepo(event.repo)
      is LocalModelsEvent.PickQuant -> editAdd { it.copy(quant = event.quant) }
      is LocalModelsEvent.SetLinkUrl -> editAdd { it.copy(linkUrl = event.url) }
      is LocalModelsEvent.SetLinkSha -> editAdd { it.copy(linkSha = event.sha) }
      is LocalModelsEvent.RequestAdd -> requestAdd(event.source ?: _state.value.add?.source())
      LocalModelsEvent.ConfirmDownload -> confirmDownload()
      LocalModelsEvent.DismissDownload -> _state.update { it.copy(confirmDownload = null) }
      is LocalModelsEvent.Check -> launchWork { models.check(event.model) }
      is LocalModelsEvent.RequestRemove -> _state.update { it.copy(confirmRemove = event.model) }
      LocalModelsEvent.ConfirmRemove -> remove()
      LocalModelsEvent.DismissRemove -> _state.update { it.copy(confirmRemove = null) }
      LocalModelsEvent.Cancel -> cancel()
      LocalModelsEvent.DismissResult ->
        _state.update { it.copy(result = null, failure = null, progress = null) }
    }
  }

  private fun editAdd(change: (AddModelState) -> AddModelState) = _state.update { state ->
    state.add?.let { state.copy(add = change(it)) } ?: state
  }

  private fun refresh() {
    viewModelScope.launch {
      val list = runCatching { models.installed() }.getOrNull()
      _state.update {
        it.copy(loading = false, ollamaRunning = list != null, models = list.orEmpty())
      }
    }
  }

  private fun searchHf() {
    val query = _state.value.add?.hfQuery?.trim().orEmpty()
    if (query.isEmpty()) return
    editAdd { it.copy(hfSearching = true, hfNothing = false, hfRepo = null, quants = emptyList()) }
    viewModelScope.launch {
      val found = runCatching { models.searchHuggingFace(query) }.getOrDefault(emptyList())
      editAdd { it.copy(hfSearching = false, hfResults = found, hfNothing = found.isEmpty()) }
    }
  }

  private fun pickRepo(repo: String) {
    editAdd { it.copy(hfRepo = repo, quants = emptyList(), quant = null, hfSearching = true) }
    viewModelScope.launch {
      val quants = runCatching { models.quants(repo) }.getOrDefault(emptyList())
      // Q4_K_M is the size the accuracy tests used; start from it when the repository has it.
      val suggested = quants.firstOrNull { it.name == SUGGESTED_QUANT }?.name
      editAdd { it.copy(quants = quants, quant = suggested, hfSearching = false) }
    }
  }

  private fun requestAdd(source: ModelSource?) {
    if (source == null || _state.value.busy) return
    viewModelScope.launch {
      val size = runCatching { models.size(source) }.getOrNull()
      _state.update {
        it.copy(confirmDownload = PendingDownload(source, size, it.profile?.freeDiskGb))
      }
    }
  }

  private fun confirmDownload() {
    val pending = _state.value.confirmDownload ?: return
    _state.update { it.copy(confirmDownload = null, add = null) }
    launchWork { models.add(pending.source) }
  }

  /** Runs [flow] as the one piece of work, showing its progress and its result. */
  private fun launchWork(flow: () -> Flow<ModelProgress>) {
    if (_state.value.busy) return
    _state.update {
      it.copy(progress = ModelProgress.Downloading(0, null), result = null, failure = null)
    }
    work = viewModelScope.launch {
      try {
        flow().collect { progress ->
          _state.update {
            when (progress) {
              is ModelProgress.Done -> it.copy(progress = progress, result = progress.check)
              is ModelProgress.Failed -> it.copy(progress = progress, failure = progress.failure)
              else -> it.copy(progress = progress)
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update {
          it.copy(
            progress = ModelProgress.Failed(ModelFailure.DOWNLOAD_FAILED),
            failure = ModelFailure.DOWNLOAD_FAILED,
          )
        }
      }
      refresh()
    }
  }

  private fun cancel() {
    work?.cancel()
    work = null
    _state.update { it.copy(progress = null) }
    refresh()
  }

  private fun remove() {
    val model = _state.value.confirmRemove ?: return
    _state.update { it.copy(confirmRemove = null) }
    viewModelScope.launch {
      runCatching { models.remove(model) }
      refresh()
    }
  }

  companion object {
    const val SUGGESTED_QUANT = "Q4_K_M"
  }
}
