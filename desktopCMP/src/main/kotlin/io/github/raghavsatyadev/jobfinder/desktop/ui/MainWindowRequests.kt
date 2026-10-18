package io.github.raghavsatyadev.jobfinder.desktop.ui

import io.github.raghavsatyadev.jobfinder.ui.navigation.TopLevel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks the main window to come forward, for example after a click on a notification, or the app to
 * quit from the tray menu.
 */
class MainWindowRequests {
  private val _shows = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP)
  private val _destinations =
    MutableSharedFlow<TopLevel>(extraBufferCapacity = 1, onBufferOverflow = DROP)
  private val _quits = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP)

  /** The window restores itself and comes to the front on each emission. */
  val shows: SharedFlow<Unit> = _shows.asSharedFlow()

  /** The UI switches to each emitted destination. */
  val destinations: SharedFlow<TopLevel> = _destinations.asSharedFlow()

  /** The app quits on an emission. */
  val quits: SharedFlow<Unit> = _quits.asSharedFlow()

  /** Brings the window forward, on [destination] when given. Never suspends. */
  fun show(destination: TopLevel? = null) {
    destination?.let { _destinations.tryEmit(it) }
    _shows.tryEmit(Unit)
  }

  /** Quits the app. Never suspends. */
  fun quit() {
    _quits.tryEmit(Unit)
  }

  private companion object {
    val DROP = BufferOverflow.DROP_OLDEST
  }
}
