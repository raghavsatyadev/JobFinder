package io.github.raghavsatyadev.jobfinder.desktop.browser

import io.github.raghavsatyadev.jobfinder.ui.platform.SignInCheck
import io.github.raghavsatyadev.jobfinder.ui.platform.SignInPage
import java.net.URI
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Looks at one saved search page per job site in the scanner's own browser profile, the way a scan
 * would: a site that sends it to a sign-in page needs a sign-in. LinkedIn shows guests a search
 * page too, so there its session cookie decides.
 */
class SignInChecker(
  private val runtime: CefRuntime,
  /** One saved search page per job site. */
  private val searchPages: suspend () -> List<String>,
) {
  private val log = LoggerFactory.getLogger(SignInChecker::class.java)

  suspend fun check(): SignInCheck {
    val pages = searchPages().mapNotNull { url -> siteOf(url)?.let { SignInPage(it, url) } }
    if (pages.isEmpty()) return SignInCheck.NoSearches
    runtime.awaitReady()
    val (ready, needed) =
      HiddenBrowser.create(runtime).use { browser -> pages.partition { signedIn(browser, it) } }
    log.info("Sign-in check: ready {}, needed {}", ready.map { it.site }, needed.map { it.site })
    return SignInCheck.Checked(ready, needed)
  }

  private suspend fun signedIn(browser: HiddenBrowser, page: SignInPage): Boolean {
    if (page.site.endsWith(LINKEDIN))
      return hasCookie(browser, "https://www.$LINKEDIN", LINKEDIN_SESSION)
    return try {
      val loaded = browser.load(page.url, LOAD_TIMEOUT)
      val landed = browser.url ?: loaded.finalUrl
      SIGN_IN_PATHS.none { landed.lowercase().contains(it) }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.warn("Could not look at {}", page.url, e)
      true
    }
  }

  private suspend fun hasCookie(browser: HiddenBrowser, url: String, name: String): Boolean =
    try {
      val reply = browser.devTools("Network.getCookies", """{"urls":["$url"]}""")
      Json.parseToJsonElement(reply).jsonObject["cookies"]?.jsonArray.orEmpty().any {
        it.jsonObject["name"]?.jsonPrimitive?.content == name
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      log.warn("Could not read the cookies of {}", url, e)
      false
    }

  private fun siteOf(url: String): String? = runCatching {
    URI(url).host
  }
    .getOrNull()
    ?.lowercase()
    ?.removePrefix("www.")
    ?.takeIf { '.' in it }

  private companion object {
    const val LINKEDIN = "linkedin.com"
    const val LINKEDIN_SESSION = "li_at"
    val LOAD_TIMEOUT = 20.seconds
    /** Where job sites send a visitor who is not signed in. */
    val SIGN_IN_PATHS = listOf("authwall", "/login", "signin", "sign-in", "/auth")
  }
}
