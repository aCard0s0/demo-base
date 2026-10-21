---
type: object
cluster: agents
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/agent-service/src/main/java/com/demo/agentservice/activity/Activity.java
---

# Activity

One plain-text line of an [Agent](agent.md)'s history (`Activity`, table `agent_activity`), written through
`ActivityLog` and read through `ActivityRepository`.

## Why this shape

The log is read far more than it is joined, so `agentId` is a plain column, not a foreign key. It is deleted with
its agent by the service (`apps/agent-service/src/main/java/com/demo/agentservice/activity/Activity.java:19-20`,
`apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:105`). An `(agent_id, id)` index
lets "last hundred" walk backwards and stop (`apps/agent-service/src/main/resources/db/changelog/changes/002-agent-activity-index.sql:3-8`).

## Shape

- Columns `agentId`, `at`, `kind`, `detail` (up to 2000, cut with `…`): `apps/agent-service/src/main/java/com/demo/agentservice/activity/Activity.java:34-57`
- Table: `apps/agent-service/src/main/resources/db/changelog/changes/001-init.sql:30-36`; `kind` is free `varchar`, no check constraint
- Four kinds: `apps/agent-service/src/main/java/com/demo/agentservice/activity/Activity.java:29-32`
  - `connected`: MCP `initialize`, client name and version: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:160-161`
  - `tool_call`: a tool ran, `-> ok:` / `-> error:`: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:180-184`, `:189-191`. Also an unreachable server, deduped by `recordOnce`: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:84-86`
  - `tool_denied`: an `AccessDenied` and why: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:185-188`
  - `config_changed`: create and every edit, prefixed `by agent '<name>' (#id): ` when another agent did it: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:77`, `:176-181`, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentTools.java:51`. Also token issued and revoked: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentController.java:99`, `:111`
- A log write that fails after a tool ran is swallowed, never turned into a failure: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:194-200`
- Reads are the last 100, newest first: `apps/agent-service/src/main/java/com/demo/agentservice/activity/ActivityLog.java:32-35`, `apps/agent-service/src/main/java/com/demo/agentservice/activity/ActivityRepository.java:11`. Delete is one statement: `apps/agent-service/src/main/java/com/demo/agentservice/activity/ActivityRepository.java:13-16`

## Connected to

- **owned-by:** [Agent](agent.md), by `agentId` (no FK)
- **joins:** [AgentMcpServer](agent-mcp-server.md): server edits and tool calls land here; [Token](../identity/token.md) issue and revoke
- **looks-like-but-is-not:** the server log (slf4j). The activity log is the owner's, shown on the page; the server log gets only what could not be recorded

## If you change this

- **Hits:** a new kind needs a constant in `Activity.java` and the web filter list `apps/web/src/app/agents/agents.ts:44`. The badge rule `apps/web/src/app/agents/agents.ts:47-49` keys on `tool_denied` and `tool_call` text. Wider `detail` needs `MAX_DETAIL` plus a Liquibase changeset widening `agent_activity.detail` (`001-init.sql:35`). Changing the read window touches `findTop100…`, and `recordOnce` dedupes against that same window (`ActivityLog.java:23-30`).
- **Does not hit:** the database for a new kind (free `varchar`, no constraint). Not the `@Index` on the entity for Postgres: Liquibase `002` builds the index and validation does not check it.

## Surfaces

| Surface | Role |
|---|---|
| [Web app](../platform/web-app.md) agent detail (`apps/web/src/app/agents/agents.ts:124`) | reads |
| `GET /api/agents/{id}/activity`, owner only (`apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentController.java:115-118`) | reads |
| another agent via `get_agent_activity` (`apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentTools.java:102-107`) | reads, with `othersAccess` READ |
| `AgentMcpEndpoint`, `McpTools`, `AgentService`, `AgentController` | write |

## See

- Source: `apps/agent-service/src/main/java/com/demo/agentservice/activity/ActivityLog.java`
