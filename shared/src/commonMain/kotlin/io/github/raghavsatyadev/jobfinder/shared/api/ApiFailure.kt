package io.github.raghavsatyadev.jobfinder.shared.api

/**
 * A failure of an API call that clients can act on: [code] says what kind it is (for example
 * [ApiErrorCode.CONFLICT] when a search page shows no job list), and [message] is written for the
 * user. The server throws it in process on desktop; the Android client throws it for an error
 * [BaseResponse].
 */
open class ApiFailure(
  val code: ApiErrorCode,
  message: String = code.defaultMessage,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
