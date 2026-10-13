---
type: object
cluster: identity
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/auth-service/src/main/java/com/demo/authservice/user/entities/AgentTokenVersion.java
---

# AgentTokenVersion

One agent's revocation counter, the `agent_token_versions` row in auth-service (`AgentTokenVersion.java`),
stamped into its tokens as `agentVer`.

## Why this shape

Lets an owner kill one [agent](../agents/agent.md)'s tokens without signing themselves or their other agents
out (`UserService.java:300-303`). It lives in auth-service, not agent-service, because the minting side stamps
the claim and serves the feed the others already poll (`AgentTokenVersion.java:16-18`). No row means version 0;
the first revoke inserts 1 (`AgentTokenVersion.java:13-14`, `AgentTokenVersionRepository.java:16-20`).

## Shape

- `agentId` PK (no FK: agents live in agent-service's DB), `version` int default 0 — `AgentTokenVersion.java:27-32`, `001-init.sql:19-22`
- `bump`: one native upsert, `insert ... on conflict do update version+1` — `AgentTokenVersionRepository.java:17-20`, called from `UserService.java:304-310`
- Mint: `issueAgentToken` reads the current version into `agentVer` — `UserService.java:296-297`, `Tokens.java:76-78`
- Endpoints (compose network only, `X-Internal-Secret` required, blank secret = closed): `POST /internal/agent-tokens`, `POST /internal/agent-tokens/{agentId}/revoke` — `AgentTokenController.java:43-64`, `application.properties:25`
- Feed: `GET /internal/token-versions` serves rows with version > 0 as `agent:<id>` next to user ids — `TokenVersionController.java:26-29`, `UserService.java:374-380`
- Check: `agentVer` missing or below `minimumAgentVersion` → 401 — `JwtVerifier.java:67-70`, `Revocations.java:61-66`
- Caller: agent-service `AgentTokens.issue` / `revoke` — `AgentTokens.java:43-65`, from `AgentController.java:95-98`, `:107-110`

Citations (short names below resolve here): `apps/auth-service/src/main/java/com/demo/authservice/user/entities/{AgentTokenVersion,AgentTokenVersionRepository}.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java`, `apps/auth-service/src/main/java/com/demo/authservice/internal/{AgentTokenController,TokenVersionController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java`, `apps/auth-service/src/main/resources/application.properties`, `apps/auth-service/src/main/resources/db/changelog/changes/001-init.sql`, `libs/auth-client/src/main/java/com/demo/auth/client/{JwtVerifier,Revocations}.java`, `apps/agent-service/src/main/java/com/demo/agentservice/{token/AgentTokens,agent/AgentController,agent/AgentService}.java`

## Connected to

- **owns:** nothing
- **owned-by:** auth-service; keyed by an agent-service id
- **joins:** [token](token.md) `agentVer`, [caller](caller.md) via `Revocations`, [agent](../agents/agent.md)
- **looks-like-but-is-not:** [user](user.md) `tokenVersion` (`ver`), which kills every token of the user, agent tokens included

## If you change this

- **Hits:** the feed key `agent:<id>` lives in `UserService.java:378` and `Revocations.java:65`; the claim name `agentVer` in `Tokens.java:77` and `JwtVerifier.java:67`; the internal secret pairs `auth.internal-secret` in both services (`apps/agent-service/src/main/resources/application.properties:13`).
- **Does not hit:** the agent row in agent-service: deleting an agent neither revokes nor removes this row (`AgentService.java:103-105`, no FK). The user's login tokens. Login TTLs.

## Surfaces

| Surface | Role |
|---|---|
| agent-service `AgentTokens` | writes (revoke), triggers mint |
| auth-service `UserService` | reads at mint, publishes on the feed |
| todo-service, agent-service `Revocations` | read the feed |
| web agent page "Revoke tokens" | via `POST /api/agents/{id}/token/revoke` |

## See

- Source: `apps/auth-service/src/main/java/com/demo/authservice/user/entities/AgentTokenVersion.java`
