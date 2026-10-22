package io.github.raghavsatyadev.jobfinder.desktop.setup

import com.sun.jna.Platform
import io.github.raghavsatyadev.jobfinder.server.ai.LlmConfig
import io.github.raghavsatyadev.jobfinder.ui.platform.SetupProblem
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.readUTF8Line
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** A setup step that could not be done, and why. */
class SetupFailure(val problem: SetupProblem, cause: Throwable? = null) :
  Exception(problem.name, cause)

/** Reports a download: bytes done, and the total when known. */
typealias Progress = (done: Long, total: Long?) -> Unit

/** Installs and starts Ollama, and gets the app's model. */
interface OllamaSetup {
  suspend fun running(): Boolean

  fun installed(): Boolean

  /** Downloads the installer and runs it silently. Throws [SetupFailure]. */
  suspend fun install(onProgress: Progress)

  /** Starts Ollama and waits until it answers; `false` when it never does. */
  suspend fun start(): Boolean

  /** The installed models, or `null` when Ollama does not answer. */
  suspend fun models(): List<String>?

  /** Downloads [model]. Throws [SetupFailure]. */
  suspend fun pull(model: String, onProgress: Progress)

  /** Makes [name] from [from] with a [contextSize] window. Throws [SetupFailure]. */
  suspend fun create(name: String, from: String, contextSize: Int)
}

/**
 * The real [OllamaSetup] and Ollama's HTTP API on [baseUrl]. On Windows: Ollama's per-user
 * installer (no permission prompt). On macOS: Ollama's app, unzipped into `~/Applications` (no
 * permission prompt), and its bundled `ollama serve`; an Ollama already in `/Applications` is used
 * as it is. On Linux: Ollama's own install script, run as root through `pkexec`, which asks for the
 * password once and also sets Ollama up as a service that starts with the computer.
 */
