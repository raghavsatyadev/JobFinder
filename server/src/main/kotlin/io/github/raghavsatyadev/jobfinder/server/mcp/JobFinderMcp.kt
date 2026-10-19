package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.server.ai.AddKeyResult
import io.github.raghavsatyadev.jobfinder.server.ai.AiKeyPool
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.Scanner
import io.github.raghavsatyadev.jobfinder.server.service.LinkService
import io.github.raghavsatyadev.jobfinder.shared.api.ApiJson
import io.github.raghavsatyadev.jobfinder.shared.model.AdvancedMatchingConfig
import io.github.raghavsatyadev.jobfinder.shared.model.AiKey
import io.github.raghavsatyadev.jobfinder.shared.model.AiPrompt
import io.github.raghavsatyadev.jobfinder.shared.model.AiRoute
import io.github.raghavsatyadev.jobfinder.shared.model.AiRouting
import io.github.raghavsatyadev.jobfinder.shared.model.AiTask
import io.github.raghavsatyadev.jobfinder.shared.model.FreeAiCatalog
import io.github.raghavsatyadev.jobfinder.shared.model.JobStatus
import io.github.raghavsatyadev.jobfinder.shared.model.PromptChecks
import io.github.raghavsatyadev.jobfinder.shared.model.PromptId
import io.github.raghavsatyadev.jobfinder.shared.model.PromptWarning
import io.github.raghavsatyadev.jobfinder.shared.model.ScannerSettings
import io.github.raghavsatyadev.jobfinder.shared.model.tryable
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository
import java.io.File
import java.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** What the agent tools work on. The desktop builds it from Koin. */
class McpDependencies(
  val jobs: JobsRepository,
  val links: LinksRepository,
  val sites: SitesRepository,
  val linkService: LinkService,
  val matching: MatchingRepository,
  val settings: LocalSettingsRepository,
  val scanner: Scanner,
  val keys: AiKeyPool,
  val prompts: AiPromptsRepository?,
  val ats: AtsRepository?,
  /** The resume's text as the app read it, or `null` without one. */
  val resumeText: suspend () -> String?,
  /** The app's own Help as Markdown. */
  val appHelp: suspend () -> String,
  /** The setup assistant's steps; empty where there is none. */
  val setupSteps: suspend () -> List<McpSetupStep>,
  val appVersion: String,
)

/** One step of the app's setup assistant, as agents see it. */
class McpSetupStep(val name: String, val state: String, val problem: String?, val optional: Boolean)

/**
 * The JobFinder MCP server: tools to craft job searches, the AI filter and the AI instructions, set
 * up AI keys and the resume, plus the help topics as resources and ready-made prompts.
 */
object JobFinderMcp {
  fun server(deps: McpDependencies): McpServer {
    val help = McpHelp(deps.appHelp)
    return McpServer(
      name = "jobfinder",
      version = deps.appVersion.ifBlank { "dev" },
      instructions = INSTRUCTIONS,
      tools = tools(deps, help),
      resources =
        McpHelp.topics.map { topic ->
          McpResource(topic.uri, topic.title, topic.summary) { help.read(topic.id) }
        },
      prompts = McpPrompts.all,
    )
  }

  private val INSTRUCTIONS =
    """
    JobFinder is a desktop app that checks the user's saved job searches for new jobs, filters them with an AI and scores the user's resume against each job. These tools change the user's real app.
    Start with app_status, then read the help topics (list_help_topics, read_help_topic) before setting something up. Ask the user before deleting anything, and never invent facts about the user: ask for skills, roles, locations and salary.
    Searches are scanned on the app's schedule (set_scan_schedule); there is no tool to start a scan.
    """
      .trimIndent()

  private inline fun <reified T> json(value: T): JsonElement = ApiJson.encodeToJsonElement(value)

