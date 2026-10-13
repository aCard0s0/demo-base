---
type: process
status: verified
verified: 2026-10-13
at: 3532829
consumes: [../objects/agents/agent.md, ../objects/identity/caller.md, ../objects/identity/user.md, ../objects/identity/agent-token-version.md, ../objects/platform/settings.md]
produces: [../objects/identity/token.md, ../objects/identity/agent-token-version.md, ../objects/agents/activity.md]
---

# agent-token-mint

An agent's owner gets a 30-day AGENT token for an external MCP client, and can kill every such token for that agent.

## Input → Movement → Output

The owner's login token hits `POST /api/agents/{id}/token` in agent-service, which checks ownership and asks
auth-service's `/internal/agent-tokens` over the compose network with `X-Internal-Secret`. auth-service signs a
token with role `AGENT`, the `agent` id and its current `agentVer`; it is shown once and never stored.
`/token/revoke` takes the same path to bump `agentVer`.

## Why this shape

Only auth-service holds the private key and the versions, so agent-service can only relay. The endpoint hands
out credentials for any user, so being on the network is not enough: the shared secret is required, and a
blank one closes it. An agent token is refused by every REST controller, so it cannot mint itself fresh ones.

## Steps

1. Web: `apps/web/src/app/agents/agents.ts:129` posts `/api/agents/{id}/token` with the owner's bearer.
2. `AgentController.token`, `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentController.java:95`; `Caller` resolution 403s an agent token, `libs/auth-client/src/main/java/com/demo/auth/client/CallerResolver.java:48`.
3. Ownership: someone else's agent is 404, `apps/agent-service/src/main/java/com/demo/agentservice/agent/AgentService.java:65`.
4. Relay: `AgentTokens.issue` posts `{userId, agentId}` with `X-Internal-Secret`, 2 s connect / 5 s read, any failure → 502, `apps/agent-service/src/main/java/com/demo/agentservice/token/AgentTokens.java:43`, `:68`. URI `AUTH_AGENT_TOKENS_URI`, `compose.yaml:115`.
5. Secret check, constant-time, blank = closed → 403, `apps/auth-service/src/main/java/com/demo/authservice/internal/AgentTokenController.java:58`; endpoint `:43`.
6. `UserService.issueAgentToken`: user must exist and not be suspended (404), `agentVer` read (0 if never revoked), `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java:290`.
7. Signed with role `AGENT`, claims `agent` + `agentVer`, user's current `ver`, TTL `auth.agent-token-ttl` 30d: `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java:76`, `:52`.
8. Activity line "agent token issued", token returned, `AgentController.java:99`.
9. Revoke: `agents.ts:133` → `AgentController.java:107` → `AgentTokens.revoke`, `AgentTokens.java:59` → `AgentTokenController.java:51` → `UserService.java:305` (upsert bump). Bites in todo-/agent-service on their next feed read, ≤10 s (see [verify-and-revoke](verify-and-revoke.md)).
10. The token also dies with its owner's `ver` (revoke, suspend, role or password change), since it carries it, `Tokens.java:77`.

## If you change this

- **Hits:** `AUTH_INTERNAL_SECRET` must match on both sides (`.env.example:20`; `auth.internal-secret` in both services' `application.properties`); the request/answer shape `{userId, agentId}` → `{token}` on both sides; the AGENT claims, which `JwtVerifier.callerOf` requires (`agentVer` is mandatory there); the web's one-time token display in `agents/agent-token`.
- **Does not hit:** login tokens or the owner's other agents (revoke is per agent id); the nginx routes (`/internal` is never proxied, `docker/nginx.conf:27`); deleting an agent, which does not call revoke (its tokens then fail at the ownership lookup instead).

## Surfaces

| Surface | Role |
|---|---|
| `POST /api/agents/{id}/token`, `/token/revoke` | owner-facing, agent-service |
| `POST /internal/agent-tokens`, `/{agentId}/revoke` | compose network + shared secret, auth-service |

## See

- Objects: [token](../objects/identity/token.md), [agent](../objects/agents/agent.md), [agent-token-version](../objects/identity/agent-token-version.md), [activity](../objects/agents/activity.md)
- Source: `apps/agent-service/src/main/java/com/demo/agentservice/token/AgentTokens.java`, `apps/auth-service/src/main/java/com/demo/authservice/internal/AgentTokenController.java`
