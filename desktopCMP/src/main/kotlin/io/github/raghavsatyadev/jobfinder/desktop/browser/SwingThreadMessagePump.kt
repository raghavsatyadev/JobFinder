package io.github.raghavsatyadev.jobfinder.desktop.browser

import com.sun.jna.Platform
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Processes the Swing thread's Windows messages while a visible browser is open.
 *
 * JCEF runs CEF's UI thread on the Swing thread, so a visible browser's native windows belong to
 * it, while the Compose window around them belongs to AWT's toolkit thread. Windows then shares one
 * input queue between the two threads, and the Swing thread only reads its messages when CEF
 * schedules work. Until it does, every click and key press in the whole window waits and the cursor
 * shows "busy". This timer reads them every [INTERVAL_MS].
 */
internal object SwingThreadMessagePump {
  private const val INTERVAL_MS = 10
  private const val PM_REMOVE = 0x0001
  private var users = 0
  private val timer = Timer(INTERVAL_MS) { pump() }

  /** Starts pumping for one more browser. Call on the Swing thread. */
  fun acquire() {
    if (!Platform.isWindows()) return
    check(SwingUtilities.isEventDispatchThread()) { "Call on the Swing thread" }
    if (users++ == 0) timer.start()
  }

  /** Stops pumping once the last browser is gone. Call on the Swing thread. */
  fun release() {
    if (!Platform.isWindows() || users == 0) return
    if (--users == 0) timer.stop()
  }

  private fun pump() {
    val message = WinUser.MSG()
    while (User32.INSTANCE.PeekMessage(message, null, 0, 0, PM_REMOVE)) {
      User32.INSTANCE.TranslateMessage(message)
      User32.INSTANCE.DispatchMessage(message)
    }
  }
}
