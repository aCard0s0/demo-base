---
type: object
cluster: agents
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/AgentMcpServer.java
---

# AgentMcpServer

One downstream MCP server an [Agent](agent.md) may call, and how far: `READ` or `WRITE` (`AgentMcpServer`, table
`agent_mcp_servers`). The UI and docs call it a "server".

## Why this shape

READ must mean "only tools that read". A trusted server's own `readOnlyHint` is believed. Any URL an owner typed in
could annotate anything, so its owner's `readOnlyTools` list counts instead
(`apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:215-224`). The caller's token is the
owner's whole authority, so it is forwarded only to trusted URLs, checked on save and again at use
(`apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:195-204`, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:132-137`).

## Shape

- `name` unique per agent, because it prefixes every offered tool as `server__tool`: `apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/AgentMcpServer.java:36`, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:227-230`
- Columns `url`, `authHeader` (encrypted), `forwardCallerToken`, `access`, `readOnlyTools` (comma separated): `apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/AgentMcpServer.java:52-68`
- Table, `access` check constraint, `unique (agent_id, name)`: `apps/agent-service/src/main/resources/db/changelog/changes/001-init.sql:16-26`
- `Access.allows`: WRITE allows all, READ only read-only tools: `apps/agent-service/src/main/java/com/demo/agentservice/agent/entities/Access.java:7-12`. It is enforced on both `tools/list` and `tools/call`: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:72`, `:105-107`
- Body limits: name `^[a-z0-9_-]{1,20}$`, url 255, authHeader 1400 printable ASCII: `apps/agent-service/src/main/java/com/demo/agentservice/agent/dto/NewMcpServer.java:17-27`
- `trusted` = URL on `agents.trusted-server-urls`, exact string match: `apps/agent-service/src/main/java/com/demo/agentservice/agent/ServerUrls.java:31-35`, `libs/outbound-urls/src/main/java/com/demo/outbound/UrlPolicy.java:86-88`. Default `apps/agent-service/src/main/resources/application.properties:36`, compose `compose.yaml:117`
- Any other URL must be http(s), dotted, and resolve only to public addresses. That is checked on save and before every connect: `libs/outbound-urls/src/main/java/com/demo/outbound/UrlPolicy.java:54-111`, `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:113`, `:136`
- `authHeader` is AES-256-GCM `v1:` at rest via `AuthHeaderCrypto` (`agents.auth-header-key`; blank refuses to start): `apps/agent-service/src/main/java/com/demo/agentservice/agent/AuthHeaderCrypto.java:14-21`, `libs/jpa-crypto/src/main/java/com/demo/jpa/crypto/EncryptedText.java:36`, `:51-54`. Legacy plain rows are encrypted at boot: `apps/agent-service/src/main/java/com/demo/agentservice/agent/AuthHeaderMigration.java:19-33`
- The header never leaves; responses carry `hasAuthHeader`, `trusted`, `readOnlyTools`: `apps/agent-service/src/main/java/com/demo/agentservice/agent/dto/McpServerResponse.java:14-21`
- Agent tool `add_mcp_server` can set neither header nor forward: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentTools.java:112`

## Connected to

- **owned-by:** [Agent](agent.md) (`@ManyToOne`, cascaded from the agent)
- **joins:** [Todo](../todos/todo.md)'s `/mcp`, the default trusted URL; [Settings](../platform/settings.md) (`.env.example:24`, `:28`); every change writes `config_changed` to [Activity](activity.md)
- **looks-like-but-is-not:** a compose service. It is a row, and its URL may point anywhere public

## If you change this

- **Hits:** a new column needs the entity plus a new Liquibase changeset (`ddl-auto=validate`). Then `McpServerResponse`, `NewMcpServer`/`UpdateMcpServer`, `AgentService.addServer`/`updateServer`, and `apps/web/src/app/agents/agents.ts:16-26`. A new `Access` value hits the `access` check constraint (`001-init.sql:23`). Editing `agents.trusted-server-urls` flips which READ rule applies and whether forwarding still sends a token: it silently sends none (`McpTools.java:134`). Changing `agents.auth-header-key` makes every stored header undecryptable (`EncryptedText.java:93-94`).
- **Does not hit:** stored rows when the trusted list changes. Nothing is rewritten; it is judged at use. `AuthHeaderMigration` on a key change: it only encrypts plain rows and never re-keys (`libs/jpa-crypto/src/main/java/com/demo/jpa/crypto/EncryptedColumnMigration.java:34`). Not READ on a trusted server when you edit `readOnlyTools`: that list is ignored there, and only the server's `readOnlyHint` counts (`McpTools.java:220-221`).

## Surfaces

| Surface | Role |
|---|---|
| [Web app](../platform/web-app.md) server form (`apps/web/src/app/agents/agents.ts:112-120`) | reads, writes |
| another agent, via `add_mcp_server` / `set_mcp_access` / `remove_mcp_server` | writes, with `othersAccess` WRITE |
| `McpTools` on every `tools/list` / `tools/call` | reads |

## See

- Source: `apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java`
