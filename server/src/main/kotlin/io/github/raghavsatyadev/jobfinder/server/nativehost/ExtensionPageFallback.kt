package io.github.raghavsatyadev.jobfinder.server.nativehost

import io.github.raghavsatyadev.jobfinder.server.scan.FallbackPage
import io.github.raghavsatyadev.jobfinder.server.scan.PageFallback
import org.slf4j.LoggerFactory

/** Scans read pages the hidden browser could not through the connected browser extension. */
class ExtensionPageFallback(private val link: NativeHostLink) : PageFallback {
  private val log = LoggerFactory.getLogger(ExtensionPageFallback::class.java)

  override suspend fun read(url: String): FallbackPage? {
    if (!link.isConnected) return null
    return when (val result = link.readPage(url, timeoutMs = READ_TIMEOUT_MS, includeHtml = true)) {
      is PageReadResult.Read ->
        result.page.html?.let { FallbackPage(it, result.page.finalUrl, result.page.title) }
      is PageReadResult.Failed -> {
        log.info("The browser extension could not read {}: {} {}", url, result.code, result.message)
        null
      }
    }
  }

  private companion object {
    const val READ_TIMEOUT_MS = 45_000L
  }
}
