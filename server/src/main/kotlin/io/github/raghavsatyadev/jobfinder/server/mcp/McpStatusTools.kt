package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Tools for what is set up, every setting at once, and the help topics. */
internal object McpStatusTools {
  fun tools(d: McpDependencies, help: McpHelp): List<McpTool> = buildList {
    add(
      McpTool(
        "app_status",
        "What is set up and what is missing: searches, AI filter, resume, AI models and keys, job counts and the scan schedule. Start here.",
        readOnly = true,
      ) {
        status(d)
      }
    )
    add(
      McpTool(
        "get_all_settings",
        "Every setting the user can change, in one answer: searches, AI filter, scanner, AI model, keys (never the keys themselves), routing and CLIs, edited AI instructions, resume and its PDF settings, job page layout, form answers count, and the phone API's port and network. Each part names the tool that changes it.",
        readOnly = true,
      ) {
        allSettings(d)
      }
    )
    add(
      McpTool(
        "list_help_topics",
        "The help topics: how to set up the app, craft job searches, write the AI filter, edit AI instructions, get free AI keys, pair a phone and back up.",
        readOnly = true,
      ) {
        buildJsonArray {
          McpHelp.topics.forEach { t ->
            add(
              buildJsonObject {
                put("id", t.id)
                put("title", t.title)
                put("summary", t.summary)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "read_help_topic",
        "One help topic in full, as Markdown.",
        McpSchema.obj(
          "id" to McpSchema.string("Topic id from list_help_topics.", McpHelp.topics.map { it.id }),
          required = listOf("id"),
        ),
        readOnly = true,
      ) {
        help.read(it.str("id"))
      }
    )
  }

  private suspend fun allSettings(d: McpDependencies): JsonObject {
    val sites = d.sites.listSites().associateBy { it.id }
    val resume = d.ats?.getResume()
    return buildJsonObject {
      putJsonArray("searches") {
        d.links.listLinks().forEach { l ->
          add(
            buildJsonObject {
              put("id", l.id)
              put("title", l.title)
              put("url", l.url)
              put("board", sites[l.siteId]?.name)
            }
          )
        }
      }
      put("aiFilter", mcpJson(d.matching.getConfig()))
      put(
        "scanner",
        McpSettingsTools.scanner(d.settings.getScannerSettings()),
      )
      put("aiModel", McpAiTools.aiModel(d))
      put("aiKeys", McpAiTools.keysJson(d))
      put("aiRouting", McpAiTools.routingJson(d))
      putJsonObject("editedAiPrompts") {
        McpPromptTools.edited(d).forEach { (id, text) -> put(id, text) }
      }
      put("resume", resume?.let { mcpJson(it) } ?: JsonPrimitive("none"))
      put(
        "resumePdf",
        d.resumePdf()?.let { mcpJson(it.status()) } ?: JsonPrimitive("unavailable"),
      )
      put("detailLayout", mcpJson(d.settings.getDetailLayout()))
      put(
        "formAnswers",
        d.formAnswers()?.listAnswers()?.size ?: 0,
      )
      put("server", McpSettingsTools.server(d))
      putJsonObject("changeWith") {
        put("searches", "add_job_search, update_job_search, delete_job_search")
        put("aiFilter", "set_ai_filter")
        put("scanner", "set_scanner_settings, set_scan_schedule")
        put("aiModel", "set_ai_model")
        put("aiKeys", "add_ai_key, remove_ai_key")
        put("aiRouting", "set_ai_routing, set_ai_clis")
        put("editedAiPrompts", "set_ai_prompt, reset_ai_prompt")
        put("resume", "upload_resume")
        put("resumePdf", "set_resume_pdf_settings, import_linkedin_data")
        put("detailLayout", "set_detail_layout")
        put("formAnswers", "list_form_answers, update_form_answer")
        put("server", "set_server_settings")
        put(
          "backups",
          "list_backups, export_settings_backup, export_full_backup, export_jobs_csv, read_backup, import_backup",
        )
        put("phonePairing", "get_phone_pairing, rotate_pairing_token")
        put("agentAccess", "get_agent_access, rotate_agent_token")
        put("siteSignIns", "check_sign_ins, open_sign_in, copy_browser_sign_ins")
        put(
          "localModels",
          "get_pc_profile, list_local_models, search_huggingface_models, list_model_quants, get_model_size, add_local_model, check_local_model, get_local_model_progress, remove_local_model",
        )
      }
    }
  }

  private suspend fun status(d: McpDependencies): JsonObject {
    val counts = d.jobs.countJobs()
    val links = d.links.listLinks()
    val filter = d.matching.getConfig()
    val scanner = d.settings.getScannerSettings()
    val keys = d.keys.list()
    val routing = d.keys.routing()
    val resume = d.ats?.getResume()
    val state = d.scanner.state.value
    return buildJsonObject {
      put("appVersion", d.appVersion)
      putJsonObject("searches") {
        put("count", links.size)
        put("broken", links.count { it.scrapeFailureCount >= 3 })
      }
      putJsonObject("jobs") { JobStatus.entries.forEach { s -> put(s.wire, counts[s]) } }
      putJsonObject("aiFilter") {
        put("descriptionSet", filter.prompt.isNotBlank())
        put("skipCompanies", filter.blacklistedCompanies.size)
      }
      putJsonObject("resume") {
        put("uploaded", resume != null)
        put("fileName", resume?.fileName)
        putJsonArray("problems") { resume?.flags?.forEach { add(mcpJson(it)) } }
      }
      putJsonObject("ai") {
        put("mainModel", mcpJson(d.settings.getAiSettings()))
        put("onlineKeys", keys.size)
        put("openCodeCli", routing.openCode.enabled)
        put("antigravityCli", routing.agy.enabled)
        put("backgroundAiPaused", scanner.aiPaused)
      }
      putJsonObject("scans") {
        put("schedule", scanner.cronRule ?: "never")
        put("scanningNow", state.scanning)
        put("lastFinished", state.lastFinishedAt?.let(::iso))
      }
      putJsonArray("toDo") {
        if (links.isEmpty()) add(JsonPrimitive("Add job searches (help: job-searches)."))
        if (filter.prompt.isBlank()) add(JsonPrimitive("Write the AI filter (help: ai-filter)."))
        if (resume == null) add(JsonPrimitive("Upload the resume (upload_resume)."))
        if (keys.isEmpty() && !routing.openCode.enabled && !routing.agy.enabled) {
          add(JsonPrimitive("Optional: add free AI keys to spare this computer (help: ai-keys)."))
        }
        if (scanner.cronRule == null) {
          add(JsonPrimitive("Scans are off: ask the user, then set_scan_schedule."))
        }
      }
      putJsonArray("setupSteps") {
        d.setupSteps().forEach { step ->
          add(
            buildJsonObject {
              put("step", step.name)
              put("state", step.state)
              put("problem", step.problem)
              put("optional", step.optional)
            }
          )
        }
      }
    }
  }
}
