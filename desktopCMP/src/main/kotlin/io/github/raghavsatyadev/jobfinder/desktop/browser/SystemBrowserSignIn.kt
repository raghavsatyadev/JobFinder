package io.github.raghavsatyadev.jobfinder.desktop.browser

import java.io.File
import java.util.TreeMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/** A Chrome or Edge installed on this PC. */
data class InstalledBrowser(val name: String, val exe: File) {
  companion object {
    /** Chrome, else Edge, from their usual install folders; `null` when neither is installed. */
    fun find(
      env: Map<String, String> = System.getenv(),
      exists: (File) -> Boolean = File::isFile,
    ): InstalledBrowser? {
      // Windows variable names ignore case, but System.getenv()'s map does not.
      val vars = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(env) }
      val roots =
        listOfNotNull(vars["ProgramFiles"], vars["ProgramFiles(x86)"], vars["LOCALAPPDATA"])
      val candidates =
        roots.map { InstalledBrowser("Chrome", File(it, "Google/Chrome/Application/chrome.exe")) } +
          roots.map { InstalledBrowser("Edge", File(it, "Microsoft/Edge/Application/msedge.exe")) }
      return candidates.firstOrNull { exists(it.exe) }
    }
  }
}

/** Turns DevTools cookies read from one browser into cookies another browser can set. */
object CookieCopy {
  /** The `Network.CookieParam` fields copied as they are. */
  private val COPIED =
    listOf("name", "value", "path", "secure", "httpOnly", "sameSite", "priority", "sourceScheme")

  /**
   * A `Network.CookieParam` for one `Network.Cookie` from `Storage.getCookies`, or `null` without a
   * name or domain. A host-only cookie (no leading dot) is set by URL without a domain, as a domain
   * would turn it into a domain cookie and `__Host-` cookies must not have one. A session cookie
   * gets no expiry; partitioned cookies keep their partition.
   */
  fun toParam(cookie: JsonObject): JsonObject? {
    val name = cookie.text("name") ?: return null
    val domain = cookie.text("domain")?.takeIf { it.isNotEmpty() } ?: return null
    if (name.isEmpty() && cookie.text("value").isNullOrEmpty()) return null
    val secure = cookie["secure"]?.jsonPrimitive?.booleanOrNull ?: false
    val session = cookie["session"]?.jsonPrimitive?.booleanOrNull ?: false
    val expires = cookie["expires"]?.jsonPrimitive?.doubleOrNull?.takeIf { !session && it > 0 }
    return buildJsonObject {
      for (key in COPIED) cookie[key]?.let { put(key, it) }
      if (domain.startsWith('.')) {
        put("domain", domain)
      } else {
        put("url", "${if (secure) "https" else "http"}://$domain${cookie.text("path") ?: "/"}")
      }
      if (expires != null) cookie["expires"]?.let { put("expires", it) }
      cookie["partitionKey"]?.takeIf { it is JsonObject }?.let { put("partitionKey", it) }
    }
  }

  /** `Storage.setCookies` parameters for every copyable cookie in [cookies]. */
  fun setCookiesParams(cookies: JsonArray): JsonObject? {
    val params = cookies.mapNotNull { (it as? JsonObject)?.let(::toParam) }
    if (params.isEmpty()) return null
    return buildJsonObject { put("cookies", JsonArray(params)) }
  }

  private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}

/**
 * Signs in to job sites in the Chrome or Edge installed on this PC, then copies the cookies into
 * the app's browser profile so scans run signed in.
 *
 * The embedded browser cannot do Google's sign-in popup, passkeys or the password manager; the real
 * browser can. It runs on its own profile under the app folder, so it never touches the user's
 * everyday profile. It opens as a plain browser, without DevTools: Google refuses to sign in ("This
 * browser or app may not be secure") in a browser that DevTools controls. When the user closes it,
 * the same profile opens once more, headless and with DevTools on a random localhost port, only to
 * read the cookies. They are written through a hidden app browser's DevTools, because JCEF's cookie
 * manager drops every cookie that has an expiry date.
 */
