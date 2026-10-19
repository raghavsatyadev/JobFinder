package io.github.raghavsatyadev.jobfinder.server.mcp

/** Ready-made requests the user starts in their agent (slash commands in most agents). */
object McpPrompts {
  private val FOCUS =
    McpPromptArgument(
      "focus",
      "Roles, places or anything else to start from, for example \"senior Android, remote, India\".",
    )

  val all: List<McpPrompt> =
    listOf(
      McpPrompt(
        "set_up_jobfinder",
        "Walk through setting up JobFinder: resume, job searches, AI filter, AI models and schedule.",
        listOf(FOCUS),
      ) { args ->
        """
        Help me set up JobFinder with its MCP tools.
        1. Call app_status and read the help topic getting-started.
        2. Go through what is missing, one step at a time, and ask me before each change. Read the step's help topic first.
        3. Ask me for facts (roles, seniority, places or remote, lowest salary, skills, companies to avoid); read get_resume when there is one. Never invent them.
        4. Tell me what only I can do in the app (signing in to the job boards, pairing the phone) and how.
        5. End with app_status again and a short list of what's done and what's left.
        ${focus(args)}
        """
          .trimIndent()
      },
      McpPrompt(
        "craft_job_searches",
        "Build and save 10 to 15 job searches for the user's skills and places.",
        listOf(FOCUS),
      ) { args ->
        """
        Help me build my JobFinder job searches.
        1. Read the help topic job-searches, then list_job_boards and list_job_searches.
        2. Read get_resume, and ask me up to 5 short questions about roles, seniority, places or remote, and boards I like.
        3. Propose 10 to 15 searches (title and full results-page address, newest first, recent jobs only) across the big and niche boards of my field and country, skipping ones I already have. Show me the list before saving.
        4. Save the ones I approve with add_job_search and tell me which failed and why.
        ${focus(args)}
        """
          .trimIndent()
      },
      McpPrompt(
        "tune_ai_filter",
        "Write or improve the AI filter that moves unwanted jobs to Filtered out.",
        listOf(FOCUS),
      ) { args ->
        """
        Help me write JobFinder's AI filter.
        1. Read the help topic ai-filter, then get_ai_filter and get_resume.
        2. Ask me up to 5 short questions: roles and seniority, lowest salary with currency, places and work modes, skills or stacks to avoid, domains or company types to skip.
        3. Write the description (plain sentences saying what to filter out, at most 1,500 characters) and the companies to skip. Show me both.
        4. Try it with try_ai_prompt id=matching on the newest job and show me the answer.
        5. Save it with set_ai_filter once I agree.
        ${focus(args)}
        """
          .trimIndent()
      },
    )

  private fun focus(args: Map<String, String>): String =
    args["focus"]?.takeIf { it.isNotBlank() }?.let { "Start from: $it" }.orEmpty()
}
