# AI agents (MCP)

AI agents on your computer (Claude Code, Gemini CLI, Codex, Cursor and other MCP apps) can set
JobFinder up with you: craft your searches, write the AI filter and AI instructions, add free AI
keys, check the setup and read the app's help.

JobFinder runs an MCP server on your computer only, at `http://127.0.0.1:47822/mcp`, while the
desktop app is open. Every request needs the agent token.

## Connect an agent

1. Open **Settings → AI agents (MCP)** and turn the server on.
2. Copy the setup for your agent; it has the token in it. Or use one of these, with your token in
   place of `<token>`:

**Claude Code**

```
claude mcp add --scope user --transport http jobfinder http://127.0.0.1:47822/mcp --header "Authorization: Bearer <token>"
```

**Gemini CLI** (`~/.gemini/settings.json`)

```json
{"mcpServers": {"jobfinder": {"httpUrl": "http://127.0.0.1:47822/mcp", "headers": {"Authorization": "Bearer <token>"}}}}
```

**Cursor** (`~/.cursor/mcp.json`) and most other apps

```json
{"mcpServers": {"jobfinder": {"url": "http://127.0.0.1:47822/mcp", "headers": {"Authorization": "Bearer <token>"}}}}
```

**Codex CLI** (`~/.codex/config.toml`)

```toml
[mcp_servers.jobfinder]
url = "http://127.0.0.1:47822/mcp"
http_headers = { "Authorization" = "Bearer <token>" }
```

## Use it

Ask the agent to "set up JobFinder", "craft job searches for me" or "tune my AI filter". It asks
for your target roles, places, salary and skills, and confirms before it deletes searches or keys
or replaces your filter. Signing in to the job sites is the one step only you can do.

The tools change your real app. **New token** stops every agent set up with the old one.
