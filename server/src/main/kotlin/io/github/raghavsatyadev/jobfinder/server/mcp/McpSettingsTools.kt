package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.server.http.LanAddress
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.ScanScheduler
import io.github.raghavsatyadev.jobfinder.shared.model.DetailLayout
import io.github.raghavsatyadev.jobfinder.shared.model.DetailSection
import io.github.raghavsatyadev.jobfinder.shared.model.JobLanguages
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.ServerSettings
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import java.net.NetworkInterface
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Tools for the scanner, the phone API's port and network, and the job detail layout. */
internal object McpSettingsTools {
  private val SECTIONS = DetailSection.entries.map { it.name.lowercase() }

  fun tools(d: McpDependencies): List<McpTool> = buildList {
    add(
      McpTool(
        "set_scan_schedule",
        "Sets how often the app checks the searches. Every 12 hours is the default and the most often advised: boards block an address that reads them every few hours. Ask the user before choosing more often.",
        McpSchema.obj(
          "every_hours" to
            McpSchema.integer(
              "Hours between scans, as in the app's choices: 2, 4, 8, 12, 24, 72 or 168; 0 turns scheduled scans off."
            ),
          required = listOf("every_hours"),
        ),
      ) {
        val rule =
          when (val hours = it.long("every_hours")) {
            0L -> null
            2L,
            4L,
            8L,
            12L -> "0 */$hours * * *"
            24L -> "0 0 * * *"
            72L -> "0 0 */3 * *"
            168L -> "0 0 * * 0"
            else -> throw McpToolError("Use 2, 4, 8, 12, 24, 72 or 168 hours, or 0 for off.")
          }
        scanner(
          d.settings.updateScannerSettings(d.settings.getScannerSettings().copy(cronRule = rule))
        )
      }
    )
    add(
      McpTool(
        "get_scanner_settings",
        "The scan settings: schedule (cron rule), keep the computer awake, sound, in-app browser, browsers used per scan, days filtered-out jobs are kept, background AI paused, and the language jobs are translated into.",
        readOnly = true,
      ) {
        scanner(d.settings.getScannerSettings())
      }
    )
    add(
      McpTool(
        "set_scanner_settings",
        "Changes the scan settings, as in Settings → Scanning. Leave out what should stay. For the schedule prefer set_scan_schedule.",
        McpSchema.obj(
          "cron_rule" to
            McpSchema.string(
              "Five-field Unix cron rule for scheduled scans, or \"never\". More often than every 12 hours risks blocks."
            ),
          "prevent_sleep" to McpSchema.boolean("Keep the computer awake so scans run on time."),
          "use_sound" to McpSchema.boolean("Play a sound when new jobs come in."),
          "in_app_browser" to McpSchema.boolean("Open jobs in the app's own browser."),
          "scraper_concurrency" to
            McpSchema.integer(
              "Hidden browsers reading the boards at once: ${ScannerSettings.SCRAPER_CONCURRENCY_RANGE.first}–${ScannerSettings.SCRAPER_CONCURRENCY_RANGE.last}."
            ),
          "excluded_keep_days" to
            McpSchema.integer(
              "Days jobs the AI filtered out are kept: ${ScannerSettings.EXCLUDED_KEEP_DAYS_RANGE.first}–${ScannerSettings.EXCLUDED_KEEP_DAYS_RANGE.last}, or 0 to keep them forever."
            ),
          "ai_paused" to
            McpSchema.boolean(
              "Pause the AI work the app does on its own (ATS scores, company ratings, careers checks). Jobs the user opens still get them."
            ),
          "translate_to" to
            McpSchema.string(
              "Language jobs in other languages are translated into, or \"off\".",
              JobLanguages.ALL + "off",
            ),
        ),
      ) {
        val current = d.settings.getScannerSettings()
        val cron =
          when (val rule = it.optStr("cron_rule")?.trim()) {
            null -> current.cronRule
            "",
            "never" -> null
            else -> {
              if (ScanScheduler.executionTime(rule) == null) {
                throw McpToolError("\"$rule\" is not a five-field Unix cron rule.")
              }
              rule
            }
          }
        val concurrency = it.optInt("scraper_concurrency") ?: current.scraperConcurrency
        if (concurrency !in ScannerSettings.SCRAPER_CONCURRENCY_RANGE) {
          throw McpToolError(
            "scraper_concurrency must be in ${ScannerSettings.SCRAPER_CONCURRENCY_RANGE}."
          )
        }
        val keepDays =
          when (val days = it.optInt("excluded_keep_days")) {
            null -> current.excludedKeepDays
            0 -> null
            in ScannerSettings.EXCLUDED_KEEP_DAYS_RANGE -> days
            else ->
              throw McpToolError(
                "excluded_keep_days must be in ${ScannerSettings.EXCLUDED_KEEP_DAYS_RANGE}, or 0 for forever."
              )
          }
        val translateTo =
          when (val lang = it.optStr("translate_to")?.trim()?.lowercase()) {
            null -> current.translateTo
            "off",
            "" -> null
            in JobLanguages.ALL -> lang
            else -> throw McpToolError("translate_to must be one of ${JobLanguages.ALL} or off.")
          }
        val saved =
          d.settings.updateScannerSettings(
            current.copy(
              cronRule = cron,
              preventSleep = it.optBool("prevent_sleep") ?: current.preventSleep,
              useSound = it.optBool("use_sound") ?: current.useSound,
              inAppBrowserEnabled = it.optBool("in_app_browser") ?: current.inAppBrowserEnabled,
              scraperConcurrency = concurrency,
              excludedKeepDays = keepDays,
              aiPaused = it.optBool("ai_paused") ?: current.aiPaused,
              translateTo = translateTo,
            )
          )
        scanner(saved)
      }
    )
    add(
      McpTool(
        "get_server_settings",
        "The port and network the phone API listens on, and the networks this computer can use.",
        readOnly = true,
      ) {
        server(d)
      }
    )
    add(
      McpTool(
        "set_server_settings",
        "Changes the port or network the phone API listens on; the API moves at once and a paired phone may need to find it again. Ask the user first. This MCP server stays where it is.",
        McpSchema.obj(
          "port" to
            McpSchema.integer(
              "Port, ${LocalSettingsRepository.PORT_RANGE.first}–${LocalSettingsRepository.PORT_RANGE.last}; default ${ServerSettings.DEFAULT_PORT}."
            ),
          "network_interface" to
            McpSchema.string(
              "Network name from get_server_settings, or \"auto\" for the one with the internet route."
            ),
        ),
      ) {
        val repo = d.serverSettings()
        val current = repo.getServerSettings()
        val port = it.optInt("port") ?: current.port
        if (port !in LocalSettingsRepository.PORT_RANGE) {
          throw McpToolError("port must be in ${LocalSettingsRepository.PORT_RANGE}.")
        }
        val network =
          when (val name = it.optStr("network_interface")?.trim()) {
            null -> current.networkInterface
            "",
            "auto" -> null
            else -> {
              if (LanAddress.interfaceIpv4(name) == null) {
                throw McpToolError("No usable network \"$name\". Use one from get_server_settings.")
              }
              name
            }
          }
        repo.updateServerSettings(ServerSettings(port, network))
        server(d)
      }
    )
    add(
      McpTool(
        "get_detail_layout",
        "The order of the sections on a job's page, and the hidden ones. The phone follows it.",
        readOnly = true,
      ) {
        mcpJson(d.settings.getDetailLayout())
      }
    )
    add(
      McpTool(
        "set_detail_layout",
        "Changes the order of a job page's sections or which are hidden. Leave out what should stay.",
        McpSchema.obj(
          "order" to McpSchema.strings("Sections in order, from $SECTIONS; missing ones go last."),
          "hidden" to McpSchema.strings("Sections to hide, from $SECTIONS; [] shows all."),
          "reset" to McpSchema.boolean("Put back the app's order with nothing hidden."),
        ),
      ) {
        val current =
          if (it.optBool("reset") == true) DetailLayout.DEFAULT else d.settings.getDetailLayout()
        val layout =
          DetailLayout(
            order = it.optStrings("order")?.map(::section) ?: current.order,
            hidden = it.optStrings("hidden")?.map(::section)?.toSet() ?: current.hidden,
          )
        mcpJson(d.settings.updateDetailLayout(layout))
      }
    )

    // Saved application form answers (Settings → Form answers)
    add(
      McpTool(
        "list_form_answers",
        "Answers the user gave on job application forms, which the app fills in again on other forms.",
        readOnly = true,
      ) {
        mcpJson(formAnswers(d).listAnswers())
      }
    )
    add(
      McpTool(
        "update_form_answer",
        "Changes a saved form answer.",
        McpSchema.obj(
          "id" to McpSchema.integer("Answer id from list_form_answers."),
          "value" to McpSchema.string("The new answer."),
          required = listOf("id", "value"),
        ),
      ) {
        val repo = formAnswers(d)
        val id = it.long("id")
        if (repo.listAnswers().none { a -> a.id == id }) throw McpToolError("No answer $id.")
        mcpJson(
          repo.updateAnswer(id, it.optStr("value") ?: throw McpToolError("\"value\" is required."))
        )
      }
    )
    add(
      McpTool(
        "delete_form_answer",
        "Deletes a saved form answer. Ask the user first.",
        McpSchema.obj("id" to McpSchema.integer("Answer id."), required = listOf("id")),
        destructive = true,
      ) {
        val repo = formAnswers(d)
        val id = it.long("id")
        if (repo.listAnswers().none { a -> a.id == id }) throw McpToolError("No answer $id.")
        repo.deleteAnswer(id)
        "Deleted answer $id."
      }
    )
  }

