package io.github.raghavsatyadev.jobfinder.desktop.browser

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * An OS file lock that lets only one app process use the CEF profile at a time. A second process
 * would hit Chromium's profile lock, so it must exit instead. The OS drops the lock when the
 * process dies, so a crash never leaves a stale lock.
 */
class SingleInstanceLock private constructor(private val file: RandomAccessFile, lock: FileLock) :
  AutoCloseable {
  private var lock: FileLock? = lock

  override fun close() {
    lock?.release()
    lock = null
    file.close()
  }

  companion object {
    /** Takes the lock on [lockFile], or returns `null` when another process holds it. */
    fun tryAcquire(lockFile: File): SingleInstanceLock? {
      lockFile.parentFile?.mkdirs()
      val file = RandomAccessFile(lockFile, "rw")
      val channel: FileChannel = file.channel
      val lock =
        try {
          channel.tryLock()
        } catch (_: OverlappingFileLockException) {
          null
        } catch (_: IOException) {
          null
        }
      if (lock == null) {
        file.close()
        return null
      }
      file.setLength(0)
      file.writeBytes(ProcessHandle.current().pid().toString())
      return SingleInstanceLock(file, lock)
    }
  }
}