  private fun tools(d: McpDependencies, help: McpHelp): List<McpTool> = buildList {
    // Status and help
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
        val saved =
          d.settings.updateScannerSettings(d.settings.getScannerSettings().copy(cronRule = rule))
        buildJsonObject {
          put("schedule", saved.cronRule ?: "never")
          put("risky", ScannerSettings.isRisky(saved.cronRule))
        }
      }
    )

    // Job searches
    add(
      McpTool(
        "list_job_boards",
        "The job boards the app reads best (built-in readers). Searches on other boards work too: the AI reads them.",
        readOnly = true,
      ) {
        buildJsonArray {
          d.sites
            .listSites()
            .filter { !it.deprecated }
            .forEach { s ->
              add(
                buildJsonObject {
                  put("name", s.name)
                  putJsonArray("addresses") { s.urls.forEach { add(JsonPrimitive(it)) } }
                  putJsonArray("notSearchPaths") {
                    s.blacklistedPaths.forEach { add(JsonPrimitive(it)) }
                  }
                }
              )
            }
        }
      }
    )
    add(
      McpTool(
        "list_job_searches",
        "The saved job searches (search result page addresses the app checks for new jobs), with failed scans in a row.",
        readOnly = true,
      ) {
        val sites = d.sites.listSites().associateBy { it.id }
        buildJsonArray {
          d.links.listLinks().forEach { l ->
            add(
              buildJsonObject {
                put("id", l.id)
                put("title", l.title)
                put("url", l.url)
                put("board", sites[l.siteId]?.name)
                put("failedScansInARow", l.scrapeFailureCount)
                put("broken", l.scrapeFailureCount >= 3)
                put("lastScanned", iso(l.lastScrapedAt))
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "add_job_search",
        "Saves a job search. The app opens the address in its browser (signed in to the boards) and checks it shows a job list, so this can take up to a minute. Use the full address of a search RESULTS page with the filters set (role, location or remote, newest first, recent days). Read help topic job-searches first.",
        McpSchema.obj(
          "title" to
            McpSchema.string("Short title, for example \"LinkedIn: Senior Android, Remote\"."),
          "url" to McpSchema.string("Full http(s) address of the search results page."),
          "force" to
            McpSchema.boolean(
              "Save even when no job list is recognised (a board the app reads later with AI). Only when the user agrees."
            ),
          "board_name" to McpSchema.string("Name for a board the app doesn't know. Optional."),
          required = listOf("title", "url"),
        ),
      ) {
        val created =
          d.linkService.createLink(
            it.str("title"),
            it.str("url"),
            it.optBool("force") ?: false,
            siteName = it.optStr("board_name")?.takeIf(String::isNotBlank),
          )
        buildJsonObject {
          put("saved", json(created.link))
          put("jobsFoundNow", created.newJobIds.size)
          put("noJobListRecognised", created.parseFailed)
        }
      }
    )
    add(
      McpTool(
        "update_job_search",
        "Changes a saved search's title or address.",
        McpSchema.obj(
          "id" to McpSchema.integer("Search id."),
          "title" to McpSchema.string("New title; leave out to keep."),
          "url" to McpSchema.string("New address; leave out to keep."),
          required = listOf("id"),
        ),
      ) {
        val id = it.long("id")
        val link = d.links.getLink(id) ?: throw McpToolError("No search $id.")
        val title = it.optStr("title")?.trim()?.ifEmpty { null } ?: link.title
        val url = it.optStr("url")?.trim()?.ifEmpty { null } ?: link.url
        json(d.links.updateLink(id, title, url) ?: throw McpToolError("No search $id."))
      }
    )
    add(
      McpTool(
        "delete_job_search",
        "Deletes a saved search. Ask the user first. Jobs it found stay.",
        McpSchema.obj("id" to McpSchema.integer("Search id."), required = listOf("id")),
        destructive = true,
      ) {
        val id = it.long("id")
        d.links.getLink(id) ?: throw McpToolError("No search $id.")
        d.links.deleteLink(id)
        "Deleted search $id."
      }
    )

    // AI filter
    add(
      McpTool(
        "get_ai_filter",
        "The AI filter: the user's description of jobs to filter out, and companies always skipped.",
        readOnly = true,
      ) {
        json(d.matching.getConfig())
      }
    )
    add(
      McpTool(
        "set_ai_filter",
        "Saves the AI filter. New jobs are checked with it, and jobs already in New are checked again in the background. Read help topic ai-filter first and confirm the text with the user.",
        McpSchema.obj(
          "description" to
            McpSchema.string(
              "Plain sentences saying which jobs to filter OUT, at most ${AdvancedMatchingConfig.MAX_PROMPT_LENGTH} characters. Leave out to keep."
            ),
          "skip_companies" to
            McpSchema.strings("The full list of companies to always skip. Leave out to keep."),
          "add_skip_companies" to McpSchema.strings("Companies to add to the list."),
        ),
      ) {
        val current = d.matching.getConfig()
        val description = it.optStr("description")?.trim() ?: current.prompt
        if (description.length > AdvancedMatchingConfig.MAX_PROMPT_LENGTH) {
          throw McpToolError(
            "The description is ${description.length} characters; the most is ${AdvancedMatchingConfig.MAX_PROMPT_LENGTH}."
          )
        }
        val base = it.optStrings("skip_companies") ?: current.blacklistedCompanies
        val companies =
          (base + it.optStrings("add_skip_companies").orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(String::lowercase)
        json(d.matching.updateConfig(AdvancedMatchingConfig(description, companies)))
      }
    )

    // AI instructions
    add(
      McpTool(
        "list_ai_prompts",
        "The instructions the app gives the AI for each kind of work, the app's own text, and the words each must keep. Read help topic ai-prompts first.",
        readOnly = true,
      ) {
        buildJsonArray { prompts(d).listPrompts().forEach { add(describe(it)) } }
      }
    )
    add(
      McpTool(
        "set_ai_prompt",
        "Saves new instructions for one kind of AI work. Only the instructions change: the job, the resume and the answer format stay fixed around them. Returns warnings; fix strong ones before keeping the change.",
        McpSchema.obj(
          "id" to McpSchema.string("Prompt id.", PromptId.entries.map { it.wire }),
          "text" to
            McpSchema.string("The new instructions, at most ${AiPrompt.MAX_LENGTH} characters."),
          "confirm" to
            McpSchema.boolean("The user agreed to change a prompt that reads job boards."),
          required = listOf("id", "text"),
        ),
      ) {
        val id = promptId(it.str("id"))
        val text = it.str("text")
        if (text.length > AiPrompt.MAX_LENGTH) {
          throw McpToolError("Instructions can be up to ${AiPrompt.MAX_LENGTH} characters.")
        }
        val repo = prompts(d)
        val default = repo.listPrompts().first { p -> p.id == id }.defaultText
        val warnings = PromptChecks.check(id, text, default)
        val missing =
          warnings.filterIsInstance<PromptWarning.MissingTerms>().flatMap { w -> w.terms }
        if (missing.isNotEmpty()) {
          throw McpToolError(
            "Not saved: the answer needs these words in the text: ${missing.joinToString()}."
          )
        }
        if (id.drivesParsing && it.optBool("confirm") != true) {
          throw McpToolError(
            "Not saved: this prompt reads job boards and a bad edit can stop jobs coming in. Ask the user, then call again with confirm=true."
          )
        }
        val saved = repo.savePrompt(id, text)
        buildJsonObject {
          put("saved", describe(saved))
          putJsonArray("warnings") { warnings.forEach { w -> add(JsonPrimitive(warning(w))) } }
        }
      }
    )
    add(
      McpTool(
        "reset_ai_prompt",
        "Puts one kind of AI work back on the app's own instructions.",
        McpSchema.obj(
          "id" to McpSchema.string("Prompt id.", PromptId.entries.map { it.wire }),
          required = listOf("id"),
        ),
      ) {
        describe(prompts(d).resetPrompt(promptId(it.str("id"))))
      }
    )
    add(
      McpTool(
        "try_ai_prompt",
        "Runs instructions once on the newest job in New without saving them, and returns what the AI answered. Works for 'matching' and 'ats_review'. Can take a few minutes on the local model.",
        McpSchema.obj(
          "id" to
            McpSchema.string("Prompt id.", PromptId.entries.filter { it.tryable }.map { it.wire }),
          "text" to McpSchema.string("The instructions to try."),
          required = listOf("id", "text"),
        ),
      ) {
        val id = promptId(it.str("id"))
        if (!id.tryable) throw McpToolError("Only matching and ats_review can be tried.")
        json(prompts(d).tryPrompt(id, it.str("text")))
      }
    )

    // AI models and keys
    add(
      McpTool(
        "list_free_ai_providers",
        "Free online AI providers the app supports, none needing a payment card: where to make a key, the model, the free limits, the work each suits and whether it may keep prompts.",
        readOnly = true,
      ) {
        buildJsonArray {
          FreeAiCatalog.TIERS.forEach { t ->
            add(
              buildJsonObject {
                put("provider", t.provider.name.lowercase())
                put("keyPage", t.keyPage)
                put("model", t.model)
                put("requestsPerMinute", t.requestsPerMinute)
                put("requestsPerDay", t.requestsPerDay)
                put("tokensPerDay", t.tokensPerDay)
                putJsonArray("suits") { t.suits.forEach { s -> add(json(s)) } }
                put("mayKeepPrompts", t.keepsPrompts)
              }
            )
          }
        }
      }
    )
    add(
      McpTool(
        "list_ai_keys",
        "The online AI keys added (never the keys themselves), today's use and expiry.",
        readOnly = true,
      ) {
        val usage = d.keys.usageToday().associateBy { it.keyId }
        val now = System.currentTimeMillis()
        buildJsonArray {
          d.keys.list().forEach { k -> add(describe(k, usage[k.id]?.requests ?: 0, now)) }
        }
      }
    )
    add(
      McpTool(
        "add_ai_key",
        "Adds an online AI key; the app checks it with the provider (found from the key's prefix) and keeps it in the computer's protected store. The key passes through this chat, so suggest the user pastes it in Settings → Free online AI instead unless they want you to add it.",
        McpSchema.obj(
          "key" to McpSchema.string("The API key."),
          "expires_in_days" to
            McpSchema.integer(
              "Days until the key expires, as set on the provider's site. Optional."
            ),
          required = listOf("key"),
        ),
      ) {
        val expiresAt =
          it.optLong("expires_in_days")?.let { days ->
            System.currentTimeMillis() + days * AiKey.DAY_MS
          }
        when (val result = d.keys.add(it.str("key"), expiresAt = expiresAt)) {
          is AddKeyResult.Added -> describe(result.key, 0, System.currentTimeMillis())
          AddKeyResult.UnknownProvider ->
            throw McpToolError("The key's prefix doesn't match a supported provider.")
          AddKeyResult.Refused -> throw McpToolError("The provider refused the key.")
        }
      }
    )
    add(
      McpTool(
        "remove_ai_key",
        "Removes an online AI key from the app. Ask the user first.",
        McpSchema.obj(
          "id" to McpSchema.string("Key id from list_ai_keys."),
          required = listOf("id"),
        ),
        destructive = true,
      ) {
        val id = it.str("id")
        if (d.keys.list().none { k -> k.id == id }) throw McpToolError("No key $id.")
        d.keys.remove(id)
        "Removed key $id."
      }
    )
    add(
      McpTool(
        "get_ai_routing",
        "Which models do which work, tried in order, with the main AI model. A task missing from 'routes' uses the default order. Read help topic ai-routing first.",
        readOnly = true,
      ) {
        val keys = d.keys.list()
        val routing = d.keys.routing()
        buildJsonObject {
          put("mainModel", json(d.settings.getAiSettings()))
          put("routing", json(routing))
          putJsonObject("effective") {
            AiTask.entries.forEach { task ->
              put(json(task).toString().trim('"'), json(routing.forTask(task, keys)))
            }
          }
        }
      }
    )
    add(
      McpTool(
        "set_ai_routing",
        "Saves which models do which work: the same shape as get_ai_routing's 'routing'. Routes name a key id (from list_ai_keys), \"opencode-cli\", \"agy-cli\", or no keyId for the main model. Work with the resume (ats, tips, answers) should stay on the main model unless the user chooses otherwise.",
        McpSchema.obj(
          "routing" to McpSchema.any("The routing object."),
          required = listOf("routing"),
        ),
      ) {
        val routing =
          try {
            ApiJson.decodeFromJsonElement(AiRouting.serializer(), it.obj("routing"))
          } catch (e: SerializationException) {
            throw McpToolError("Not a routing object: ${e.message}")
          }
        val ids = d.keys.list().map { k -> k.id }.toSet()
        val unknown =
          routing.routes.values
            .flatten()
            .mapNotNull { r -> r.keyId }
            .filter { k ->
              k !in ids && k != AiRoute.OPEN_CODE_ID && k != AiRoute.AGY_ID
            }
        if (unknown.isNotEmpty()) throw McpToolError("Unknown key ids: ${unknown.distinct()}")
        d.keys.setRouting(routing)
        json(d.keys.routing())
      }
    )

    // Resume
    add(
      McpTool(
        "get_resume",
        "The resume the app scores jobs against: file name, pages, words, problems found, and its text as the app read it.",
        readOnly = true,
      ) {
        val info =
          d.ats?.getResume()
            ?: return@McpTool "No resume yet. Upload one with upload_resume or in Settings → Resume."
        buildJsonObject {
          put("info", json(info))
          put("text", d.resumeText().orEmpty())
        }
      }
    )
    add(
      McpTool(
        "upload_resume",
        "Replaces the resume with a file on this computer (PDF, DOCX or TXT). ATS scores are made again for it.",
        McpSchema.obj(
          "path" to McpSchema.string("Absolute path of the file."),
          required = listOf("path"),
        ),
      ) {
        val ats = d.ats ?: throw McpToolError("The resume isn't available in this app.")
        val file = File(it.str("path"))
        if (!file.isFile) throw McpToolError("No file at ${file.path}.")
        if (file.extension.lowercase() !in setOf("pdf", "docx", "txt")) {
          throw McpToolError("Use a PDF, DOCX or TXT file.")
        }
        if (file.length() > MAX_RESUME_BYTES) throw McpToolError("The file is over 10 MB.")
        json(ats.uploadResume(file.name, file.readBytes()))
      }
    )
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
      putJsonObject("jobs") {
        JobStatus.entries.forEach { s -> put(s.wire, counts[s]) }
      }
      putJsonObject("aiFilter") {
        put("descriptionSet", filter.prompt.isNotBlank())
        put("skipCompanies", filter.blacklistedCompanies.size)
      }
      putJsonObject("resume") {
        put("uploaded", resume != null)
        put("fileName", resume?.fileName)
        putJsonArray("problems") { resume?.flags?.forEach { add(json(it)) } }
      }
      putJsonObject("ai") {
        put("mainModel", json(d.settings.getAiSettings()))
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

  private fun prompts(d: McpDependencies): AiPromptsRepository =
    d.prompts ?: throw McpToolError("AI instructions aren't available in this app.")

  private fun promptId(wire: String): PromptId =
    PromptId.fromWire(wire)
      ?: throw McpToolError(
        "Unknown prompt \"$wire\". Use one of ${PromptId.entries.map { it.wire }}."
      )

  private fun describe(p: AiPrompt) = buildJsonObject {
    put("id", p.id.wire)
    put("edited", p.edited)
    put("readsJobBoards", p.id.drivesParsing)
    put("canTry", p.id.tryable)
    putJsonArray("mustKeepWords") { p.id.requiredTerms.forEach { add(JsonPrimitive(it)) } }
    put("text", p.text)
    if (p.edited) put("defaultText", p.defaultText)
  }

  private fun describe(k: AiKey, requestsToday: Int, now: Long) = buildJsonObject {
    put("id", k.id)
    put("provider", k.provider.name.lowercase())
    put("label", k.label)
    put("model", k.defaultModel)
    put("requestsPerMinute", k.requestsPerMinute)
    put("requestsPerDay", k.requestsPerDay)
    put("requestsToday", requestsToday)
    put("expires", k.expiresAt?.let(::iso))
    put("expired", k.expired(now))
  }

  private fun warning(w: PromptWarning): String =
    when (w) {
      PromptWarning.Empty -> "No instructions at all."
      is PromptWarning.MissingTerms ->
        "The answer needs these words, now missing: ${w.terms.joinToString()}."
      PromptWarning.MentionsFormat ->
        "The text talks about the answer's format, which the app fixes; it may confuse the model."
      PromptWarning.DrivesParsing ->
        "This prompt reads job boards: a bad edit can stop jobs coming in."
      PromptWarning.ResultsChange -> "Results will differ from before."
    }

  private fun iso(millis: Long): String? =
    if (millis <= 0) null else Instant.ofEpochMilli(millis).toString()

  private const val MAX_RESUME_BYTES = 10L * 1024 * 1024
}
