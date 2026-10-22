package io.github.raghavsatyadev.jobfinder.desktop.nativehost

import io.github.raghavsatyadev.jobfinder.server.nativehost.NativeProtocol
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Registers the installed app as the extension's native host for Chrome and Edge, for this user
 * only. Windows: writes the host manifest into the data folder and points the `HKCU` registry keys
 * at it. macOS: writes the manifest into each browser's `NativeMessagingHosts` folder, which is how
 * a Mac browser finds it. Runs at every start (the install folder or version may change) and does
 * nothing when run from the build or on other systems.
 */
object NativeHostRegistration {
  private val log = LoggerFactory.getLogger(NativeHostRegistration::class.java)

  fun register(dataDir: File) {
    val os = System.getProperty("os.name").orEmpty()
    val mac = os.startsWith("Mac")
    if (!os.startsWith("Windows") && !mac) return
    val exe = installedExe() ?: return
    try {
      val body = manifest(exe)
      if (mac) {
        val support = File(System.getProperty("user.home"), "Library/Application Support")
        for (browser in MAC_BROWSER_DIRS) {
          // Only browsers that are set up for this user; the folder is theirs to create.
          val browserDir = support.resolve(browser).takeIf { it.isDirectory } ?: continue
          browserDir.resolve("NativeMessagingHosts/${NativeProtocol.HOST_NAME}.json").write(body)
        }
        return
      }
      val manifest = dataDir.resolve(MANIFEST_DIR).resolve("${NativeProtocol.HOST_NAME}.json")
      manifest.write(body)
      for (key in REGISTRY_KEYS) {
        val code = reg("add", key, "/ve", "/t", "REG_SZ", "/d", manifest.absolutePath, "/f")
        if (code != 0) log.warn("reg add {} ended with {}", key, code)
      }
    } catch (e: Exception) {
      log.warn("The browser extension host could not be registered", e)
    }
  }

  private fun File.write(body: String) {
    parentFile.mkdirs()
    if (!isFile || readText() != body) writeText(body)
  }

  private fun manifest(exe: File): String = buildJsonObject {
    put("name", NativeProtocol.HOST_NAME)
    put("description", DESCRIPTION)
    put("path", exe.absolutePath)
    put("type", "stdio")
    put("allowed_origins", JsonArray(ALLOWED_ORIGINS.map { JsonPrimitive(it) }))
  }
    .toString()

  /** The installer's launcher, or `null` when the app runs from the build. */
  private fun installedExe(): File? =
    System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile }

  private fun reg(vararg args: String): Int {
    val process =
      ProcessBuilder(listOf("reg.exe") + args)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start()
    if (!process.waitFor(REG_TIMEOUT_S, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      return -1
    }
    return process.exitValue()
  }

  private const val MANIFEST_DIR = "native-messaging"
  private const val DESCRIPTION = "JobFinder native host"
  private const val REG_TIMEOUT_S = 10L
  /** The JobFinder Helper extension ids the host accepts. */
  private val ALLOWED_ORIGINS = listOf("chrome-extension://cnleajglbkmfgfkfihheoacmojpklpop/")
  /** Chrome's and Edge's folders under `~/Library/Application Support`. */
  private val MAC_BROWSER_DIRS = listOf("Google/Chrome", "Microsoft Edge")
  private val REGISTRY_KEYS =
    listOf(
      "HKCU\\Software\\Google\\Chrome\\NativeMessagingHosts\\${NativeProtocol.HOST_NAME}",
      "HKCU\\Software\\Microsoft\\Edge\\NativeMessagingHosts\\${NativeProtocol.HOST_NAME}",
    )
}
