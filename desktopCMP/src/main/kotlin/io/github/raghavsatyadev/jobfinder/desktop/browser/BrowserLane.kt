package io.github.raghavsatyadev.jobfinder.desktop.browser

import io.github.raghavsatyadev.jobfinder.server.scan.UrgentRead
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import org.slf4j.LoggerFactory

/**
 * Browsers of one kind with a resizable limit: each is created on first use and reused, and one
 * that stays idle for [idleCloseMs] is closed on [reaper].
 */
internal class BrowserLane<B : AutoCloseable>(
  size: Int,
  private val idleCloseMs: Long,
  private val reaper: ScheduledExecutorService,
  private val clock: () -> Long = System::nanoTime,
  private val create: suspend () -> B,
) {
  private val limit = MutableStateFlow(size)
  private val busy = MutableStateFlow(0)
  private val idle = ConcurrentLinkedDeque<B>()
  private val all = CopyOnWriteArrayList<B>()
  private val idleSince = ConcurrentHashMap<B, Long>()

  init {
    require(size > 0) { "Pool size must be positive, was $size" }
  }

  /** How many browsers are open, busy or idle. */
  val open: Int
    get() = all.size

  suspend fun <T> use(block: suspend (B) -> T): T {
    acquire()
    val browser =
      try {
        idle.pollFirst() ?: create().also { all += it }
      } catch (e: Throwable) {
        release()
        throw e
      }
    try {
      return block(browser)
    } finally {
      // Also closes the browser an urgent read opened past the limit.
      if (all.size > limit.value) {
        remove(browser)
      } else {
        idleSince[browser] = clock()
        idle.addFirst(browser)
        reaper.schedule({ closeIfIdle(browser) }, idleCloseMs, TimeUnit.MILLISECONDS)
      }
      release()
    }
  }

  fun resize(size: Int) {
    limit.value = size
    while (all.size > size) {
      val extra = idle.pollLast() ?: break
      remove(extra)
    }
    log.info("Scan browsers: up to {}", size)
  }

  fun close() {
    all.forEach { it.close() }
    all.clear()
    idle.clear()
    idleSince.clear()
  }

  /** Closes [browser] if it has been idle since the reuse that scheduled this check. */
  internal fun closeIfIdle(browser: B) {
    val since = idleSince[browser] ?: return
    if (clock() - since < TimeUnit.MILLISECONDS.toNanos(idleCloseMs)) return
    // Fails when a scan took it meanwhile; it is checked again when it comes back.
    if (idle.remove(browser)) {
      remove(browser)
      log.info("Closed a scan browser idle for {} s", idleCloseMs / 1000)
    }
  }

  private fun remove(browser: B) {
    all.remove(browser)
    idleSince.remove(browser)
    browser.close()
  }

  /** An [UrgentRead] may take one browser past the limit. */
  private suspend fun acquire() {
    val extra = if (currentCoroutineContext()[UrgentRead.Key] != null) 1 else 0
    while (true) {
      val taken = busy.value
      if (taken < limit.value + extra) {
        if (busy.compareAndSet(taken, taken + 1)) return
      } else {
        combine(busy, limit) { b, l -> b < l + extra }.first { it }
      }
    }
  }

  private fun release() = busy.update { it - 1 }

  private companion object {
    val log = LoggerFactory.getLogger(BrowserLane::class.java)
  }
}
