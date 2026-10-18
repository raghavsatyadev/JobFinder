package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Settings → Pause AI work ([ScannerSettings.aiPaused]): the background workers wait while it is
 * on. Work for a job the user opened goes on.
 */
class AiWorkPause(val paused: StateFlow<Boolean>) {
  constructor(
    settings: Flow<ScannerSettings>,
    scope: CoroutineScope,
  ) : this(settings.map { it.aiPaused }.stateIn(scope, SharingStarted.Eagerly, false))

  /** Returns at once while not paused, else once the user resumes. */
  suspend fun awaitResumed() {
    paused.first { !it }
  }

  companion object {
    /** Never paused (tests, tools). */
    val NEVER = AiWorkPause(MutableStateFlow(false))
  }
}
