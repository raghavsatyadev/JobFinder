package io.github.raghavsatyadev.jobfinder.server.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Where the running OpenCode server answers, and the header that lets the app in. */
class OpenCodeEndpoint(val baseUrl: String, internal val authorization: String) {
  /** Never prints the password. */
  override fun toString(): String = "OpenCodeEndpoint($baseUrl)"
}

/**
 * The user's installed OpenCode CLI (v2) as one long-lived child: `opencode serve` on a free
 * loopback port, started at the first call and stopped by [close] (and on JVM exit).
 *
 * The password is random, made per start and kept in memory only. The child gets its own empty
 * config folder ([configDir] as `XDG_CONFIG_HOME`), so the user's global `opencode.json` and its
 * MCP servers don't load; the sign-in (`opencode auth login`, kept under the data folder) still
 * applies.
 */
class OpenCodeServer(
  private val configDir: Path,
  private val http: HttpClient,
  private val findExecutable: () -> List<String>? = ::findOpenCode,
) : AutoCloseable {
  private val log = LoggerFactory.getLogger(OpenCodeServer::class.java)
  private val lock = Mutex()
  @Volatile private var process: Process? = null
  @Volatile private var endpoint: OpenCodeEndpoint? = null
  @Volatile private var hook: Thread? = null
  @Volatile private var version: String? = null

  /** The command that starts `opencode`; `null` when it isn't installed. */
  fun command(): List<String>? = findExecutable()

  /** `opencode --version` (e.g. "v2.0.31"); `null` when not installed or it doesn't answer. */
  suspend fun version(): String? {
    version?.let {
      return it
    }
    val command = command() ?: return null
    return withContext(Dispatchers.IO) {
      runCatching {
        val child =
          ProcessBuilder(command + "--version")
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
            .start()
        val out = child.inputStream.bufferedReader().readText().trim()
        if (!child.waitFor(VERSION_TIMEOUT_S, TimeUnit.SECONDS)) {
          kill(child)
          null
        } else out.lineSequence().lastOrNull { it.isNotBlank() }?.trim()
      }
        .getOrNull()
        ?.also { version = it }
    }
  }

  /** The running server, started first if needed. Throws [LlmException] when it can't start. */
  suspend fun endpoint(): OpenCodeEndpoint = lock.withLock {
    val running = endpoint
    if (running != null && process?.isAlive == true) return running
    stop()
    start()
  }

  /** After a connection error: the child may have died; the next [endpoint] starts it again. */
  suspend fun restartIfDead() = lock.withLock { if (process?.isAlive != true) stop() }

  override fun close() {
    stop()
  }

  private suspend fun start(): OpenCodeEndpoint {
    val command = command() ?: throw LlmException("The OpenCode CLI is not installed")
    val password = randomPassword()
    val port = freePort()
    val child =
      withContext(Dispatchers.IO) {
        Files.createDirectories(configDir)
        ProcessBuilder(command + listOf("serve", "--port", "$port", "--hostname", LOOPBACK))
          .directory(configDir.toFile())
          .redirectInput(ProcessBuilder.Redirect.from(nullFile()))
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .apply {
            environment()["OPENCODE_SERVER_PASSWORD"] = password
            // Its own config folder: the user's global opencode.json (MCP servers, agents) stays
            // out.
            environment()["XDG_CONFIG_HOME"] = configDir.toAbsolutePath().toString()
            environment().remove("OPENCODE_CONFIG")
            environment().remove("OPENCODE_CONFIG_CONTENT")
          }
          .start()
      }
    process = child
    val shutdown = Thread({ kill(child) }, "opencode-stop")
    runCatching { Runtime.getRuntime().addShutdownHook(shutdown) }.onSuccess { hook = shutdown }
    val auth =
      "Basic " + Base64.getEncoder().encodeToString("$USER:$password".toByteArray(Charsets.UTF_8))
    val ready = OpenCodeEndpoint("http://$LOOPBACK:$port", auth)
    val started = System.currentTimeMillis()
    while (System.currentTimeMillis() - started < START_TIMEOUT_MS) {
      if (!child.isAlive) {
        stop()
        throw LlmException("OpenCode stopped at start (exit ${child.exitValue()})")
      }
      if (healthy(ready)) {
        endpoint = ready
        log.info(
          "OpenCode server ready on port {} in {} ms",
          port,
          System.currentTimeMillis() - started,
        )
        return ready
      }
      delay(HEALTH_POLL_MS)
    }
    stop()
    throw LlmException("OpenCode did not start in ${START_TIMEOUT_MS / 1000} s")
  }

  private suspend fun healthy(endpoint: OpenCodeEndpoint): Boolean =
    try {
      http
        .get("${endpoint.baseUrl}/health") {
          header(HttpHeaders.Authorization, endpoint.authorization)
          timeout {
            connectTimeoutMillis = HEALTH_TIMEOUT_MS
            requestTimeoutMillis = HEALTH_TIMEOUT_MS
          }
        }
        .status
        .value == 200
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      false
    }

  private fun stop() {
    endpoint = null
    process?.let { kill(it) }
    process = null
    hook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
    hook = null
  }

  companion object {
    private const val USER = "opencode"
    private const val LOOPBACK = "127.0.0.1"
    private const val START_TIMEOUT_MS = 30_000L
    private const val HEALTH_POLL_MS = 200L
    private const val HEALTH_TIMEOUT_MS = 2_000L
    private const val VERSION_TIMEOUT_S = 10L
    private const val KILL_WAIT_S = 3L

    private val WINDOWS = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** The child and everything it started (an npm `.cmd` shim starts the real binary). */
    private fun kill(child: Process) {
      runCatching {
        val descendants = child.descendants().toList()
        child.destroy()
        descendants.forEach { it.destroy() }
        if (!child.waitFor(KILL_WAIT_S, TimeUnit.SECONDS)) child.destroyForcibly()
        descendants.filter { it.isAlive }.forEach { it.destroyForcibly() }
      }
    }

    private fun randomPassword(): String {
      val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun freePort(): Int =
      ServerSocket(0, 1, InetAddress.getByName(LOOPBACK)).use { it.localPort }

    private fun nullFile() = File(if (WINDOWS) "NUL" else "/dev/null")

    /**
     * `opencode` on PATH. On Windows the npm install puts an `opencode.cmd` shim there; the real
     * `opencode.exe` beside it in `node_modules` is used when found, so stopping the child stops
     * the server too.
     */
    fun findOpenCode(): List<String>? {
      val dirs =
        System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
      for (dir in dirs) {
        val folder = File(dir.trim('"'))
        if (!WINDOWS) {
          val bin = File(folder, "opencode")
          if (bin.isFile && bin.canExecute()) return listOf(bin.absolutePath)
          continue
        }
        File(folder, "opencode.exe")
          .takeIf { it.isFile }
          ?.let {
            return listOf(it.absolutePath)
          }
        val shim = File(folder, "opencode.cmd").takeIf { it.isFile } ?: continue
        val exe = File(folder, "node_modules/@opencode/cli/bin/opencode.exe")
        return if (exe.isFile) listOf(exe.absolutePath)
        else listOf("cmd.exe", "/c", shim.absolutePath)
      }
      return null
    }
  }
}
