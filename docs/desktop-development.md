# Desktop editor and optional MCP setup

Checked against official documentation on 17 October 2026. No credentials are embedded and existing user-level configuration is not overwritten.

Use rust-analyzer, ESLint and Prettier in editors that support them. CodeLLDB is recommended when native debugging is needed. The desktop project includes an extension recommendations file; extensions are development tools, not shipped dependencies.

This session already has GitHub, documentation/web tools and computer/browser tools. Playwright is installed as a project test dependency. Optional MCPs are not needed for application runtime or the first build.

Read-only local inspection found both Codex and Antigravity configuration files present. Only Codex MCP section names were inspected; no credentials were printed or copied and neither global configuration was changed.

## Codex

[Official MCP configuration](https://developers.openai.com/codex/mcp/) uses `config.toml` and `[mcp_servers.NAME]`. Inspect current entries with `codex mcp list` before adding duplicates. User config is usually `~/.codex/config.toml`; project config support follows the official configuration guide and trust rules. The application’s provided connectors may already cover the same need.

Optional examples (not automatically applied):

```toml
[mcp_servers.context7]
command = "npx"
args = ["-y", "@upstash/context7-mcp"]

[mcp_servers.playwright]
command = "npx"
args = ["-y", "@playwright/mcp@latest"]
```

On Windows, if an editor cannot execute `npx` as a stdio process, use `command = "cmd"` with `args = ["/c", "npx", ...]` as described by that server's setup instructions. Keep authentication in environment variables or the connector's login flow.

## Antigravity

Antigravity has **separate configuration**. Follow [Google's current MCP instructions](https://antigravity.google/docs/mcp). In the IDE: agent panel `…` → MCP Servers → Manage MCP Servers → View raw config. The current docs identify global `~/.gemini/config/mcp_config.json` and workspace `.agents/mcp_config.json`; older installations may differ, so use the editor's raw-config action rather than guessing its location. The JSON root is `mcpServers`; remote servers use `serverUrl` in the documented current schema.

```json
{
  "mcpServers": {
    "context7": { "command": "npx", "args": ["-y", "@upstash/context7-mcp"] },
    "playwright": { "command": "npx", "args": ["-y", "@playwright/mcp@latest"] }
  }
}
```

Merge chosen entries into the existing object; do not replace existing servers. Do not add tokens to committed files. No MCP changes are required here because equivalent tools are already available.

Primary server instructions: [Context7](https://github.com/upstash/context7), [Playwright MCP](https://github.com/microsoft/playwright-mcp), [GitHub MCP](https://github.com/github/github-mcp-server). Prefer an existing GitHub connector over another authenticated server. If using the official GitHub MCP separately, follow its current OAuth/environment instructions and request only needed repository permissions.

If Codex and Antigravity work concurrently, assign explicit file/task ownership and use separate branches/worktrees. Do not share build-output directories or concurrently mutate lockfiles.
