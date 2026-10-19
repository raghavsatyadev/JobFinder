package io.github.raghavsatyadev.jobfinder.server.mcp

/** One help topic for agents. The Markdown is in `resources/mcp-help/<id>.md`. */
class McpHelpTopic(val id: String, val title: String, val summary: String) {
  val uri: String
    get() = "jobfinder://help/$id"
}

/**
 * The help agents read: guides written for agents (bundled Markdown), and [APP], the app's own Help
 * (its guides and questions) in the app's language, from [appHelp].
 */
class McpHelp(private val appHelp: suspend () -> String) {
  suspend fun read(id: String): String =
    if (id == APP) appHelp()
    else
      TOPICS.firstOrNull { it.id == id }?.let(::bundled)
        ?: throw McpToolError("No help topic \"$id\". Use one of ${topics.map { it.id }}.")

  private fun bundled(topic: McpHelpTopic): String =
    McpHelp::class.java.getResourceAsStream("/mcp-help/${topic.id}.md")?.use {
      it.readBytes().decodeToString()
    } ?: error("Help topic ${topic.id} is missing from the app")

  companion object {
    const val APP = "app"

    private val TOPICS =
      listOf(
        McpHelpTopic(
          "getting-started",
          "Set up JobFinder",
          "What the app does, its job lists, and the setup checklist. Read first.",
        ),
        McpHelpTopic(
          "job-searches",
          "Craft job searches",
          "Which search result pages to save, how to build their addresses, and broken searches.",
        ),
        McpHelpTopic(
          "ai-filter",
          "Write the AI filter",
          "How the AI filter decides, and how to write its description and skip list.",
        ),
        McpHelpTopic(
          "ai-prompts",
          "Edit the AI instructions",
          "Each kind of AI work's editable instructions and the rules for changing them.",
        ),
        McpHelpTopic(
          "ai-keys",
          "AI models and free keys",
          "The local model, free online keys (no card) and the no-key CLIs.",
        ),
        McpHelpTopic(
          "ai-routing",
          "Which model does which work",
          "The order models are tried in for each task, and its JSON shape.",
        ),
        McpHelpTopic(
          "resume-ats",
          "Resume and ATS scores",
          "What the app checks in the resume, the ATS review, and rewrite rules.",
        ),
        McpHelpTopic("phone", "Pair the Android phone", "Connect the phone over Wi-Fi."),
        McpHelpTopic(
          "browser-extension",
          "Browser extension",
          "What JobFinder Helper does and how to install it.",
        ),
        McpHelpTopic("backups", "Backups and moving", "Export, restore and move computers."),
        McpHelpTopic(
          "connect-agent",
          "Connect an AI agent",
          "This MCP server's address, token and setup for Claude Code, Gemini CLI, Codex, Cursor.",
        ),
      )

    /** Every topic, the app's own Help last. */
    val topics: List<McpHelpTopic> =
      TOPICS +
        McpHelpTopic(
          APP,
          "The app's Help",
          "The in-app Help as the user sees it: step-by-step guides, questions and answers, shortcuts.",
        )
  }
}
