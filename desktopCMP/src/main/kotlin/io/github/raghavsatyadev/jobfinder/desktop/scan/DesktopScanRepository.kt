package io.github.raghavsatyadev.jobfinder.desktop.scan

import io.github.raghavsatyadev.jobfinder.desktop.ui.PickedPages
import io.github.raghavsatyadev.jobfinder.server.scan.Scanner
import io.github.raghavsatyadev.jobfinder.server.scan.ServerEvents
import io.github.raghavsatyadev.jobfinder.server.service.ApiException
import io.github.raghavsatyadev.jobfinder.server.service.LinkService
import io.github.raghavsatyadev.jobfinder.shared.api.ApiErrorCode
import io.github.raghavsatyadev.jobfinder.shared.api.CreateLinkResponse
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.Job
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.ScanState
import io.github.raghavsatyadev.jobfinder.shared.repository.ScanRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.slf4j.LoggerFactory

/**
 * The desktop UI's [ScanRepository]: the server's [Scanner], [ServerEvents] and [LinkService] in
 * process, with no HTTP round trip. A search added from the desktop browser is parsed from the page
 * the user picked ([PickedPages]). Failures reach the UI as [ScanFailedException] with a message
 * the user can read; "not available" (the scanner before the scan pipeline exists) is one of them,
 * never a crash.
 */
class DesktopScanRepository(
  private val scanner: Scanner,
  serverEvents: ServerEvents,
  private val links: LinkService,
  private val picked: PickedPages = PickedPages(),
) : ScanRepository {
  private val log = LoggerFactory.getLogger(DesktopScanRepository::class.java)

  override val state: StateFlow<ScanState> = scanner.state

  override val events: Flow<ServerEvent> = serverEvents.events

  override suspend fun startScanAll(): Boolean = guard("scan all") { scanner.startScanAll() }

  override suspend fun scanLink(linkId: Long): List<Long> =
    guard("scan link $linkId") { scanner.scanLink(linkId) }

  override suspend fun scanJob(jobId: Long): Job =
    guard("scan job $jobId") { scanner.scanJob(jobId) }

  override suspend fun createLink(
    title: String,
    url: String,
    force: Boolean,
    siteName: String?,
  ): CreateLinkResponse =
    guard("create link") {
      // The page the user picked in the add-search browser, as they saw it; else it is loaded.
      links.createLink(title, url, force, picked.get(url), siteName).also { picked.clear() }
    }

  override suspend fun brokenLinks(): List<Link> =
    guard("list broken links") { links.brokenLinks() }

  private suspend fun <T> guard(what: String, block: suspend () -> T): T =
    try {
      block()
    } catch (e: ApiException) {
      log.info("{} refused: {} ({})", what, e.message, e.code)
      throw ScanFailedException(e.code, e.message ?: e.code.defaultMessage, e)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.error("{} failed", what, e)
      throw ScanFailedException(ApiErrorCode.INTERNAL, ApiErrorCode.INTERNAL.defaultMessage, e)
    }
}

/**
 * A scan call that failed. [code] says why ([ApiErrorCode.UNAVAILABLE] while scanning is not
 * available, [ApiErrorCode.CONFLICT] for a page without a job list); [message] is for the user.
 */
class ScanFailedException(val code: ApiErrorCode, message: String, cause: Throwable? = null) :
  IllegalStateException(message, cause)