class DesktopOllamaSetup(
  private val http: HttpClient,
  private val baseUrl: String = BASE_URL,
  private val installDir: File = defaultInstallDir(),
  private val downloads: File = File(System.getProperty("java.io.tmpdir")),
) : OllamaSetup {
  override suspend fun running(): Boolean =
    try {
      http
        .get("$baseUrl/api/version") { timeout { requestTimeoutMillis = QUICK_MS } }
        .status
        .isSuccess()
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      false
    }

  override fun installed(): Boolean =
    when {
      Platform.isWindows() -> File(installDir, OLLAMA_EXE).isFile
      Platform.isMac() -> macBinary() != null
      else -> linuxBinary() != null
    }

  override suspend fun install(onProgress: Progress) {
    if (Platform.isMac()) return installMac(onProgress)
    if (!Platform.isWindows()) return installLinux(onProgress)
    val installer = File(downloads, "OllamaSetup.exe")
    try {
      download(INSTALLER_URL, installer, onProgress)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      throw SetupFailure(SetupProblem.DOWNLOAD_FAILED, e)
    }
    val exit =
      withContext(Dispatchers.IO) {
        val process =
          ProcessBuilder(installer.path, "/VERYSILENT", "/NORESTART", "/SUPPRESSMSGBOXES", "/SP-")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (process.waitFor(INSTALL_MINUTES, TimeUnit.MINUTES)) process.exitValue()
        else process.destroyForcibly().let { -1 }
      }
    installer.delete()
    if (exit != 0 || !installed()) throw SetupFailure(SetupProblem.INSTALL_FAILED)
  }

  /** Ollama's app zip, unzipped by `ditto` (which keeps its signature) into `~/Applications`. */
  private suspend fun installMac(onProgress: Progress) {
    val zip = File(downloads, "Ollama-darwin.zip")
    try {
      download(MAC_ZIP_URL, zip, onProgress)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      throw SetupFailure(SetupProblem.DOWNLOAD_FAILED, e)
    }
    val target = File(System.getProperty("user.home"), "Applications")
    val exit =
      withContext(Dispatchers.IO) {
        target.mkdirs()
        File(target, MAC_APP).deleteRecursively()
        val process =
          ProcessBuilder("/usr/bin/ditto", "-x", "-k", zip.path, target.path)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (process.waitFor(INSTALL_MINUTES, TimeUnit.MINUTES)) process.exitValue()
        else process.destroyForcibly().let { -1 }
      }
    zip.delete()
    if (exit != 0 || !installed()) throw SetupFailure(SetupProblem.INSTALL_FAILED)
  }

  /** Ollama's install script, run as root through `pkexec` (the desktop's password prompt). */
  private suspend fun installLinux(onProgress: Progress) {
    val pkexec = onPath("pkexec") ?: throw SetupFailure(SetupProblem.INSTALL_FAILED)
    val script = File(downloads, "ollama-install.sh")
    try {
      download(LINUX_SCRIPT_URL, script, onProgress)
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      throw SetupFailure(SetupProblem.DOWNLOAD_FAILED, e)
    }
    val exit =
      withContext(Dispatchers.IO) {
        val process =
          ProcessBuilder(pkexec.path, "/bin/sh", script.path)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        if (process.waitFor(INSTALL_MINUTES, TimeUnit.MINUTES)) process.exitValue()
        else process.destroyForcibly().let { -1 }
      }
    script.delete()
    if (exit != 0 || !installed()) throw SetupFailure(SetupProblem.INSTALL_FAILED)
  }

  override suspend fun start(): Boolean {
    if (running()) return true
    val app = File(installDir, OLLAMA_APP_EXE)
    val command =
      when {
        Platform.isMac() -> listOf((macBinary() ?: return false).path, "serve")
        !Platform.isWindows() -> listOf((linuxBinary() ?: return false).path, "serve")
        app.isFile -> listOf(app.path)
        else -> listOf(File(installDir, OLLAMA_EXE).path, "serve")
      }
    try {
      withContext(Dispatchers.IO) {
        ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
      }
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      return false
    }
    repeat(START_WAIT_SECONDS) {
      delay(1_000)
      if (running()) return true
    }
    return false
  }

  override suspend fun models(): List<String>? =
    try {
      val response = http.get("$baseUrl/api/tags") { timeout { requestTimeoutMillis = QUICK_MS } }
      if (response.status.isSuccess()) parseModels(response.bodyAsText()) else null
    } catch (e: Exception) {
      if (e is CancellationException) throw e
      null
    }

  override suspend fun pull(model: String, onProgress: Progress) {
    val progress = PullProgress()
    try {
      http
        .preparePost("$baseUrl/api/pull") {
          longRunning()
          contentType(ContentType.Application.Json)
          setBody(buildJsonObject { put("model", model) }.toString())
        }
        .execute { response ->
          if (!response.status.isSuccess()) throw SetupFailure(SetupProblem.DOWNLOAD_FAILED)
          val channel = response.bodyAsChannel()
          while (true) {
            val line = channel.readUTF8Line() ?: break
            if (progress.add(line)) onProgress(progress.done, progress.total)
          }
        }
    } catch (e: Exception) {
      if (e is CancellationException || e is SetupFailure) throw e
      throw SetupFailure(SetupProblem.DOWNLOAD_FAILED, e)
    }
    if (!progress.success) throw SetupFailure(SetupProblem.DOWNLOAD_FAILED)
  }

  override suspend fun create(name: String, from: String, contextSize: Int) {
    val body = buildJsonObject {
      put("model", name)
      put("from", from)
      put("stream", false)
      put("parameters", buildJsonObject { put("num_ctx", contextSize) })
    }
    val ok =
      try {
        http
          .preparePost("$baseUrl/api/create") {
            longRunning()
            contentType(ContentType.Application.Json)
            setBody(body.toString())
          }
          .execute { it.status.isSuccess() }
      } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
      }
    if (!ok) throw SetupFailure(SetupProblem.DOWNLOAD_FAILED)
  }

  private suspend fun download(url: String, target: File, onProgress: Progress) {
    http
      .prepareGet(url) { longRunning() }
      .execute { response ->
        check(response.status.isSuccess()) { "HTTP ${response.status.value}" }
        val total = response.contentLength()
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(BUFFER_BYTES)
        var done = 0L
        withContext(Dispatchers.IO) {
          target.outputStream().use { out ->
            while (true) {
              val read = channel.readAvailable(buffer)
              if (read < 0) break
              if (read == 0 && channel.isClosedForRead) break
              out.write(buffer, 0, read)
              done += read
              onProgress(done, total)
            }
          }
        }
      }
  }

  private fun HttpRequestBuilder.longRunning() = timeout {
    requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    socketTimeoutMillis = QUIET_LIMIT_MS
  }

  companion object {
    const val BASE_URL = "http://localhost:11434"
    const val INSTALLER_URL = "https://ollama.com/download/OllamaSetup.exe"
    const val LINUX_SCRIPT_URL = "https://ollama.com/install.sh"
    const val MAC_ZIP_URL = "https://ollama.com/download/Ollama-darwin.zip"
    private const val MAC_APP = "Ollama.app"
    private const val OLLAMA_EXE = "ollama.exe"
    private const val OLLAMA_APP_EXE = "ollama app.exe"
    private const val QUICK_MS = 2_000L
    /** Ollama checks a big download's digest before it says anything more. */
    private const val QUIET_LIMIT_MS = 10 * 60_000L
    private const val INSTALL_MINUTES = 10L
    private const val START_WAIT_SECONDS = 60
    private const val BUFFER_BYTES = 64 * 1024

    /** Where the per-user installer puts Ollama: `%LOCALAPPDATA%\Programs\Ollama`. */
    fun defaultInstallDir(): File =
      File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Programs/Ollama")

    /** Where Ollama's Linux install script puts it, or anywhere else on the PATH. */
    private fun linuxBinary(): File? =
      listOf(File("/usr/local/bin/ollama"), File("/usr/bin/ollama")).firstOrNull { it.canExecute() }
        ?: onPath("ollama")

    /** The `ollama` inside Ollama's app (user or system Applications), or one on the PATH. */
    private fun macBinary(): File? =
      listOf(File(System.getProperty("user.home"), "Applications"), File("/Applications"))
        .map { File(it, "$MAC_APP/Contents/Resources/ollama") }
        .firstOrNull { it.canExecute() }
        ?: listOf(File("/opt/homebrew/bin/ollama"), File("/usr/local/bin/ollama")).firstOrNull {
          it.canExecute()
        }
        ?: onPath("ollama")

    private fun onPath(name: String): File? =
      System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .map { File(it, name) }
        .firstOrNull { it.canExecute() }

    /** The names in an `/api/tags` answer. */
    fun parseModels(body: String): List<String>? = runCatching {
      (Json.parseToJsonElement(body) as JsonObject)["models"]
        ?.let { it as JsonArray }
        .orEmpty()
        .mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
    }
      .getOrNull()
  }
}

