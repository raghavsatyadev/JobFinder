package io.github.raghavsatyadev.jobfinder.ui.jobs

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.AtsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.GhostStatus
import io.github.raghavsatyadev.jobfinder.shared.model.InsightsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.JobLabel
import io.github.raghavsatyadev.jobfinder.shared.model.JobReport
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.Note
import io.github.raghavsatyadev.jobfinder.shared.model.TipsStatus
import io.github.raghavsatyadev.jobfinder.shared.model.TranslationStatus
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.GhostRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.InsightsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobReportRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.NotesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TipsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.TranslationRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One job: its description, labels and notes. */
@Immutable
data class JobDetailUiState(
  val jobId: Long,
  /** The list's copy first (no description), then the full job. */
  val job: Job? = null,
  val loading: Boolean = true,
  val failed: Boolean = false,
  /** The job is gone (deleted elsewhere, or a stale link). */
  val notFound: Boolean = false,
  val descriptionLoading: Boolean = false,
  val descriptionFailed: Boolean = false,
  val siteName: String? = null,
  val siteLogoKey: String? = null,
  val linkTitle: String? = null,
  /** `null` until loaded. */
  val notes: ImmutableList<Note>? = null,
  /** A status or label change is being saved. */
  val busy: Boolean = false,
  /** The ATS review, or why there is none; `null` until asked. */
  val ats: AtsStatus? = null,
  val atsFailed: Boolean = false,
  /** The company's ratings and salary; `null` until asked or without a computer to read them. */
  val insights: InsightsStatus? = null,
  val insightsFailed: Boolean = false,
  /** The ghost-job check; `null` until asked or without a computer to read careers sites. */
  val ghost: GhostStatus? = null,
  val ghostFailed: Boolean = false,
  /** Negotiation tips; `null` until asked or without a computer to write them. */
  val tips: TipsStatus? = null,
  val tipsFailed: Boolean = false,
  /** The translation into the user's language, or why there is none; `null` until asked. */
  val translation: TranslationStatus? = null,
  /** The user chose to see the job as posted. */
  val showOriginal: Boolean = false,
  /** The user's section order; the app's own until read. */
  val layout: DetailLayout = DetailLayout.DEFAULT,
  /** "Shouldn't be in New" can be asked (a computer that runs the AI is there). */
  val canReport: Boolean = false,
  /** The report dialog; `null` while closed. */
  val report: ReportUi? = null,
)

