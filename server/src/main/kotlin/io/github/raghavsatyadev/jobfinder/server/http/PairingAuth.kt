package io.github.raghavsatyadev.jobfinder.server.http

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.BaseResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.response.header
import io.ktor.server.response.respond
import java.security.MessageDigest

/** The principal of a request with the right pairing token. */
data object PairedClient

/**
 * Bearer auth against the pairing token. The token is read on every request, so a "re-pair" takes
 * effect at once. Tokens are compared in constant time (over their SHA-256, so the length does not
 * leak either). A failure answers 401 with the API error envelope.
 */
class PairingAuthProvider internal constructor(config: Config) : AuthenticationProvider(config) {
  private val token = config.token
  private val onSuccess = config.onSuccess

  class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
    internal var token: suspend () -> String = { error("pairing token supplier not set") }
    internal var onSuccess: () -> Unit = {}
  }

  override suspend fun onAuthenticate(context: AuthenticationContext) {
    val presented = bearerToken(context.call.request.headers[HttpHeaders.Authorization])
    if (presented != null && constantTimeEquals(presented, token())) {
      context.principal(name, PairedClient)
      onSuccess()
      return
    }
    val cause =
      if (presented == null) AuthenticationFailedCause.NoCredentials
      else AuthenticationFailedCause.InvalidCredentials
    context.challenge(CHALLENGE_KEY, cause) { challenge, call ->
      call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"jobfinder\"")
      call.respond(HttpStatusCode.Unauthorized, BaseResponse.error(ApiErrorCode.UNAUTHORIZED))
      challenge.complete()
    }
  }

  companion object {
    const val NAME = "pairing"
    private const val CHALLENGE_KEY = "PairingBearer"

    /** The token of an `Authorization: Bearer <token>` header, or `null`. */
    fun bearerToken(header: String?): String? {
      if (header == null) return null
      val parts = header.trim().split(Regex("\\s+"), limit = 2)
      if (parts.size != 2 || !parts[0].equals("Bearer", ignoreCase = true)) return null
      return parts[1].trim().takeIf { it.isNotEmpty() }
    }

    fun constantTimeEquals(a: String, b: String): Boolean {
      val sha = { s: String -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()) }
      return MessageDigest.isEqual(sha(a), sha(b))
    }
  }
}

/**
 * Registers [PairingAuthProvider] under [PairingAuthProvider.NAME]; [onSuccess] runs after each
 * request with the right token.
 */
fun AuthenticationConfig.pairing(token: suspend () -> String, onSuccess: () -> Unit = {}) {
  val config =
    PairingAuthProvider.Config(PairingAuthProvider.NAME).apply {
      this.token = token
      this.onSuccess = onSuccess
    }
  register(PairingAuthProvider(config))
}