  private fun formAnswers(d: McpDependencies): FormAnswersRepository =
    d.formAnswers() ?: throw McpToolError("Form answers aren't available in this app.")

  fun scanner(s: ScannerSettings): JsonObject = buildJsonObject {
    put("cronRule", s.cronRule ?: "never")
    put("risky", ScannerSettings.isRisky(s.cronRule))
    put("preventSleep", s.preventSleep)
    put("useSound", s.useSound)
    put("inAppBrowser", s.inAppBrowserEnabled)
    put("scraperConcurrency", s.scraperConcurrency)
    put("excludedKeepDays", s.excludedKeepDays ?: 0)
    put("excludedKeptForever", s.excludedKeepDays == null)
    put("aiPaused", s.aiPaused)
    put("translateTo", s.translateTo ?: "off")
  }

  suspend fun server(d: McpDependencies): JsonObject {
    val s = d.serverSettings().getServerSettings()
    return buildJsonObject {
      put("port", s.port)
      put("networkInterface", s.networkInterface ?: "auto")
      put("address", LanAddress.pick(s.networkInterface)?.hostAddress)
      putJsonArray("networks") {
        NetworkInterface.getNetworkInterfaces().asSequence().forEach { n ->
          val ip = LanAddress.interfaceIpv4(n.name) ?: return@forEach
          add(
            buildJsonObject {
              put("name", n.name)
              put("label", n.displayName)
              put("address", ip.hostAddress)
            }
          )
        }
      }
    }
  }

  private fun section(wire: String): DetailSection =
    DetailSection.entries.firstOrNull { it.name.equals(wire.trim(), ignoreCase = true) }
      ?: throw McpToolError("Unknown section \"$wire\". Use $SECTIONS.")
}
