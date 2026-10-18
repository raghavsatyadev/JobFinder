package io.github.raghavsatyadev.jobfinder.server.service

import io.github.raghavsatyadev.jobfinder.server.db.ServerDatabase
import io.github.raghavsatyadev.jobfinder.server.db.SiteEntity
import io.github.raghavsatyadev.jobfinder.server.db.SiteLogos
import io.github.raghavsatyadev.jobfinder.shared.model.BoardNames
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider
import java.net.URI
import org.slf4j.LoggerFactory

/**
 * Job boards the app has no parser for each get their own site (provider `custom`, read by the AI),
 * named by the user or after the web address, so jobs can be filtered by board like LinkedIn's. The
 * seeded "Custom Job Board" (the first custom site) stays as the catch-all that new boards start
 * from.
 */
class CustomBoards(
  private val db: ServerDatabase,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val log = LoggerFactory.getLogger(CustomBoards::class.java)

  /**
   * The board for [url]: an existing custom board on the same domain (renamed to [name] when one is
   * given), or a new one called [name] or after the domain.
   */
  suspend fun siteFor(url: String, name: String?): SiteEntity {
    val host = host(url) ?: return requireNotNull(catchAll())
    val label = BoardNames.domainLabel(host)
    val custom = db.sitesDao().listAll().filter { it.provider == SiteProvider.CUSTOM.wire }
    val catchAll = custom.minByOrNull { it.id }
    val wanted = name?.trim()?.take(MAX_NAME_CHARS)?.ifBlank { null }
    val existing = custom.firstOrNull { site ->
      site.id != catchAll?.id &&
        site.urls.any { u -> host(u)?.let(BoardNames::domainLabel) == label }
    }
    if (existing != null) {
      if (wanted != null && wanted != existing.name) db.sitesDao().rename(existing.id, wanted)
      return existing.copy(name = wanted ?: existing.name)
    }
    val site =
      SiteEntity(
        name = uniqueName(wanted ?: BoardNames.fromHost(host), custom.map { it.name }.toSet()),
        provider = SiteProvider.CUSTOM.wire,
        urls = listOf("https://$host/"),
        logoKey = SiteLogos.FALLBACK_KEY,
        incognitoSupport = catchAll?.incognitoSupport ?: false,
        createdAt = clock(),
      )
    val id = db.sitesDao().insert(site)
    log.info("New custom board {} for {}", site.name, host)
    return site.copy(id = id)
  }

  /** Gives each search still on the catch-all board its own board, with its jobs. */
  suspend fun splitCatchAll() {
    val catchAll = catchAll() ?: return
    for (link in db.linksDao().listAll().filter { it.siteId == catchAll.id }) {
      val site = siteFor(link.url, null)
      if (site.id == catchAll.id) continue
      db.linksDao().moveToSite(link.id, site.id)
      val jobs = db.jobsDao().moveToSite(link.id, catchAll.id, site.id)
      log.info("Search {} and {} jobs moved to the board {}", link.id, jobs, site.name)
    }
  }

  private suspend fun catchAll(): SiteEntity? =
    db.sitesDao().listAll().filter { it.provider == SiteProvider.CUSTOM.wire }.minByOrNull { it.id }

  private fun uniqueName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    return (2..MAX_SUFFIX).map { "$name $it" }.firstOrNull { it !in taken } ?: name
  }

  companion object {
    private const val MAX_NAME_CHARS = 60
    private const val MAX_SUFFIX = 99

    fun host(url: String): String? = runCatching {
      URI(url.trim()).host?.lowercase()?.removePrefix("www.")
    }
      .getOrNull()
  }
}
