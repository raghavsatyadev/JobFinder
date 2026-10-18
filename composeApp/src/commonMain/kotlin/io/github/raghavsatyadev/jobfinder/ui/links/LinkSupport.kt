package io.github.raghavsatyadev.jobfinder.ui.links

import io.github.raghavsatyadev.jobfinder.shared.api.ApiFailure
import io.github.raghavsatyadev.jobfinder.shared.api.ServerEvent
import io.github.raghavsatyadev.jobfinder.shared.model.BoardNames
import io.github.raghavsatyadev.jobfinder.shared.model.Link
import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import io.github.raghavsatyadev.jobfinder.ui.fake.FakeBackend
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** Why a search URL was rejected. */
sealed interface UrlError {
  /** Not a full `http(s)://host/...` address. */
  data object Invalid : UrlError

  /** An edit moved the search to another board; it must stay on [host]. */
  data class OtherHost(val host: String) : UrlError
}

/** URL checks shared by the add and edit forms. */
object LinkUrls {
  /** The lower-case host of [url], or `null` when it is not a full http(s) address. */
  fun host(url: String): String? {
    val text = url.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val rest =
      when {
        text.startsWith("https://", ignoreCase = true) -> text.substring("https://".length)
        text.startsWith("http://", ignoreCase = true) -> text.substring("http://".length)
        else -> return null
      }
    val authority =
      rest.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
    val host = authority.substringBefore(':').lowercase()
    val looksLikeHost =
      host.isNotEmpty() &&
        (host.contains('.') || host == "localhost") &&
        !host.startsWith('.') &&
        !host.endsWith('.')
    return host.takeIf { looksLikeHost }
  }

  /** The host a board is known by: [host] without a leading `www.`. */
  fun siteHost(host: String): String = host.removePrefix("www.")

  /** Checks a new [url] for a search; [originalUrl] (an edit) keeps it on the same board. */
  fun validate(url: String, originalUrl: String? = null): UrlError? {
    val host = host(url) ?: return UrlError.Invalid
    val originalHost = originalUrl?.let(::host)?.let(::siteHost) ?: return null
    return if (siteHost(host) == originalHost) null else UrlError.OtherHost(originalHost)
  }
}

/** True when the last [ServerEvent.BROKEN_LINK_THRESHOLD] or more scans of the link failed. */
val Link.isBroken: Boolean
  get() = scrapeFailureCount >= ServerEvent.BROKEN_LINK_THRESHOLD

/** Boards a user can add a search for, by name. The catch-all custom board is left out. */
fun List<Site>.supported(): List<Site> = filter {
  !it.deprecated && it.logoKey != CUSTOM_SITE_KEY
}
  .sortedBy { it.name.lowercase() }

internal const val CUSTOM_SITE_KEY = "custom"

/**
 * For a search on a board the app has no parser for: the name the board has, or would get from
 * [url]. `null` when a supported board serves the address, or the address is not complete.
 */
fun List<Site>.customBoardName(url: String): String? {
  val host = LinkUrls.host(url)?.let(LinkUrls::siteHost) ?: return null
  val label = BoardNames.domainLabel(host)
  val match = firstOrNull { site ->
    site.urls.any { u -> LinkUrls.host(u)?.let(BoardNames::domainLabel) == label }
  }
  if (match != null && match.provider != SiteProvider.CUSTOM) return null
  val catchAll = filter { it.provider == SiteProvider.CUSTOM }.minByOrNull { it.id }
  return match?.takeIf { it.id != catchAll?.id }?.name ?: BoardNames.fromHost(host)
}

/**
 * The message to show for a failed call: the server's own text for an [ApiFailure] (written for the
 * user), or `null` for the generic message.
 */
internal fun Throwable.userMessage(): String? = (this as? ApiFailure)?.message

/** Rethrows cancellation, so a `catch (e: Exception)` never swallows it. */
internal fun Throwable.rethrowIfCancelled() {
  if (this is CancellationException) throw this
}

/**
 * The clock the screens show relative times against: the fake's fixed clock in previews and tests,
 * so screenshots never change, and the system clock otherwise.
 */
internal fun screenClock(fake: FakeBackend?): () -> Long =
  if (fake != null) ({ fake.now() }) else ({ Clock.System.now().toEpochMilliseconds() })
