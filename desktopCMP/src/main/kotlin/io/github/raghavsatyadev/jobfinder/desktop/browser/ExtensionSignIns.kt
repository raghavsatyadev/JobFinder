package io.github.raghavsatyadev.jobfinder.desktop.browser

import io.github.raghavsatyadev.jobfinder.server.nativehost.CookiesResult
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeErrorCode
import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeHostLink
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCopy
import java.net.URI
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.jsonArray
import org.slf4j.LoggerFactory

/**
 * Copies the user's job board sign-ins from their everyday Chrome or Edge, through the JobFinder
 * Helper extension, into the scanner's browser profile. No second sign-in, and Google sign-in works
 * because the user signed in in their own browser.
 */
class ExtensionSignIns(
  private val link: NativeHostLink,
  private val runtime: CefRuntime,
  /** The origins of every job board, for example `https://www.linkedin.com`. */
  private val boardUrls: suspend () -> List<String>,
) {
  private val log = LoggerFactory.getLogger(ExtensionSignIns::class.java)

  suspend fun copy(): SignInCopy {
    if (!link.isConnected) return SignInCopy.NoExtension
    val sites = boardUrls().mapNotNull(::siteOf).distinct()
    if (sites.isEmpty()) return SignInCopy.Copied(0, emptyList())
    return when (val result = link.cookies(sites)) {
      is CookiesResult.Failed ->
        when (result.code) {
          NativeErrorCode.NOT_CONNECTED -> SignInCopy.NoExtension
          NativeErrorCode.NO_PERMISSION -> SignInCopy.SharingOff
          else -> SignInCopy.Failed(result.message)
        }
      is CookiesResult.Read -> {
        val params = CookieCopy.setCookiesParams(result.cookies)
        if (params != null) {
          runtime.awaitReady()
          HiddenBrowser.create(runtime).use { it.devTools("Storage.setCookies", params.toString()) }
          runInterruptible { runtime.flushCookies() }
        }
        val copied = params?.get("cookies")?.jsonArray?.size ?: 0
        log.info(
          "Copied {} sign-in cookies from the browser; no permission for {}",
          copied,
          result.missing,
        )
        SignInCopy.Copied(copied, result.missing)
      }
    }
  }

  private fun siteOf(url: String): String? = runCatching {
    URI(url).host
  }
    .getOrNull()
    ?.lowercase()
    ?.removePrefix("www.")
    ?.takeIf { '.' in it }
}
