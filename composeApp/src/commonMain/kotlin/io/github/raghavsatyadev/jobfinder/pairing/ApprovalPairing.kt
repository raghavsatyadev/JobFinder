package io.github.raghavsatyadev.jobfinder.pairing

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.BaseResponse
import io.github.raghavsatyadev.jobfinder.shared.api.HealthResponse
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestBody
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestCreated
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestState
import io.github.raghavsatyadev.jobfinder.shared.api.PairingRequestStatus
import io.github.raghavsatyadev.jobfinder.ui.platform.PairingError
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * Pairing by approval, the phone's side: ask the computer, show the same 6 digits as its dialog,
 * and wait until the user allows or denies it there. [client] makes an HTTP client that reaches
 * only the given address (through [PairedHost]). [nextCode] gives a random number below its
 * argument, from a secure source.
 */
class ApprovalPairing(
  private val client: (LanIp) -> HttpClient,
  private val deviceName: () -> String,
  private val nextCode: (Int) -> Int,
) {
  sealed interface Outcome {
    data class Approved(val desktop: PairedDesktop) : Outcome

    data class Failed(val error: PairingError) : Outcome
  }

  /** The computer's `/health`, or `null` when nothing (or not JobFinder) answers. */
  suspend fun health(ip: LanIp, port: Int): HealthResponse? =
    try {
      client(ip).use { health(it, base(port)) }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }

  suspend fun request(ip: LanIp, port: Int, onCode: (String) -> Unit): Outcome =
    client(ip).use { http ->
      val base = base(port)
      val health = health(http, base) ?: return Outcome.Failed(PairingError.DESKTOP_DID_NOT_ANSWER)
      val code = newCode()
      val created =
        try {
          http
            .post("$base/pair/requests") {
              contentType(ContentType.Application.Json)
              setBody(
                ApiJson.encodeToString(
                  PairingRequestBody.serializer(),
                  PairingRequestBody(deviceName(), code),
                )
              )
            }
            .decode(PairingRequestCreated.serializer())
        } catch (e: CancellationException) {
          throw e
        } catch (e: Refused) {
          val error =
            if (e.code == ApiErrorCode.CONFLICT) PairingError.BUSY
            else PairingError.DESKTOP_DID_NOT_ANSWER
          return Outcome.Failed(error)
        } catch (_: Exception) {
          return Outcome.Failed(PairingError.DESKTOP_DID_NOT_ANSWER)
        }
      onCode(code)
      try {
        await(http, base, created.requestId, ip, port, health)
      } catch (e: CancellationException) {
        // The user gave up: close the dialog on the computer.
        withContext(NonCancellable) {
          runCatching { http.delete("$base/pair/requests/${created.requestId}") }
        }
        throw e
      }
    }

  private suspend fun await(
    http: HttpClient,
    base: String,
    id: String,
    ip: LanIp,
    port: Int,
    health: HealthResponse,
  ): Outcome {
    var misses = 0
    repeat(MAX_POLLS) {
      val status =
        try {
          http
            .get("$base/pair/requests/$id?wait=$WAIT_S")
            .decode(PairingRequestStatus.serializer())
            .also { misses = 0 }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          if (++misses >= MAX_MISSES) return Outcome.Failed(PairingError.DESKTOP_DID_NOT_ANSWER)
          null
        }
      when (status?.state) {
        null,
        PairingRequestState.PENDING -> Unit
        PairingRequestState.APPROVED -> {
          val token = status.token ?: return Outcome.Failed(PairingError.EXPIRED)
          return Outcome.Approved(PairedDesktop(ip, port, token, health.id, health.name))
        }
        PairingRequestState.DENIED -> return Outcome.Failed(PairingError.DENIED)
        PairingRequestState.EXPIRED -> return Outcome.Failed(PairingError.EXPIRED)
      }
    }
    return Outcome.Failed(PairingError.EXPIRED)
  }

  private suspend fun health(http: HttpClient, base: String): HealthResponse? =
    try {
      ApiJson.decodeFromString(HealthResponse.serializer(), http.get("$base/health").bodyAsText())
        .takeIf { it.app == APP }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }

  /** Six digits, leading zeros kept. */
  private fun newCode(): String = nextCode(CODE_RANGE).toString().padStart(CODE_LENGTH, '0')

  private fun base(port: Int) = "http://${PairedHost.NAME}:$port"

  /** The computer answered with an error. */
  private class Refused(val code: ApiErrorCode) : Exception()

  private suspend fun <T> io.ktor.client.statement.HttpResponse.decode(data: KSerializer<T>): T {
    val response =
      try {
        ApiJson.decodeFromString(BaseResponse.serializer(data), bodyAsText())
      } catch (_: SerializationException) {
        throw Refused(ApiErrorCode.INTERNAL)
      }
    if (!response.success) throw Refused(response.code ?: ApiErrorCode.INTERNAL)
    return response.data ?: throw Refused(ApiErrorCode.INTERNAL)
  }

  private companion object {
    const val APP = "jobfinder"
    const val CODE_LENGTH = 6
    const val CODE_RANGE = 1_000_000
    const val WAIT_S = 25

    /** 8 × 25 s outlasts the computer's 2-minute expiry. */
    const val MAX_POLLS = 8
    const val MAX_MISSES = 3
  }
}
