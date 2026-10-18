package io.github.raghavsatyadev.jobfinder.server.parsing

import io.github.raghavsatyadev.jobfinder.shared.model.Site
import io.github.raghavsatyadev.jobfinder.shared.model.SiteProvider

/** A URL the app cannot save as a search link. [message] is shown to the user. */
class JobSiteException(message: String) : IllegalArgumentException(message)

/** The board for a URL, and the URL as it is saved. */
data class CleanJobUrl(val cleanUrl: String, val site: Site)

/**
 * Maps search URLs to boards (old `getJobSite` and `cleanJobUrl` in `jobListParser.ts`).
 *
 * Everything is unlocked in the rebuild, so the old `hasCustomJobsParsing` flag is always true:
 * unknown boards go to the custom (LLM) site instead of failing with the "PRO plan" error.
 */
object JobSites {
  /**
   * The board for [url]: the first site whose `urls` share the URL's second-level domain label
   * (`www.linkedin.com` and `in.linkedin.com` are both `linkedin`), else the custom site.
   *
   * @throws JobSiteException when the path is one of the board's blacklisted paths (a home page,
   *   not a search), or when no custom site exists.
   * @throws IllegalArgumentException when [url] is not a valid URL.
   */
  fun getJobSite(allSites: List<Site>, url: String): Site {
    val parsed = WebUrl.parse(url)
    val urlDomain = domainLabel(parsed)
    val site = allSites.firstOrNull { site ->
      site.urls.any { siteUrl ->
        runCatching { domainLabel(WebUrl.parse(siteUrl)) }.getOrNull() == urlDomain
      }
    }

    site?.blacklistedPaths?.forEach { path ->
      val pathname = parsed.pathname.lowercase()
      val blacklisted = path.lowercase()
      if (pathname == blacklisted || "$pathname/" == blacklisted) {
        throw JobSiteException(
          "Looks like the URL you are trying to save does not contain any search params. Make " +
            "sure to configure your desired filters like role/tech stach, location, etc. on " +
            "${parsed.hostname} and try again. Optionally add the last 24h filter when possible."
        )
      }
    }

    return site
      ?: allSites.firstOrNull { it.provider == SiteProvider.CUSTOM }
      ?: throw JobSiteException("No custom site found")
  }

  /**
   * The URL to save for [url]: the board's `queryParamsToRemove` are dropped (which re-encodes the
   * query the way `URLSearchParams` does), and LinkedIn searches are forced to `sortBy=DD` (most
   * recent first).
   */
  fun cleanJobUrl(allSites: List<Site>, url: String): CleanJobUrl {
    val site = getJobSite(allSites, url)
    var cleanUrl = url

    // The old `if (site.queryParamsToRemove)` skipped NULL columns; no seeded site has `[]`.
    if (site.queryParamsToRemove.isNotEmpty()) {
      val parsed = WebUrl.parse(url)
      site.queryParamsToRemove.forEach { parsed.searchParams.delete(it) }
      cleanUrl = parsed.toString()
    }

    if (site.provider == SiteProvider.LINKEDIN) {
      val parsed = WebUrl.parse(cleanUrl)
      parsed.searchParams.set("sortBy", "DD")
      cleanUrl = parsed.toString()
    }

    return CleanJobUrl(cleanUrl, site)
  }

  /**
   * Old `getUrlDomain`: drop the first `emplois.`, reverse the labels, keep the last three of the
   * reversed list (the old `shift()` loop) and return the second: `www.linkedin.com` and
   * `www.glassdoor.co.uk` give `linkedin` and `glassdoor`, `hiring.cafe` gives `hiring`.
   */
  private fun domainLabel(url: WebUrl): String? {
    val labels = url.hostname.replaceFirst("emplois.", "").split('.').reversed()
    return labels.takeLast(3).getOrNull(1)
  }
}
