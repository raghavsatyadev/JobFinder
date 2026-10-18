package io.github.raghavsatyadev.jobfinder.desktop.browser

import java.io.File
import org.cef.CefSettings
import org.cef.callback.CefCommandLine

/**
 * Folders the desktop app keeps under `%LOCALAPPDATA%\jobfinder`.
 *
 * @property root the app's data folder.
 */
data class AppPaths(val root: File) {
  /** CEF `root_cache_path`. Must be a parent of [cefProfile]. */
  val cefRoot: File = File(root, "cef")

  /** CEF `cache_path`: the persistent profile (cookies, local storage). */
  val cefProfile: File = File(cefRoot, "profile")

  /** Where jcefmaven extracts the bundled CEF natives (once per JCEF version). */
  val cefNatives: File = File(root, "jcef-bundle")

  /** File locked by the running instance; see [SingleInstanceLock]. */
  val instanceLock: File = File(root, "instance.lock")

  companion object {
    /** `%LOCALAPPDATA%\jobfinder`, or `~/.jobfinder` where LOCALAPPDATA is not set. */
    fun default(
      env: Map<String, String> = System.getenv(),
      userHome: String = System.getProperty("user.home"),
    ): AppPaths {
      val localAppData = env["LOCALAPPDATA"]?.takeIf { it.isNotBlank() }
      fun dir(name: String) =
        if (localAppData != null) File(localAppData, name) else File(userHome, ".$name")
      return AppPaths(moveOldData(dir(OLD_APP_DIR), dir(APP_DIR)))
    }

    /**
     * Moves the data of the app's old name (first2apply) to [root] once, renaming the database with
     * it. Returns the folder to use: the old one while it can't be moved, so the data is never
     * split.
     */
    private fun moveOldData(old: File, root: File): File {
      val use = if (!root.exists() && old.isDirectory && !old.renameTo(root)) old else root
      if (File(use, "$OLD_APP_DIR.db").exists() && !File(use, "$APP_DIR.db").exists()) {
        for (suffix in listOf("", "-wal", "-shm")) {
          File(use, "$OLD_APP_DIR.db$suffix").renameTo(File(use, "$APP_DIR.db$suffix"))
        }
      }
      return use
    }

    private const val APP_DIR = "jobfinder"
    private const val OLD_APP_DIR = "first2apply"
  }
}

/** Builders for the CEF command line, settings and user agent. Pure, so they are unit tested. */
object CefConfig {
  /** Width of the hidden browsers' view; job sites switch to the desktop layout at this size. */
  const val VIEW_WIDTH = 1600

  /** Height of the hidden browsers' view. */
  const val VIEW_HEIGHT = 1200

  /**
   * Chromium switches. Hidden browsers count as background or occluded pages, so without these
   * Chromium throttles their timers and rendering and infinite lists stop loading.
   */
  val switches: List<String> =
    listOf(
      "--disable-background-timer-throttling",
      "--disable-renderer-backgrounding",
      "--disable-backgrounding-occluded-windows",
      "--disable-features=CalculateNativeWinOcclusion",
    )

  /** Switches whose value is a comma list; Chromium keeps only the last copy of a switch. */
  private val LIST_SWITCHES = setOf("disable-features", "enable-features")

  /**
   * Appends [switches] to the browser process [commandLine]. Comma-list switches such as
   * `--disable-features` are merged with the value CEF already set (CEF disables a few features
   * itself), because appending a second copy would replace CEF's list.
   */
  fun applySwitches(commandLine: CefCommandLine, switches: List<String> = this.switches) {
    for (switch in switches) {
      val body = switch.removePrefix("--")
      val name = body.substringBefore('=')
      val value = body.substringAfter('=', missingDelimiterValue = "")
      when {
        body == name -> commandLine.appendSwitch(name)
        name in LIST_SWITCHES ->
          commandLine.appendSwitchWithValue(
            name,
            mergeList(
              commandLine.getSwitchValue(name).takeIf { commandLine.hasSwitch(name) },
              value,
            ),
          )
        else -> commandLine.appendSwitchWithValue(name, value)
      }
    }
  }

  /** Union of two comma lists, keeping order and dropping blanks and duplicates. */
  fun mergeList(existing: String?, added: String): String =
    (existing.orEmpty().split(',') + added.split(','))
      .map { it.trim() }
      .filter { it.isNotEmpty() }
      .distinct()
      .joinToString(",")

  /**
   * Fills [settings] for a persistent, windowless-capable profile under [paths].
   *
   * An empty `cache_path` silently makes the profile incognito, so it is always set.
   */
  fun applySettings(settings: CefSettings, paths: AppPaths, userAgent: String): CefSettings =
    settings.apply {
      root_cache_path = paths.cefRoot.absolutePath
      cache_path = paths.cefProfile.absolutePath
      persist_session_cookies = true
      windowless_rendering_enabled = true
      user_agent = userAgent
      log_file = File(paths.cefRoot, "cef.log").absolutePath
      log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
    }

  /**
   * Reads the Chromium major version from a jcefmaven release tag such as
   * `jcef-17e805a+cef-152.0.6+g708dc14+chromium-152.0.7977.83`.
   */
  fun chromiumMajor(releaseTag: String): Int =
    CHROMIUM_VERSION.find(releaseTag)?.groupValues?.get(1)?.toInt()
      ?: error("No Chromium version in JCEF release tag '$releaseTag'")

  /**
   * A plain Chrome user agent for [chromiumMajor], with no "CEF" or app tokens. Bot protection (for
   * example Cloudflare on LinkedIn) blocks unusual user agents; the old Electron app used the same
   * format (from the old Electron app).
   */
  fun chromeUserAgent(
    chromiumMajor: Int,
    osName: String = System.getProperty("os.name"),
  ): String {
    val os = osName.lowercase()
    val platform =
      when {
        os.startsWith("windows") -> "Windows NT 10.0; Win64; x64"
        os.startsWith("mac") -> "Macintosh; Intel Mac OS X 10_15_7"
        else -> "X11; Linux x86_64"
      }
    return "Mozilla/5.0 ($platform) AppleWebKit/537.36 (KHTML, like Gecko) " +
      "Chrome/$chromiumMajor.0.0.0 Safari/537.36"
  }

  private val CHROMIUM_VERSION = Regex("""chromium-(\d+)\.""")
}
