package io.github.raghavsatyadev.jobfinder.desktop.browser

import androidx.compose.ui.window.application
import io.github.raghavsatyadev.jobfinder.desktop.ui.LoginBrowserWindow
import io.github.raghavsatyadev.jobfinder.desktop.ui.LoginRequest
import io.github.raghavsatyadev.jobfinder.server.scan.HtmlFetcher
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager

/**
 * Developer commands, run as `./gradlew :desktopCMP:run --args="<command> ..."`. Each command
 * starts CEF on the persistent profile, does its job and returns an exit code; the caller shuts CEF
 * down.
 */
class DevCommands(private val runtime: CefRuntime, private val out: (String) -> Unit = ::println) {

  /** Returns `true` when [args] name a dev command instead of the normal app start. */
  fun handles(args: List<String>): Boolean = args.firstOrNull() in COMMANDS

  /** Runs the command in [args] and returns the process exit code. */
  fun run(args: List<String>): Int =
    when (args.firstOrNull()) {
      "capture" ->
        if (args.size < 3) usage()
        else capture(args[1], File(args[2]), args.getOrNull(3)?.toIntOrNull() ?: DEFAULT_SCROLLS)
      "login" -> systemLogin(args.getOrElse(1) { LINKEDIN_LOGIN })
      "login-embedded" -> login(args.getOrElse(1) { LINKEDIN_LOGIN })
      "bridge-test" -> bridgeTest(args.getOrNull(1)?.toIntOrNull() ?: 7)
      "cookie-test" -> cookieTest(args.getOrElse(1) { "get" })
      "memory-test" ->
        if (args.size < 3) usage()
        else memoryTest(args[1].toInt(), args[2], args.getOrNull(3)?.toLongOrNull() ?: 60)
      else -> usage()
    }

  private fun usage(): Int {
    out(
      """
      Dev commands:
        capture <url> <out.html> [scrollTimes=$DEFAULT_SCROLLS]  load in a hidden browser, write the DOM
        login [url]                       sign in in the installed Chrome/Edge; cookies copied until it closes
        login-embedded [url]              sign in in the embedded browser; click Done when signed in
        bridge-test [megabytes=7]         send a big string through CefMessageRouter, check length + hash
        cookie-test set|get               set test cookies on example.com, or read them (after a restart)
        memory-test <browsers> <url> [holdSeconds=60]  load <browsers> hidden browsers, print PIDs, hold
      """
        .trimIndent()
    )
    return 64
  }

  private fun capture(url: String, target: File, scrollTimes: Int): Int = runBlocking {
    val started = TimeSource.Monotonic.markNow()
    HiddenBrowser.create(runtime).use { browser ->
      val result = browser.fetch(url, scrollTimes)
      val page = browser.evaluate(PAGE_STATS)
      target.absoluteFile.parentFile?.mkdirs()
      target.writeText(result.html)
      out("url:           ${result.url}")
      out("final url:     ${result.finalUrl}")
      out("http status:   ${result.httpStatus}")
      out("title:         ${result.title}")
      out("page:          $page")
      out("html chars:    ${result.html.length}")
      out("file bytes:    ${target.length()} -> ${target.absolutePath}")
      for (token in JOB_CARD_CLASSES) {
        out("class $token: ${countClassToken(result.html, token)}")
      }
      out("elapsed:       ${started.elapsedNow()}")
    }
    0
  }

  private fun systemLogin(url: String): Int = runBlocking {
    val signIn = SystemBrowserSignIn(runtime)
    if (!signIn.isAvailable) return@runBlocking login(url)
    signIn.use {
      it.open(url)
      out("Sign in in ${it.browserName}, then close it.")
      it.awaitClosed()
    }
    printSignIns()
  }

  private fun login(url: String): Int {
    out("Sign in in the window, then select Done (or close it).")
    application { LoginBrowserWindow(runtime, LoginRequest(url, 0), onDone = ::exitApplication) }
    return printSignIns()
  }

  private fun printSignIns(): Int {
    val cookies = readCookies()
    val linkedIn = cookies.filter { it.domain.endsWith("linkedin.com") }
    out("cookies in profile: ${cookies.size}, linkedin.com: ${linkedIn.size}")
    out("LinkedIn session cookie li_at present: ${linkedIn.any { it.name == "li_at" }}")
    cookies
      .filter { it.name.contains("f2a") }
      .forEach {
        out("test cookie ${it.name}=${it.value} domain='${it.domain}' expires=${it.hasExpires}")
      }
    return 0
  }