class SystemBrowserSignIn(
  private val runtime: CefRuntime,
  private val browser: InstalledBrowser? = InstalledBrowser.find(),
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(SystemBrowserSignIn::class.java)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Mutex()
  private var session: Session? = null

  /** `true` when a Chrome or Edge was found. */
  val isAvailable: Boolean
    get() = browser != null

  /** The browser's name, for messages. */
  val browserName: String?
    get() = browser?.name

  private class Session(val process: Process) {
    lateinit var copy: Job
  }

  private val profile: File
    get() = File(runtime.paths.root, PROFILE_DIR).apply { mkdirs() }

  /**
   * Opens [url] in the sign-in browser: a new tab when it is already open, a new window otherwise.
   * Throws when no browser is installed or it does not start.
   */
  suspend fun open(url: String): Unit = lock.withLock {
    val installed = browser ?: error("Neither Chrome nor Edge is installed")
    val current = session?.takeIf { it.process.isAlive }
    if (current != null) {
      // A second start on the same profile hands the page to the open browser and quits.
      launch(installed, url)
      return
    }
    session?.copy?.join()
    session = start(installed, url)
  }

  /** Suspends until the sign-in browser is closed and its cookies are copied. */
  suspend fun awaitClosed() {
    session?.copy?.join()
  }

  private fun launch(installed: InstalledBrowser, url: String, vararg extra: String): Process =
    ProcessBuilder(
        listOf(
          installed.exe.absolutePath,
          "--user-data-dir=${profile.absolutePath}",
          "--no-first-run",
          "--no-default-browser-check",
        ) + extra + url
      )
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .start()

  private fun start(installed: InstalledBrowser, url: String): Session {
    val process = launch(installed, url, "--new-window")
    val current = Session(process)
    log.info("Sign-in browser {} started (pid {})", installed.name, process.pid())
    current.copy = scope.launch {
      runInterruptible { process.waitFor() }
      log.info("Sign-in browser closed")
      try {
        copyCookies(installed)
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.warn("Copying sign-in cookies failed: {}", e.toString())
      }
    }
    return current
  }

  /** Opens the sign-in profile headless, reads its cookies and writes them to the app's browser. */
  private suspend fun copyCookies(installed: InstalledBrowser) {
    val portFile = File(profile, "DevToolsActivePort")
    portFile.delete()
    val reader =
      launch(
        installed,
        "about:blank",
        "--headless=new",
        "--remote-debugging-port=0",
        "--mute-audio",
      )
    try {
      DevToolsConnection.connect(awaitEndpoint(portFile, reader)).use { devTools ->
        val cookies = devTools.send("Storage.getCookies")["cookies"] as? JsonArray ?: return
        val params = CookieCopy.setCookiesParams(cookies) ?: return
        runtime.awaitReady()
        HiddenBrowser.create(runtime).use { writer ->
          writer.devTools("Storage.setCookies", params.toString())
        }
        log.info("Copied {} sign-in cookies", params["cookies"]?.jsonArray?.size)
        runCatching { devTools.send("Browser.close") }
      }
    } finally {
      if (reader.isAlive) reader.destroy()
      runInterruptible { runtime.flushCookies() }
    }
  }

  /** Waits for the browser to write its DevTools port, then returns the browser endpoint. */
  private suspend fun awaitEndpoint(portFile: File, process: Process): String {
    repeat((START_TIMEOUT / POLL).toInt()) {
      if (!process.isAlive) error("The browser quit while starting (exit ${process.exitValue()})")
      val lines = runCatching { portFile.readLines() }.getOrNull()
      if (lines != null && lines.size >= 2 && lines[0].isNotBlank()) {
        return "ws://127.0.0.1:${lines[0].trim()}${lines[1].trim()}"
      }
      delay(POLL)
    }
    process.destroy()
    error("The browser did not open DevTools within $START_TIMEOUT")
  }

  override fun close() {
    scope.coroutineContext[Job]?.cancel()
  }

  companion object {
    /** The sign-in browser's own profile, under the app folder. */
    const val PROFILE_DIR = "signin-browser"
    private val START_TIMEOUT = 30.seconds
    private val POLL = 200.milliseconds
  }
}
