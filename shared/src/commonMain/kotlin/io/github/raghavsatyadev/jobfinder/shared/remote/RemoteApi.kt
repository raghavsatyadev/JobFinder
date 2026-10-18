package io.github.raghavsatyadev.jobfinder.shared.remote

import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.api.BaseResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer

/** The paired desktop: where its API is and the token to send. */
data class RemoteEndpoint(val baseUrl: String, val token: String)

/** No desktop is paired (or it was unpaired), so there is nothing to call. */
class NotPairedException : ApiFailure(ApiErrorCode.UNAUTHORIZED)

/** The desktop could not be reached: off, asleep, on another network, or a timeout. */
class DesktopUnreachableException(cause: Throwable) :
  ApiFailure(
    ApiErrorCode.UNAVAILABLE,
    "Can't reach the desktop app. Is it running on this Wi-Fi?",
  ) {
  init {
    initCause(cause)
  }
}

/**
 * Calls the desktop's `/v1` API: sends the pairing token, unwraps [BaseResponse] and turns an error
 * response into [ApiFailure] with its code and message. [endpoint] is read on every call, so
 * pairing again takes effect at once.
 */
class RemoteApi(val http: HttpClient, private val endpoint: () -> RemoteEndpoint?) {
  /** The current endpoint, or [NotPairedException]. */
  fun requireEndpoint(): RemoteEndpoint = endpoint() ?: throw NotPairedException()

  suspend fun <T> call(
    method: HttpMethod,
    path: String,
    result: KSerializer<T>,
    configure: HttpRequestBuilder.() -> Unit = {},
  ): T {
    val target = requireEndpoint()
    val text =
      try {
        http
          .request {
            this.method = method
            url("${target.baseUrl.trimEnd('/')}/v1$path")
            bearerAuth(target.token)
            configure()
          }
          .bodyAsText()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw DesktopUnreachableException(e)
      }
    val response =
      try {
        ApiJson.decodeFromString(BaseResponse.serializer(result), text)
      } catch (e: SerializationException) {
        throw ApiFailure(ApiErrorCode.INTERNAL, "The desktop app sent an unexpected answer.")
      } catch (e: IllegalArgumentException) {
        throw ApiFailure(ApiErrorCode.INTERNAL, "The desktop app sent an unexpected answer.")
      }
    if (!response.success) {
      val code = response.code ?: ApiErrorCode.INTERNAL
      throw ApiFailure(code, response.message ?: code.defaultMessage)
    }
    @Suppress("UNCHECKED_CAST")
    return response.data ?: (Unit as? T) ?: throw ApiFailure(ApiErrorCode.INTERNAL)
  }

  suspend fun <T> get(
    path: String,
    result: KSerializer<T>,
    configure: HttpRequestBuilder.() -> Unit = {},
  ) = call(HttpMethod.Get, path, result, configure)

  suspend fun <B, T> send(
    method: HttpMethod,
    path: String,
    body: B,
    bodySerializer: KSerializer<B>,
    result: KSerializer<T>,
  ): T =
    call(method, path, result) {
      contentType(ContentType.Application.Json)
      setBody(ApiJson.encodeToString(bodySerializer, body))
    }

  suspend fun <T> post(path: String, result: KSerializer<T>): T =
    call(HttpMethod.Post, path, result)

  /**
   * A file the computer sends as is (not wrapped in [BaseResponse]), such as a backup zip. An error
   * still comes as a [BaseResponse] and is thrown as [ApiFailure].
   */
  suspend fun download(path: String): ByteArray {
    val target = requireEndpoint()
    val response =
      try {
        http.request {
          method = HttpMethod.Get
          url("${target.baseUrl.trimEnd('/')}/v1$path")
          bearerAuth(target.token)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw DesktopUnreachableException(e)
      }
    val bytes =
      try {
        response.bodyAsBytes()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw DesktopUnreachableException(e)
      }
    if (response.status.isSuccess()) return bytes
    val error =
      try {
        ApiJson.decodeFromString(BaseResponse.serializer(Unit.serializer()), bytes.decodeToString())
      } catch (_: Exception) {
        null
      }
    val code = error?.code ?: ApiErrorCode.INTERNAL
    throw ApiFailure(code, error?.message ?: code.defaultMessage)
  }

  suspend fun delete(path: String) {
    call(HttpMethod.Delete, path, Unit.serializer())
  }
}
