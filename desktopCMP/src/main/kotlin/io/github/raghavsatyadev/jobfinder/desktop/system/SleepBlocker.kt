package io.github.raghavsatyadev.jobfinder.desktop.system

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.slf4j.LoggerFactory

/**
 * Keeps Windows from sleeping while the app runs, so scheduled scans happen (the old app's
 * `powerSaveBlocker.start('prevent-app-suspension')`). The display may still turn off.
 *
 * `SetThreadExecutionState` holds only while the calling thread lives, so every call runs on one
 * dedicated thread. Does nothing on other systems.
 */
class SleepBlocker : AutoCloseable {
  private val log = LoggerFactory.getLogger(SleepBlocker::class.java)
  private val thread: ExecutorService = Executors.newSingleThreadExecutor {
    Thread(it, "sleep-blocker").apply { isDaemon = true }
  }
  @Volatile private var blocking = false

  /** Blocks sleep when [enabled], allows it again otherwise. */
  fun set(enabled: Boolean) {
    if (!Platform.isWindows() || enabled == blocking) return
    blocking = enabled
    thread.execute {
      val flags =
        if (enabled) WinBase.ES_CONTINUOUS or WinBase.ES_SYSTEM_REQUIRED else WinBase.ES_CONTINUOUS
      if (Kernel32.INSTANCE.SetThreadExecutionState(flags) == 0) {
        log.warn("SetThreadExecutionState failed")
      } else {
        log.info(if (enabled) "Sleep blocked while the app runs" else "Sleep allowed")
      }
    }
  }

  override fun close() {
    set(false)
    thread.shutdown()
    thread.awaitTermination(2, TimeUnit.SECONDS)
  }
}
