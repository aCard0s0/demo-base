---
type: object
cluster: agents
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/Agent.java
---

# Agent

A user-owned MCP server at `/mcp?agent=<id>` (the `Agent` entity, table `agents`): a name, instructions, a list of
[MCP servers](agent-mcp-server.md), and `othersAccess` for how far it reaches into its owner's other agents. No
model runs here.

## Why this shape

The servers live inside the agent (cascade, orphan removal), so the one owner check on the agent covers them too
(`apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/Agent.java:22-26`, `:52-55`). Ownership is
**strictly the owner's**: no role reads everybody's agents, an admin included, and another user's id is 404
everywhere (`apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/AgentRepository.java:9-11`,
`apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:64-68`). `AgentService.get` has
no role branch, unlike `TodoService.writable`.

## Shape

- `owner`, `name`, `instructions` (8000), `othersAccess`, all not null: `apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/Agent.java:38-50`
- Table and the `others_access` check constraint (`NONE`, `READ`, `WRITE`): `apps/agent-service/src/main/resources/db/changelog/changes/001-init.sql:8-14`
- Schema is Liquibase, Hibernate only validates: `apps/agent-service/src/main/resources/application.properties:25`, `apps/agent-service/src/main/resources/db/changelog/db.changelog-master.yaml:2-8`
- Body limits (name 1-100, instructions up to 8000) are DTO annotations checked in the service: `apps/agent-service/src/main/java/com/demo/agentservice/agent/dto/NewAgent.java:8-10`, `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:187-193`
- `othersAccess` defaults to `NONE` on create: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:74-75`
- `OthersAccess`: `NONE`, `READ` (reads), `WRITE` (writes): `apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/OthersAccess.java:4-14`
- It gates seven built-in tools, re-read every call; an agent may never change itself: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentTools.java:39-40`, `:85-95`
- `instructions` go out as the MCP `initialize` result's instructions: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:163-165`
- REST `/api/agents` refuses an agent token with 403: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentController.java:26-29`, `libs/auth-client/src/main/java/com/demo/auth/client/CallerResolver.java:48-49`
- Delete removes its activity in the same transaction: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:102-106`

## Connected to

- **owns:** [AgentMcpServer](agent-mcp-server.md) rows; its [Activity](activity.md) log (by plain `agentId`, no FK)
- **owned-by:** a [User](../identity/user.md) id, from the [Caller](../identity/caller.md)
- **joins:** an agent [Token](../identity/token.md) pinned to this id (`apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentController.java:95-112`) and its [AgentTokenVersion](../identity/agent-token-version.md)
- **looks-like-but-is-not:** the `AGENT` [Role](../identity/role.md), which only ever exists on a token

## If you change this

- **Hits:** a new column needs `Agent.java` plus a new `changes/003-*.sql` included from the master, or the context refuses to start (`ddl-auto=validate`). Then `AgentResponse.java:11-16`, the DTOs, and `apps/web/src/app/agents/agents.ts:28-34`. A new `OthersAccess` value hits the `others_access` check constraint (`001-init.sql:13`) through a changeset. Validation does not catch it: the insert fails at runtime. It also hits `AgentTools.defs`/`call`.
- **Does not hit:** admin visibility. No role branch exists, so do not add one to `AgentRepository` thinking it mirrors todos. Not the auth-service schema: an agent is not a user row.

## Surfaces

| Surface | Role |
|---|---|
| [Web app](../platform/web-app.md) agents pages (`apps/web/src/app/agents/agents.ts:92-108`) | reads, writes |
| another agent of the same owner, via `AgentTools` | reads/writes per `othersAccess` |
| external MCP client at `/mcp?agent=<id>` | reads `instructions` on `initialize` |

## See

- Source: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java`
