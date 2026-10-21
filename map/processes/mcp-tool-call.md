---
type: process
status: verified
verified: 2026-10-13
at: 3532829
consumes: [../objects/identity/caller.md, ../objects/agents/agent.md, ../objects/agents/agent-mcp-server.md, ../objects/todos/todo.md, ../objects/platform/settings.md]
produces: [../objects/agents/activity.md, ../objects/todos/todo.md]
---

# mcp-tool-call

An MCP client talks to one agent at `POST /mcp?agent=<id>`; agent-service decides per request which tools exist and which may run.

## Input → Movement → Output

A JSON-RPC request with a bearer (owner login or that agent's AGENT token) reaches agent-service's stateless
`/mcp`. `tools/list` returns the agent's built-in tools plus every attached server's tools that the row's READ/WRITE
allows, renamed `server__tool`; `tools/call` re-reads the row, re-checks, connects afresh and relays. Every
connect and call lands in the agent's activity log; todo-service's own `/mcp` answers as the forwarded caller.

## Why this shape

Permission is checked twice on purpose: at list time so a READ agent never even sees writing tools, and at call
time against the row as it is now, so an owner's flip bites on the next call and an unlisted tool is still
refused. "Read-only" is the server's own `readOnlyHint` only for trusted servers; a URL an owner typed in could
annotate anything, so there the owner's `readOnlyTools` list rules. The caller's token is the owner's whole
authority, so it is forwarded only to trusted servers.

## Steps

1. Servlet at `/mcp`, context = `Authorization` header + `agent` param, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:65`; dispatch `:102` (ping, initialize, tools/list, tools/call; errors as JSON-RPC errors `:113`).
2. Per request: `jwt.callerOf` (401), agent id from the param or the AGENT token's claim, `mayActAs` else 404, owner lookup else 404, `AgentMcpEndpoint.java:137`.
3. `initialize` records CONNECTED with the client name, returns the agent's instructions, `AgentMcpEndpoint.java:158`.
4. `tools/list` = built-ins gated by `othersAccess` + downstream tools, `AgentMcpEndpoint.java:168`; built-ins `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentTools.java:57`.
5. Downstream listing per server, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:65`: offered only if `Access.allows(readOnly)` `:72` (`apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/Access.java:10`); `readOnly` = `readOnlyHint` if trusted, else owner's `readOnlyToolSet`, `McpTools.java:219`; names truncated to 64 and clashes dropped `:227`, `:92`; an unreachable server is logged once and skipped `:81`.
6. Listings cached 15 s per URL + credential, URL policy run first even on a hit, `McpTools.java:55`, `:118`.
7. `tools/call`, `AgentMcpEndpoint.java:175`: built-in → `AgentTools.call` re-checks `othersAccess` and forbids self-edit, `AgentTools.java:85`; else `McpTools.call` `McpTools.java:97`: server by longest name prefix `:140`, tool re-matched from a fresh-or-cached listing `:99`, WRITE re-checked against the row `:105`, new client per call `:108`.
8. Credential, `McpTools.java:132`: forwarded caller bearer only when the URL is trusted at that moment, else the stored header. Saving a forward to an untrusted URL is refused too, `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:204`; an add that leaves it unsaid forwards to a trusted URL given no header, `AgentService.java:114-117`.
9. Outbound: `checkBeforeConnect` re-resolves the host (no private addresses unless trusted), `libs/outbound-urls/src/main/java/com/demo/outbound/UrlPolicy.java:75`, called `McpTools.java:156`; one shared HTTP/1.1 client, redirects off, `:187`. Trusted = exact match on `agents.trusted-server-urls`, `apps/agent-service/src/main/java/com/demo/agentservice/agent/ServerUrls.java:31`, compose default `compose.yaml:137`.
10. Activity: TOOL_CALL or TOOL_DENIED line per call, a log failure never fails the call, `AgentMcpEndpoint.java:196` → `apps/agent-service/src/main/java/com/demo/agentservice/activity/ActivityLog.java:19`.
11. todo-service's `/mcp`: `libs/mcp-server/src/main/java/com/demo/mcp/server/McpEndpoint.java:34`; each tool resolves the caller from the forwarded header and maps a refusal to an error result, `McpEndpoint.java:58`; four tools, only `list_todos` marked `readOnlyHint`, `apps/todo-service/src/main/java/com/demo/todoservice/mcp/TodoMcpServer.java:48`, `:54`. It accepts AGENT tokens (it uses `JwtVerifier` directly, not `CallerResolver`).

## If you change this

- **Hits:** a tool's `readOnlyHint` on any trusted server (todo-service's `TodoMcpServer`) changes what a READ agent may call; `agents.trusted-server-urls` / `AGENTS_TRUSTED_SERVER_URLS` changes both forwarding and the READ rule; `offeredName` changes every tool name clients have cached; `Access`/`OthersAccess` semantics; `UrlPolicy` (shared by save and connect).
- **Does not hit:** the web pages (they only edit rows over `/api/agents`); auth-service (tokens are verified in process); todo-service's REST API, which still 403s agent tokens.

## Surfaces

| Surface | Role |
|---|---|
| `POST /mcp?agent=<id>` (agent-service; nginx `docker/nginx.conf:22`) | the agent, seen by an MCP client |
| `http://todo-service:1002/mcp` | trusted downstream, compose network only |
| owner-attached URLs | untrusted downstream, public addresses only |

## See

- Objects: [agent](../objects/agents/agent.md), [agent-mcp-server](../objects/agents/agent-mcp-server.md), [activity](../objects/agents/activity.md), [caller](../objects/identity/caller.md), [todo](../objects/todos/todo.md)
- Source: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/`
