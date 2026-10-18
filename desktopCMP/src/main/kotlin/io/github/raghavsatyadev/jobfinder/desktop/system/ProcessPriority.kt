package io.github.raghavsatyadev.jobfinder.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import org.slf4j.LoggerFactory

/**
 * Runs the app below normal priority. Scans start Chromium processes, which inherit the class, so a
 * scan never takes CPU time from what the user is doing. Does nothing on other systems.
 */
object ProcessPriority {
  private val log = LoggerFactory.getLogger(ProcessPriority::class.java)

  fun lower() {
    if (!Platform.isWindows()) return
    val kernel = Kernel32.INSTANCE
    if (
      !kernel.SetPriorityClass(kernel.GetCurrentProcess(), Kernel32.BELOW_NORMAL_PRIORITY_CLASS)
    ) {
      log.warn("Could not lower the process priority")
    }
  }
}