  private fun bridgeTest(megabytes: Int): Int = runBlocking {
    HiddenBrowser.create(runtime).use { browser ->
      browser.load(TEST_ORIGIN)
      val chars = megabytes * 1024 * 1024
      browser
        .evaluate(
          """(function () {
          var alphabet = "abcdefghijklmnopqrstuvwxyz0123456789 <>&\"'\n\té漢😀";
          var seed = 12345, parts = [], n = 0;
          while (n < $chars) {
            seed = (seed * 1103515245 + 12345) & 0x7fffffff;
            var piece = alphabet.substr((seed >> 8) % alphabet.length, 1 + (seed % 7));
            parts.push(piece);
            n += piece.length;
          }
          window.__f2aBig = parts.join("");
          var h = 0x811c9dc5;
          for (var i = 0; i < window.__f2aBig.length; i++) {
            h ^= window.__f2aBig.charCodeAt(i);
            h = Math.imul(h, 0x01000193) >>> 0;
          }
          return window.__f2aBig.length + ":" + h;
        })()"""
        )
        .let { expected ->
          val clock = TimeSource.Monotonic.markNow()
          val received = browser.evaluate("window.__f2aBig", timeout = 60.seconds)
          val elapsed = clock.elapsedNow()
          val actual = "${received.length}:${fnv1a32(received)}"
          val bytes = received.toByteArray(Charsets.UTF_8).size
          out("in page:  $expected (length:fnv1a32 over UTF-16 units)")
          out("received: $actual, ${bytes} UTF-8 bytes in $elapsed")
          out(if (expected == actual) "BRIDGE OK" else "BRIDGE MISMATCH")
          if (expected == actual) 0 else 1
        }
    }
  }

  private fun cookieTest(mode: String): Int = runBlocking {
    HiddenBrowser.create(runtime).use { browser ->
      browser.load(TEST_ORIGIN)
      when (mode) {
        "set" -> {
          val stamp = System.currentTimeMillis().toString()
          browser.evaluate("document.cookie = 'f2a_session=$stamp; path=/; Secure'")
          browser.evaluate("document.cookie = 'f2a_persist=$stamp; max-age=86400; path=/; Secure'")
          out("set f2a_session (session cookie) and f2a_persist (1 day) to $stamp")
          out("document.cookie now: ${browser.evaluate("document.cookie")}")
        }
        else -> {
          out("document.cookie: ${browser.evaluate("document.cookie")}")
          readCookies()
            .filter { it.name.startsWith("f2a_") }
            .forEach { out("CefCookieManager: ${it.name}=${it.value} hasExpires=${it.hasExpires}") }
        }
      }
    }
    0
  }

  private fun memoryTest(count: Int, url: String, holdSeconds: Long): Int = runBlocking {
    val browsers = (1..count).map { HiddenBrowser.create(runtime) }
    try {
      coroutineScope {
        browsers.map { async { it.fetch(url, 1).html.length } }.awaitAll()
      }
        .forEachIndexed { i, chars -> out("browser ${i + 1}: $chars chars") }
      val self = ProcessHandle.current()
      out("READY java pid=${self.pid()}")
      self.descendants().forEach { out("child pid=${it.pid()} ${it.info().command().orElse("")}") }
      delay(holdSeconds * 1000)
    } finally {
      browsers.forEach { it.close() }
    }
    0
  }

  /**
   * All cookies in the profile. CEF never calls the visitor when there are none, hence the wait.
   */
  private fun readCookies(): List<CefCookie> {
    val cookies = mutableListOf<CefCookie>()
    val done = CountDownLatch(1)
    val visiting =
      CefCookieManager.getGlobalManager().visitAllCookies { cookie, count, total, _ ->
        synchronized(cookies) { cookies += cookie }
        if (count == total - 1) done.countDown()
        true
      }
    if (visiting) done.await(3, TimeUnit.SECONDS)
    return synchronized(cookies) { cookies.toList() }
  }

  companion object {
    private val COMMANDS =
      setOf("capture", "login", "login-embedded", "bridge-test", "cookie-test", "memory-test")
    private const val DEFAULT_SCROLLS = HtmlFetcher.DEFAULT_SCROLL_TIMES
    private const val LINKEDIN_LOGIN = "https://www.linkedin.com/login"
    private const val TEST_ORIGIN = "https://example.com/"

    /** Class names that mark one job card on LinkedIn's public and signed-in search pages. */
    val JOB_CARD_CLASSES =
      listOf("base-card", "job-search-card", "jobs-search-results__list-item", "job-card-container")

    private const val PAGE_STATS =
      """JSON.stringify({ innerWidth: window.innerWidth, innerHeight: window.innerHeight,
        visibilityState: document.visibilityState, hidden: document.hidden,
        userAgent: navigator.userAgent, webdriver: navigator.webdriver })"""

    /** Counts elements whose `class` attribute contains [token] as a whole class name. */
    fun countClassToken(html: String, token: String): Int =
      Regex("""class\s*=\s*["'](?:[^"']*\s)?${Regex.escape(token)}(?=["'\s])""")
        .findAll(html)
        .count()

    /** 32-bit FNV-1a over UTF-16 code units, the same as the in-page hash in `bridge-test`. */
    fun fnv1a32(text: String): Long {
      var hash = 0x811c9dc5L
      for (ch in text) {
        hash = hash xor ch.code.toLong()
        hash = (hash * 0x01000193L) and 0xffffffffL
      }
      return hash
    }
  }
}
