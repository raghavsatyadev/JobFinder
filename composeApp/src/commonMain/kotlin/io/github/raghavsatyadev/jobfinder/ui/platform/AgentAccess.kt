package io.github.raghavsatyadev.jobfinder.ui.platform

import kotlinx.coroutines.flow.StateFlow

/** Where AI agents reach the app's MCP server. */
data class AgentEndpoint(
  /** The server's address, or `null` while it is off. */
  val url: String?,
  /** Another program has the port. */
  val portInUse: Boolean = false,
)

/**
 * The MCP server AI agents on this computer use to set the app up. Bound on desktop only (resolve
 * it with `getOrNull()`).
 */
interface AgentAccess {
  val endpoint: StateFlow<AgentEndpoint>

  /** The agent token, which every request needs. */
  fun token(): String

  /** Replaces the token; agents set up with the old one stop working. */
  fun rotateToken()
}

/** The agents the settings copy a ready setup for. */
enum class AgentClient {
  CLAUDE_CODE,
  GEMINI_CLI,
  CODEX,
  /** Cursor, VS Code and most other apps: the common `mcpServers` JSON. */
  OTHER,
}

/** The setup text each agent takes, with the token in it. */
object AgentSetups {
  fun text(client: AgentClient, url: String, token: String): String =
    when (client) {
      AgentClient.CLAUDE_CODE ->
        "claude mcp add --scope user --transport http jobfinder $url " +
          "--header \"Authorization: Bearer $token\""
      AgentClient.GEMINI_CLI -> json("httpUrl", url, token)
      AgentClient.CODEX ->
        """
        |[mcp_servers.jobfinder]
        |url = "$url"
        |http_headers = { "Authorization" = "Bearer $token" }
        """
          .trimMargin()
      AgentClient.OTHER -> json("url", url, token)
    }

  private fun json(urlKey: String, url: String, token: String): String =
    """
    |{
    |  "mcpServers": {
    |    "jobfinder": {
    |      "$urlKey": "$url",
    |      "headers": { "Authorization": "Bearer $token" }
    |    }
    |  }
    |}
    """
      .trimMargin()
}
