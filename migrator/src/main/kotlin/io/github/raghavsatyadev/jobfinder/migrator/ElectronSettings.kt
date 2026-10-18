package io.github.raghavsatyadev.jobfinder.migrator

import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * The old Electron app's scanner settings (`<userData>/settings.json`, written by `jobScanner.ts`).
 */
object ElectronSettings {
  /** Where Electron keeps `userData`: `%APPDATA%\<productName>`, or the package name in dev. */
  fun candidatePaths(): List<Path> {
    val appData =
      System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
        ?: Path.of(System.getProperty("user.home"), "AppData", "Roaming")
    return listOf("First 2 Apply", "first2apply-desktop", "Electron").map {
      appData.resolve(it).resolve("settings.json")
    }
  }

  /** Result of [load]: the settings and where they came from. */
  data class Loaded(val settings: ScannerSettings, val source: String)

  /**
   * Loads the settings the way `JobScanner` did. With a file, its values override the class
   * defaults (`preventSleep=false`, `useSound=false`, `inAppBrowserEnabled=true`, no cron rule, so
   * a missing `cronRule` means "Never"). Without a file, `DEFAULT_SETTINGS` apply (every hour,
   * prevent sleep, sound, in-app browser). `areEmailAlertsEnabled` is dropped (no email).
   */
  fun load(explicit: Path?): Loaded {
    val file = explicit ?: candidatePaths().firstOrNull { it.exists() }
    if (file == null || !file.exists()) {
      require(explicit == null) { "Settings file not found: $explicit" }
      return Loaded(
        ScannerSettings(
          cronRule = ScannerSettings.DEFAULT_CRON_RULE,
          preventSleep = true,
          useSound = true,
          inAppBrowserEnabled = true,
        ),
        "none found (looked in ${candidatePaths().joinToString()}); Electron DEFAULT_SETTINGS used",
      )
    }
    return Loaded(parse(file.readText()), file.toString())
  }

  fun parse(json: String): ScannerSettings {
    val obj: JsonObject = Json.parseToJsonElement(json).jsonObject
    fun bool(key: String, default: Boolean) = (obj[key] as? JsonPrimitive)?.booleanOrNull ?: default
    return ScannerSettings(
      cronRule = (obj["cronRule"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
      preventSleep = bool("preventSleep", false),
      useSound = bool("useSound", false),
      inAppBrowserEnabled = bool("inAppBrowserEnabled", true),
    )
  }
}
