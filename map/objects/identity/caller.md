---
type: object
cluster: identity
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: libs/auth-client/src/main/java/com/demo/auth/client/Caller.java
---

# Caller

Who is asking, as todo-service and agent-service see it: `Caller(userId, role, agentId)` out of a verified
[token](token.md), built by `JwtVerifier`, injected by `CallerResolver`, revocation-checked by `Revocations`
(the `libs/auth-client` jar).

## Why this shape

One copy of "check a token without asking auth-service" for every verifying service. Role is a plain string, not
auth-service's enum, so an unknown role answers "no" to every question instead of crashing (`Caller.java:8-10`).
An agent token's `sub` is the owner, so `agentId` is what tells owner from agent (`Caller.java:7-8`).

## Shape

- `Caller`: `isAgent`, `readsEveryone`, `writesEveryone`, `mayActAs(agent)`, `describe` — `Caller.java:18-38`
- `JwtVerifier.callerOf`: RS256-only key selector over `auth.jwks-uri`, `sub`+`exp` required, `iss` = `auth-service` and `aud` = `demo-base` exactly — `JwtVerifier.java:31-33`, `:39-49`; `ver` below the feed's minimum → 401 — `:58-61`; `AGENT` must carry `agent` and `agentVer` ≥ minimum — `:65-77`; no role → `USER` — `:78`; any failure → 401 — `:79-81`
- `CallerResolver`: a `Caller` controller parameter; agent token → 403 — `CallerResolver.java:45-52`
- `Revocations`: polls `auth.token-versions-uri` at most every 10s; users by id, agents as `agent:<id>` — `Revocations.java:33`, `:58-65`; **fails closed, late**: a failed read keeps the last list (`:101-103`), but only for `STALE_AFTER` (5 min) after the last good one; then every check throws and every token is 401 until a read succeeds, and before the first good read nothing passes; a check that finds the list stale or expired waits for a read in flight (`refresh` is synchronized) rather than being refused — `:26-27`, `:36`, `:68-78`, `:84-86`, `:88`, `:97-100`
- Wiring: `scanBasePackages` includes `com.demo.auth.client` — `TodoServiceApplication.java:6`, `AgentServiceApplication.java:6`; properties `apps/todo-service/src/main/resources/application.properties:4`, `:6`, `apps/agent-service/src/main/resources/application.properties:7`, `:9`; compose `compose.yaml:105-106`, `:133-134`
- MCP servers skip the resolver and call `jwt.callerOf` directly — `TodoMcpServer.java:45`, `AgentMcpEndpoint.java:139`, `McpEndpoint.java:58-64`

Citations (short names below resolve here): `libs/auth-client/src/main/java/com/demo/auth/client/{Caller,CallerResolver,JwtVerifier,Revocations}.java`, `apps/todo-service/src/main/java/com/demo/todoservice/{TodoServiceApplication,mcp/TodoMcpServer}.java`, `apps/agent-service/src/main/java/com/demo/agentservice/{AgentServiceApplication,mcp/AgentMcpEndpoint}.java`, `libs/mcp-server/src/main/java/com/demo/mcp/server/McpEndpoint.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/{UserService,CurrentUserResolver}.java`, `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java`

## Connected to

- **owns:** the cached JWKS and revocation map (memory)
- **owned-by:** `libs/auth-client`; auth-service depends on it in test scope only (`apps/auth-service/pom.xml:83-85`)
- **joins:** [token](token.md), [role](role.md), [agent-token-version](agent-token-version.md) via the feed, [agent](../agents/agent.md) (`mayActAs`, `AgentMcpEndpoint.java:152`), [todo](../todos/todo.md) owner checks
- **looks-like-but-is-not:** auth-service's `CurrentUserResolver`, which resolves a `User` row, not a `Caller` (`CurrentUserResolver.java:17-23`)

## If you change this

- **Hits:** every consumer at once: todo-service, agent-service and `libs/mcp-server` (`libs/mcp-server/pom.xml:25`). A new claim read here must be minted in `Tokens.java:120-131`; the `iss`/`aud` values here and there (`Tokens.java:122-123`) together. Changing the feed key format (`agent:<id>`): `UserService.java:403` and `Revocations.java:64` together. `STALE_AFTER` is how long todo- and agent-service ride out an auth-service outage on the last list before refusing every token.
- **Does not hit:** auth-service's runtime: it never uses this jar outside tests. The web app: it never sees a `Caller`.

## Surfaces

| Surface | Role |
|---|---|
| todo-service REST + MCP | reads |
| agent-service REST + `/mcp` gateway | reads |
| auth-service `/api/jwks.json`, `/internal/token-versions` | source it polls |

## See

- Source: `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java`
