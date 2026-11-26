package io.github.raghavsatyadev.jobfinder.server.mcp

import io.github.raghavsatyadev.jobfinder.server.ai.AiKeyPool
import io.github.raghavsatyadev.jobfinder.server.ai.AiService
import io.github.raghavsatyadev.jobfinder.server.repository.LocalSettingsRepository
import io.github.raghavsatyadev.jobfinder.server.scan.Scanner
import io.github.raghavsatyadev.jobfinder.server.service.LinkService
import io.github.raghavsatyadev.jobfinder.shared.repository.AiPromptsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.AtsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.FormAnswersRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.JobsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.LinksRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.MatchingRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.ResumePdfRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SettingsRepository
import io.github.raghavsatyadev.jobfinder.shared.repository.SitesRepository

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
  /**
   * Settings for the port and network: the UI's repository, which moves the API server at once,
   * where there is one.
   */
  val serverSettings: () -> SettingsRepository = { settings },
  /** The main AI choice and its keys; `null` where there is none. */
  val ai: () -> AiService? = { null },
  val resumePdf: () -> ResumePdfRepository? = { null },
  val formAnswers: () -> FormAnswersRepository? = { null },
)

/** One step of the app's setup assistant, as agents see it. */
class McpSetupStep(val name: String, val state: String, val problem: String?, val optional: Boolean)

/**
 * The JobFinder MCP server: tools to read and change every setting the user can edit (searches, the
 * AI filter, scanning, AI models, keys and routing, AI instructions, the resume, the job page
 * layout, form answers, the phone API), plus the help topics as resources and ready-made prompts.
 */
object JobFinderMcp {
  fun server(deps: McpDependencies): McpServer {
    val help = McpHelp(deps.appHelp)
    return McpServer(
      name = "jobfinder",
      version = deps.appVersion.ifBlank { "dev" },
      instructions = INSTRUCTIONS,
      tools =
        McpStatusTools.tools(deps, help) +
          McpSettingsTools.tools(deps) +
          McpSearchTools.tools(deps) +
          McpPromptTools.tools(deps) +
          McpAiTools.tools(deps) +
          McpResumeTools.tools(deps),
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
    Start with app_status and get_all_settings, then read the help topics (list_help_topics, read_help_topic) before setting something up. Every setting the user can edit in the app has a get and a set tool. Ask the user before deleting anything, and never invent facts about the user: ask for skills, roles, locations and salary.
    Searches are scanned on the app's schedule (set_scan_schedule); there is no tool to start a scan. API keys are never shown.
    """
      .trimIndent()
}
