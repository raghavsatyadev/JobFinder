package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure

/**
 * A failure the client should see. Its [message] is written for the user and is sent as is; any
 * other exception is logged and answered with a generic message.
 */
class ApiException(code: ApiErrorCode, message: String = code.defaultMessage) :
  ApiFailure(code, message)

fun notFound(what: String): Nothing = throw ApiException(ApiErrorCode.NOT_FOUND, "$what not found.")
