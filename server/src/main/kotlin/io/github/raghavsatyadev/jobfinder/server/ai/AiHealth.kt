package io.github.raghavsatyadev.jobfinder.server.ai

import io.github.raghavsatyadev.jobfinder.shared.model.AiProblem
import io.github.raghavsatyadev.jobfinder.shared.model.AiProblemKind
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/**
 * Which model sources keep failing, for Settings and the desktop's warning. A refused key is a
 * problem at once; other errors after [FAILING_AFTER] in a row. Rate limits and quota never count:
 * the work just goes to the next model. One answer clears the source.
 */
class AiHealth(private val clock: () -> Long = System::currentTimeMillis) {
  private val log = LoggerFactory.getLogger(AiHealth::class.java)
  private val streaks = ConcurrentHashMap<String, AiProblem>()
  private val expired = ConcurrentHashMap<String, AiProblem>()
  private val _problems = MutableStateFlow<List<AiProblem>>(emptyList())

  /** The sources with a problem now, the oldest first. */
  val problems: StateFlow<List<AiProblem>> = _problems.asStateFlow()

  fun succeeded(routeId: String) {
    if (streaks.remove(routeId) != null) publish()
  }

  fun failed(routeId: String, name: String, error: Throwable) {
    val refused = error is AuthRefusedException
    val detail = error.message.orEmpty().take(DETAIL_CHARS)
    val before = streaks[routeId]
    val now =
      AiProblem(
        routeId = routeId,
        name = name,
        kind = if (refused) AiProblemKind.KEY_REFUSED else AiProblemKind.FAILING,
        detail = detail,
        since = before?.since ?: clock(),
        failures = (before?.failures ?: 0) + 1,
      )
    streaks[routeId] = now
    if (shown(now) && (before == null || !shown(before) || before.kind != now.kind)) {
      log.warn("{} has a problem: {} ({})", name, now.kind, detail)
    }
    publish()
  }

  /** [routeId]'s key expired ([at]); `null` [at] clears it once the date is moved. */
  fun expiry(routeId: String, name: String, at: Long?) {
    val changed =
      if (at == null) expired.remove(routeId) != null
      else
        expired.putIfAbsent(routeId, AiProblem(routeId, name, AiProblemKind.KEY_EXPIRED, "", at)) ==
          null
    if (changed) publish()
  }

  /** The key was removed: nothing more to say about it. */
  fun forget(routeId: String) {
    val a = streaks.remove(routeId)
    val b = expired.remove(routeId)
    if (a != null || b != null) publish()
  }

  private fun shown(p: AiProblem) =
    p.kind == AiProblemKind.KEY_REFUSED || p.failures >= FAILING_AFTER

  private fun publish() {
    _problems.value =
      (expired.values + streaks.values.filter(::shown))
        .distinctBy { it.routeId }
        .sortedBy { it.since }
  }

  private companion object {
    const val FAILING_AFTER = 3
    const val DETAIL_CHARS = 240
  }
}