/**
 * Adds up an `/api/pull` stream: each layer reports its own `total` and `completed`, keyed by
 * `digest`, so the whole download is the sum over the layers.
 */
class PullProgress {
  private val layers = mutableMapOf<String, Pair<Long, Long>>()
  var success = false
    private set

  val done: Long
    get() = layers.values.sumOf { it.second }

  val total: Long?
    get() = layers.values.sumOf { it.first }.takeIf { it > 0 }

  /** Reads one line; `true` when the numbers changed. Throws [SetupFailure] on an error line. */
  fun add(line: String): Boolean {
    if (line.isBlank()) return false
    val json =
      runCatching { Json.parseToJsonElement(line) as JsonObject }.getOrNull() ?: return false
    json["error"]?.jsonPrimitive?.contentOrNull?.let {
      throw SetupFailure(SetupProblem.DOWNLOAD_FAILED, IllegalStateException(it))
    }
    if (json["status"]?.jsonPrimitive?.contentOrNull == "success") success = true
    val digest = json["digest"]?.jsonPrimitive?.contentOrNull ?: return false
    val total = json["total"]?.jsonPrimitive?.longOrNull ?: return false
    val completed = json["completed"]?.jsonPrimitive?.longOrNull ?: 0L
    layers[digest] = total to completed.coerceAtMost(total)
    return true
  }
}

/** The app's model and what it is made from. */
object AppModel {
  /** The name the server asks for, without the `:latest` tag. */
  val NAME = LlmConfig.DEFAULT_MODEL.substringBefore(':')
  const val BASE = "qwen3.6:35b-a3b"
  const val CONTEXT_SIZE = 16_384
  /** About how big [BASE] is. */
  const val BYTES = 22_000_000_000L

  fun isIn(models: List<String>): Boolean = models.any { it.substringBefore(':') == NAME }

  fun baseIsIn(models: List<String>): Boolean = BASE in models
}
