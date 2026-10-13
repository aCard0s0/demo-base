---
type: process
status: verified
verified: 2026-10-13
at: 3532829
consumes: [../objects/identity/token.md, ../objects/identity/user.md, ../objects/identity/agent-token-version.md, ../objects/platform/settings.md]
produces: [../objects/identity/caller.md]
---

# verify-and-revoke

todo- and agent-service turn a bearer token into a `Caller` in process, and learn revocations by polling one feed.

## Input → Movement → Output

A request's `Authorization` header is checked against auth-service's public key (fetched from `/api/jwks.json`)
and against the minimum `ver` / `agentVer` from `/internal/token-versions`. A pass yields a `Caller(userId, role,
agentId)`; any failure is 401. auth-service itself checks its own tokens against the user row, so revocation bites
there at once and in the other two services within one 10-second window.

## Why this shape

Asymmetric keys mean a verifying service can never mint. Checking in process removes a call to auth-service per
request; the price is that a revocation needs a feed, and the feed fails open so auth-service going down does
not take the others with it.

## Steps

1. Keypair generated at boot, never stored: a restart voids every token, `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java:55`. `ver` stamped at `:88`.
2. Public half served unauthenticated, `apps/auth-service/src/main/java/com/demo/authservice/token/JwksController.java:19`; URI wired by `AUTH_JWKS_URI`, `compose.yaml:89`, `:113`.
3. `JwtVerifier` builds a cached JWKS source pinned to RS256, requiring `sub` and `exp`, `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java:35`.
4. `callerOf`, `JwtVerifier.java:46`: signature/expiry `:49`; `ver` must be ≥ the user's minimum `:52`; missing role reads as USER `:72`; an AGENT token must name its agent `:60` and carry `agentVer` ≥ the agent's minimum `:67`; anything wrong → 401 `:73`.
5. REST controllers get the `Caller` through `CallerResolver`, which also answers an agent token 403, `libs/auth-client/src/main/java/com/demo/auth/client/CallerResolver.java:45`. MCP endpoints call `callerOf` directly: `apps/todo-service/src/main/java/com/demo/todoservice/mcp/TodoMcpServer.java:45`, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:139`.
6. `Revocations` re-reads the feed lazily, on the first check after 10 s (`MAX_AGE`), under a lock, 2 s timeouts; on failure it keeps the last list: `libs/auth-client/src/main/java/com/demo/auth/client/Revocations.java:33`, `:68`, `:72`. URI from `AUTH_TOKEN_VERSIONS_URI`, `compose.yaml:90`, `:114`.
7. Feed: `apps/auth-service/src/main/java/com/demo/authservice/internal/TokenVersionController.java:26` → `UserService.tokenVersions`, users with `ver>0` by id and agents as `agent:<id>`, `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java:374`. Never proxied: `docker/nginx.conf:27`.
8. auth-service's own check re-reads the user: not suspended and `ver` equal, agent token 403, `UserService.java:268` via `apps/auth-service/src/main/java/com/demo/authservice/user/CurrentUserResolver.java:49`.

What bumps `ver` (all tokens of one user):
- admin revoke, `UserService.java:364`; suspend (only on the way in), `:355`; role change (only if it differs), `:336`;
- own password change, `UserService.java:174` (relative bump, `UserRepository.java:23`), answered with a fresh token, `UserController.java:65`;
- a provider claiming an unproven address, `UserRepository.java:45`.

What bumps `agentVer` (one agent's tokens): `POST /internal/agent-tokens/{id}/revoke`, `apps/auth-service/src/main/java/com/demo/authservice/internal/AgentTokenController.java:51` → `UserService.java:305` → upsert `+1`, `apps/auth-service/src/main/java/com/demo/authservice/user/entities/AgentTokenVersionRepository.java:18`.

## If you change this

- **Hits:** claim names (`ver`, `role`, `agent`, `agentVer`, `sub`) in `Tokens.issue` and `JwtVerifier.callerOf` together; the feed's key format (`<id>` / `agent:<id>`) in `UserService.tokenVersions` and `Revocations`; `auth.jwks-uri` / `auth.token-versions-uri` in each consumer's `application.properties` and `compose.yaml`; any new service that verifies tokens (it needs both URIs).
- **Does not hit:** the web (it only drops a token on 401, `apps/web/src/app/auth/auth.ts:154`); the email link (HMAC, not JWT); the agent's READ/WRITE rules, which sit after the `Caller`.

## Surfaces

| Surface | Role |
|---|---|
| `GET /api/jwks.json` | public key |
| `GET /internal/token-versions` | revocation feed, compose network only |
| `libs/auth-client` | verifier in todo- and agent-service (test scope in auth-service) |

## See

- Objects: [token](../objects/identity/token.md), [caller](../objects/identity/caller.md), [user](../objects/identity/user.md), [agent-token-version](../objects/identity/agent-token-version.md)
- Source: `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java`, `.../Revocations.java`
