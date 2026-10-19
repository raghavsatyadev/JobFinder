# Connect an AI agent to JobFinder

JobFinder runs an MCP server on this computer only: `http://127.0.0.1:47822/mcp` (Streamable HTTP, JSON answers). Every request needs the agent token as `Authorization: Bearer <token>`. In the desktop app, Settings → AI agents (MCP) shows the address and copies a ready setup with the token. "New token" stops agents set up with the old one.

Examples (replace `<token>`):
- **Claude Code:** `claude mcp add --scope user --transport http jobfinder http://127.0.0.1:47822/mcp --header "Authorization: Bearer <token>"`
- **Gemini CLI** (`~/.gemini/settings.json`):
  ```json
  {"mcpServers": {"jobfinder": {"httpUrl": "http://127.0.0.1:47822/mcp", "headers": {"Authorization": "Bearer <token>"}}}}
  ```
- **Cursor** (`~/.cursor/mcp.json`) and most other apps:
  ```json
  {"mcpServers": {"jobfinder": {"url": "http://127.0.0.1:47822/mcp", "headers": {"Authorization": "Bearer <token>"}}}}
  ```
- **Codex CLI** (`~/.codex/config.toml`):
  ```toml
  [mcp_servers.jobfinder]
  url = "http://127.0.0.1:47822/mcp"
  http_headers = { "Authorization" = "Bearer <token>" }
  ```

The server runs while the desktop app is open. Tools change the user's real app. The prompts `set_up_jobfinder`, `craft_job_searches` and `tune_ai_filter` start guided sessions.
