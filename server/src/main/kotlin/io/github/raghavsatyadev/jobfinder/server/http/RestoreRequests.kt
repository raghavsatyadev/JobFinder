package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.model.BackupManifest
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A phone asking to replace everything on this computer with a backup. The desktop shows [pending]
 * and the user allows or denies it, the same as pairing. One request at a time; unanswered after
 * [ttl] it counts as denied.
 */
class RestoreRequests(private val ttl: Duration = 2.minutes) {
  /** A request waiting for the user: what the backup holds. */
  data class Pending(val id: String, val manifest: BackupManifest)

  private val _pending = MutableStateFlow<Pending?>(null)
  private var answer: CompletableDeferred<Boolean>? = null

  /** The request the desktop should show, or `null`. */
  val pending: StateFlow<Pending?> = _pending.asStateFlow()

  /** Waits for the user's answer to replacing everything with [manifest]'s backup. */
  suspend fun ask(manifest: BackupManifest): Boolean {
    val request = Pending(UUID.randomUUID().toString(), manifest)
    val decision = CompletableDeferred<Boolean>()
    synchronized(this) {
      if (_pending.value != null) {
        throw ApiException(
          ApiErrorCode.CONFLICT,
          "The computer is answering another request. Try again in a moment.",
        )
      }
      answer = decision
      _pending.value = request
    }
    try {
      return withTimeoutOrNull(ttl) { decision.await() } ?: false
    } finally {
      synchronized(this) {
        if (_pending.value?.id == request.id) {
          _pending.value = null
          answer = null
        }
      }
    }
  }

  /** The user allowed [id]. */
  fun approve(id: String) = decide(id, true)

  /** The user denied [id]. */
  fun deny(id: String) = decide(id, false)

  private fun decide(id: String, allowed: Boolean) {
    synchronized(this) {
      if (_pending.value?.id != id) return
      answer?.complete(allowed)
      _pending.value = null
      answer = null
    }
  }
}
