package io.github.raghavsatyadev.jobfinder.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import org.slf4j.LoggerFactory

/**
 * Runs the app below normal priority. Scans start Chromium processes, which inherit the class (on
 * macOS, the nice value), so a scan never takes CPU time from what the user is doing. Does nothing
 * on other systems.
 */
object ProcessPriority {
  private val log = LoggerFactory.getLogger(ProcessPriority::class.java)

  fun lower() {
    if (Platform.isMac()) return lowerMac()
    if (!Platform.isWindows()) return
    val kernel = Kernel32.INSTANCE
    if (
      !kernel.SetPriorityClass(kernel.GetCurrentProcess(), Kernel32.BELOW_NORMAL_PRIORITY_CLASS)
    ) {
      log.warn("Could not lower the process priority")
    }
  }

  /** `renice`: a process may always lower its own priority. */
  private fun lowerMac() {
    val pid = ProcessHandle.current().pid().toString()
    runCatching {
      val process =
        ProcessBuilder("/usr/bin/renice", "-n", MAC_NICE, "-p", pid)
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .start()
      check(process.waitFor() == 0) { "renice ended with ${process.exitValue()}" }
    }
      .onFailure { log.warn("Could not lower the process priority", it) }
  }

  /** Like Windows' below-normal class: a little behind what the user runs. */
  private const val MAC_NICE = "5"
}
