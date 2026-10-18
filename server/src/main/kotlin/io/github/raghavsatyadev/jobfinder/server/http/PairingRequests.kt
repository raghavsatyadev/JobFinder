package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestState
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestStatus
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pairing by approval. A phone on the network asks to pair; the desktop shows [pending] and the
 * user allows or denies it. Only an allowed request gets the token, and only once. One request at a
 * time, each open for [ttl]. The phone's code lets the user check that the request on screen comes
 * from the phone in their hand.
 */
class PairingRequests(
  private val token: suspend () -> String,
  private val ttl: Duration = 2.minutes,
  private val minGap: Duration = 3.seconds,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  /** A request waiting for the user. */
  data class Pending(val id: String, val deviceName: String, val code: String, val createdAt: Long)

  private data class Entry(val request: Pending, val state: PairingRequestState)

  private val entries = MutableStateFlow<Map<String, Entry>>(emptyMap())
  private val _pending = MutableStateFlow<Pending?>(null)
  private var lastRequestAt = Long.MIN_VALUE / 2

  /** The request the desktop should show, or `null`. */
  val pending: StateFlow<Pending?> = _pending.asStateFlow()

  /** Opens a request. Refuses one while another is open, and requests too close together. */
  fun open(deviceName: String, code: String): String {
    val name = deviceName.trim().take(MAX_NAME)
    if (name.isEmpty()) throw ApiException(ApiErrorCode.BAD_REQUEST, "The phone has no name.")
    if (!CODE.matches(code)) {
      throw ApiException(ApiErrorCode.BAD_REQUEST, "The code must be 6 digits.")
    }
    synchronized(this) {
      expire()
      val now = clock()
      if (_pending.value != null || now - lastRequestAt < minGap.inWholeMilliseconds) {
        throw ApiException(
          ApiErrorCode.CONFLICT,
          "The computer is answering another request. Try again in a moment.",
        )
      }
      lastRequestAt = now
      val request = Pending(UUID.randomUUID().toString(), name, code, now)
      entries.value += request.id to Entry(request, PairingRequestState.PENDING)
      _pending.value = request
      return request.id
    }
  }

  /** The user allowed [id]. */
  fun approve(id: String) = decide(id, PairingRequestState.APPROVED)

  /** The user denied [id]. */
  fun deny(id: String) = decide(id, PairingRequestState.DENIED)

  /** The phone gave up on [id]; the desktop stops showing it. */
  fun cancel(id: String) {
    synchronized(this) {
      entries.value -= id
      if (_pending.value?.id == id) _pending.value = null
    }
  }

  /** Expires a request the user did not answer in time; call it now and then. */
  fun expireOld() = synchronized(this) { expire() }

  /**
   * The state of [id], waiting up to [wait] while it is pending. An approved request returns the
   * token on this first read and is then forgotten.
   */
  suspend fun await(id: String, wait: Duration): PairingRequestStatus {
    withTimeoutOrNull(wait) {
      entries.first { it[id]?.state != PairingRequestState.PENDING }
    }
    val entry =
      synchronized(this) {
        expire()
        val found = entries.value[id]
        if (found != null && found.state != PairingRequestState.PENDING) entries.value -= id
        found
      } ?: return PairingRequestStatus(PairingRequestState.EXPIRED)
    return when (entry.state) {
      PairingRequestState.APPROVED -> PairingRequestStatus(PairingRequestState.APPROVED, token())
      else -> PairingRequestStatus(entry.state)
    }
  }

  private fun decide(id: String, state: PairingRequestState) {
    synchronized(this) {
      val entry = entries.value[id] ?: return
      if (entry.state != PairingRequestState.PENDING) return
      entries.value += id to entry.copy(state = state)
      if (_pending.value?.id == id) _pending.value = null
    }
  }

  /** Marks requests older than [ttl] expired and drops answers nobody read. */
  private fun expire() {
    val now = clock()
    val limit = ttl.inWholeMilliseconds
    val current = entries.value
    val next =
      current
        .filterValues { now - it.request.createdAt < limit * 2 }
        .mapValues { (_, e) ->
          if (e.state == PairingRequestState.PENDING && now - e.request.createdAt >= limit) {
            e.copy(state = PairingRequestState.EXPIRED)
          } else {
            e
          }
        }
    if (next != current) entries.value = next
    val shown = _pending.value
    if (shown != null && next[shown.id]?.state != PairingRequestState.PENDING) _pending.value = null
  }

  private companion object {
    const val MAX_NAME = 60
    val CODE = Regex("\\d{6}")
  }
}