class JobDetailViewModel(
  private val jobId: Long,
  preview: Job?,
  private val jobs: JobsRepository,
  private val notes: NotesRepository,
  private val sites: SitesRepository,
  private val links: LinksRepository,
  private val scan: ScanRepository,
  private val changes: JobChanges,
  private val ats: AtsRepository,
  private val insights: InsightsRepository? = null,
  private val ghost: GhostRepository? = null,
  private val tips: TipsRepository? = null,
  private val settings: SettingsRepository? = null,
  private val reports: JobReportRepository? = null,
  private val translations: TranslationRepository? = null,
) : ViewModel() {
  private val _state =
    MutableStateFlow(JobDetailUiState(jobId = jobId, job = preview, canReport = reports != null))
  val state: StateFlow<JobDetailUiState> = _state.asStateFlow()

  private val _effects = Channel<JobDetailEffect>(Channel.BUFFERED)
  val effects: Flow<JobDetailEffect> = _effects.receiveAsFlow()

  init {
    load()
    viewModelScope.launch { loadLayout() }
    viewModelScope.launch {
      scan.events.collect { event ->
        when (event) {
          is ServerEvent.AtsScored -> if (event.jobId == jobId) loadAts()
          ServerEvent.ResumeChanged -> loadAts()
          is ServerEvent.InsightsReady -> if (jobId in event.jobIds) loadInsights()
          is ServerEvent.GhostChecked -> if (jobId in event.jobIds) loadGhost()
          is ServerEvent.TipsReady -> if (event.jobId == jobId) loadTips()
          is ServerEvent.TranslationReady -> if (event.jobId == jobId) loadTranslation()
          ServerEvent.DetailLayoutChanged -> loadLayout()
          // The phone showed its copy and the computer had something newer.
          ServerEvent.CopyRefreshed -> readAgain()
          else -> Unit
        }
      }
    }
    viewModelScope.launch {
      changes.flow.collect { change ->
        when (change) {
          is JobChange.StatusChanged ->
            if (change.jobId == jobId)
              _state.update { s -> s.copy(job = s.job?.copy(status = change.to)) }
          is JobChange.Updated ->
            if (change.jobId == jobId)
              _state.update { s ->
                s.copy(job = change.job.copy(description = s.job?.description))
              }
          is JobChange.BulkChanged ->
            if (_state.value.job?.status == change.from)
              _state.update { s -> s.copy(job = s.job?.copy(status = change.to)) }
        }
      }
    }
  }

  fun onEvent(event: JobDetailEvent) {
    when (event) {
      JobDetailEvent.Retry -> load()
      is JobDetailEvent.ChangeStatus -> changeStatus(event.to)
      is JobDetailEvent.SetLabel -> setLabel(event.label)
      JobDetailEvent.ToggleStar ->
        _state.value.job?.let { saveLabels(it.labelsStarred(!it.starred)) }
      JobDetailEvent.RefreshDescription -> refreshDescription()
      JobDetailEvent.RetryAts -> viewModelScope.launch { loadAts() }
      JobDetailEvent.RetryInsights -> viewModelScope.launch { loadInsights(retry = true) }
      JobDetailEvent.RetryGhost -> viewModelScope.launch { loadGhost(retry = true) }
      JobDetailEvent.WriteTips -> viewModelScope.launch { loadTips(write = true) }
      JobDetailEvent.ToggleOriginal -> _state.update { it.copy(showOriginal = !it.showOriginal) }
      JobDetailEvent.RetryTranslation -> viewModelScope.launch { loadTranslation(retry = true) }
      is JobDetailEvent.AddNote -> addNote(event.text)
      is JobDetailEvent.EditNote -> editNote(event.id, event.text)
      is JobDetailEvent.DeleteNote -> deleteNote(event.id)
      JobDetailEvent.OpenReport -> _state.update { it.copy(report = ReportUi()) }
      is JobDetailEvent.SetReportNote ->
        _state.update { s ->
          s.copy(report = s.report?.copy(note = event.note.take(JobReport.MAX_NOTE)))
        }
      JobDetailEvent.SendReport -> sendReport()
      JobDetailEvent.CloseReport -> _state.update { it.copy(report = null) }
      JobDetailEvent.MoveAnyway -> {
        _state.update { it.copy(report = null) }
        changeStatus(JobStatus.EXCLUDED_BY_ADVANCED_MATCHING)
      }
    }
  }

  private fun load() {
    _state.update { it.copy(loading = true, failed = false) }
    viewModelScope.launch {
      try {
        val job = jobs.getJob(jobId)
        if (job == null) {
          _state.update { it.copy(loading = false, notFound = true) }
          return@launch
        }
        _state.update { it.copy(job = job, loading = false) }
        launch { loadNotes() }
        launch { loadSource(job) }
        launch { loadInsights() }
        launch { loadGhost() }
        launch { loadTips() }
        launch { loadTranslation() }
        // Like the old app: a job without a description is read again from its board.
        if (job.description.isNullOrBlank()) refreshDescription() else launch { loadAts() }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(loading = false, failed = true) }
      }
    }
  }

  /** Reads the job and its parts again, keeping what is on show meanwhile. */
  private suspend fun readAgain() {
    if (_state.value.loading) return
    try {
      val job = jobs.getJob(jobId) ?: return
      _state.update { s -> s.copy(job = job.copy(ats = job.ats ?: s.job?.ats)) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      return
    }
    loadNotes()
    loadAts()
    loadInsights()
    loadGhost()
    loadTips()
    loadTranslation()
  }

  private suspend fun loadNotes() {
    try {
      val list = notes.listNotes(jobId)
      _state.update { it.copy(notes = list.toImmutableList()) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      _state.update { it.copy(notes = it.notes ?: emptyList<Note>().toImmutableList()) }
    }
  }

  /** Asks for the review; a queued one arrives later as an `AtsScored` event. */
  private suspend fun loadAts() {
    _state.update { it.copy(atsFailed = false) }
    try {
      val status = ats.requestReview(jobId)
      _state.update { s ->
        s.copy(ats = status, job = s.job?.copy(ats = status.review?.summary))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      _state.update { it.copy(atsFailed = true) }
    }
  }

  /** Asks for the company insights; a queued read arrives later as an `InsightsReady` event. */
  private suspend fun loadInsights(retry: Boolean = false) {
    val repository = insights ?: return
    _state.update { it.copy(insightsFailed = false) }
    try {
      val status = repository.requestInsights(jobId, retry)
      _state.update { it.copy(insights = status) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      _state.update { it.copy(insightsFailed = true) }
    }
  }

  /** Asks for the ghost-job check; a queued careers-site read arrives as a `GhostChecked` event. */
  private suspend fun loadGhost(retry: Boolean = false) {
    val repository = ghost ?: return
    _state.update { it.copy(ghostFailed = false) }
    try {
      val status = repository.requestGhostCheck(jobId, retry)
      _state.update { it.copy(ghost = status) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      _state.update { it.copy(ghostFailed = true) }
    }
  }

  /** Reads the tips kept for the job; [write] asks for new ones, which arrive as `TipsReady`. */
  private suspend fun loadTips(write: Boolean = false) {
    val repository = tips ?: return
    _state.update { it.copy(tipsFailed = false) }
    try {
      val status = repository.requestTips(jobId, write)
      _state.update { it.copy(tips = status) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      _state.update { it.copy(tipsFailed = true) }
    }
  }

  /** Asks for the translation; a queued one arrives later as a `TranslationReady` event. */
  private suspend fun loadTranslation(retry: Boolean = false) {
    val repository = translations ?: return
    try {
      val status = repository.requestTranslation(jobId, retry)
      _state.update { it.copy(translation = status) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // The original stays on show.
    }
  }

  /** A layout that can't be read leaves the one shown. */
  private suspend fun loadLayout() {
    val repository = settings ?: return
    try {
      val layout = repository.getDetailLayout()
      _state.update { it.copy(layout = layout) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {}
  }

  private suspend fun loadSource(job: Job) {
    try {
      val site = sites.listSites().firstOrNull { it.id == job.siteId }
      val link = job.linkId?.let { links.getLink(it) }
      _state.update {
        it.copy(siteName = site?.name, siteLogoKey = site?.logoKey, linkTitle = link?.title)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // The source line is optional.
    }
  }

  private fun refreshDescription() {
    if (_state.value.descriptionLoading) return
    _state.update { it.copy(descriptionLoading = true, descriptionFailed = false) }
    viewModelScope.launch {
      try {
        val job = scan.scanJob(jobId)
        _state.update { s ->
          s.copy(
            job = job.copy(status = s.job?.status ?: job.status),
            descriptionLoading = false,
          )
        }
        if (!job.description.isNullOrBlank()) loadAts()
        loadTranslation()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(descriptionLoading = false, descriptionFailed = true) }
      }
    }
  }

  private fun sendReport() {
    val report = _state.value.report ?: return
    val repository = reports ?: return
    if (report.checking) return
    _state.update { it.copy(report = report.copy(checking = true, failed = false)) }
    viewModelScope.launch {
      try {
        val result = repository.reportJob(jobId, report.note.trim().ifBlank { null })
        _state.update { s -> s.copy(report = s.report?.copy(checking = false, result = result)) }
        if (result.moved) {
          val from = _state.value.job?.status ?: JobStatus.NEW
          val to = JobStatus.EXCLUDED_BY_ADVANCED_MATCHING
          _state.update { s ->
            s.copy(job = s.job?.copy(status = to, excludeReason = result.reason))
          }
          changes.publish(JobChange.StatusChanged(jobId, from, to))
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { s -> s.copy(report = s.report?.copy(checking = false, failed = true)) }
      }
    }
  }

  private fun changeStatus(to: JobStatus) {
    val job = _state.value.job ?: return
    if (job.status == to || _state.value.busy) return
    _state.update { it.copy(busy = true) }
    viewModelScope.launch {
      try {
        jobs.updateStatus(jobId, to)
        _state.update { s -> s.copy(job = s.job?.copy(status = to), busy = false) }
        changes.publish(JobChange.StatusChanged(jobId, job.status, to))
        _effects.trySend(JobDetailEffect.Message(JobsMessage.StatusChanged(to)))
        if (to == JobStatus.DELETED) _effects.trySend(JobDetailEffect.Close)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { it.copy(busy = false) }
        _effects.trySend(JobDetailEffect.Message(JobsMessage.Failed))
      }
    }
  }

  /** One label besides the star, as in the old app. */
  private fun setLabel(label: JobLabel?) {
    val job = _state.value.job ?: return
    saveLabels(listOfNotNull(label) + job.labels.filter { it == JobLabel.STARRED })
  }

  private fun saveLabels(labels: List<JobLabel>) {
    val job = _state.value.job ?: return
    if (job.labels == labels) return
    // Show the new label at once; put the old one back if saving fails.
    _state.update { it.copy(job = job.copy(labels = labels)) }
    viewModelScope.launch {
      try {
        val updated = jobs.updateLabels(jobId, labels)
        if (updated != null) changes.publish(JobChange.Updated(updated))
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _state.update { s -> s.copy(job = s.job?.copy(labels = job.labels)) }
        _effects.trySend(JobDetailEffect.Message(JobsMessage.Failed))
      }
    }
  }

  private fun addNote(text: String) {
    if (text.isBlank()) return
    viewModelScope.launch {
      try {
        val note = notes.createNote(jobId, text.trim())
        _state.update { s -> s.copy(notes = (listOf(note) + s.notes.orEmpty()).toImmutableList()) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _effects.trySend(JobDetailEffect.Message(JobsMessage.Failed))
      }
    }
  }

  private fun editNote(id: Long, text: String) {
    if (text.isBlank()) return
    viewModelScope.launch {
      try {
        val note = notes.updateNote(id, text.trim()) ?: return@launch
        _state.update { s ->
          s.copy(notes = s.notes?.map { if (it.id == id) note else it }?.toImmutableList())
        }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _effects.trySend(JobDetailEffect.Message(JobsMessage.Failed))
      }
    }
  }

  private fun deleteNote(id: Long) {
    viewModelScope.launch {
      try {
        notes.deleteNote(id)
        _state.update { s -> s.copy(notes = s.notes?.filterNot { it.id == id }?.toImmutableList()) }
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        _effects.trySend(JobDetailEffect.Message(JobsMessage.Failed))
      }
    }
  }
}
