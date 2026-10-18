package io.github.raghavsatyadev.jobfinder.ui.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * The phone's copy of the computer's data. Screens show it at once and read the computer in the
 * background. Android binds it in Koin; on desktop it is absent.
 */
interface PhoneCopy {
  /** `true` while the phone reads from or syncs with the computer; a thin bar shows on top. */
  val busy: StateFlow<Boolean>
}
