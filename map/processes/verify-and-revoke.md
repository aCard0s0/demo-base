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
there at once and in the other two services within one 10-second window. A feed unread for 5 minutes makes every
token 401 there.

## Why this shape

Asymmetric keys mean a verifying service can never mint. Checking in process removes a call to auth-service per
request; the price is that a revocation needs a feed. The feed fails closed, but late: the last list rides out a
short auth-service outage, then every token is refused, so a revocation the feed cannot deliver is missed for at
most `STALE_AFTER` (5 min), never indefinitely.

## Steps

1. Signing key from `auth.signing-key` / `AUTH_SIGNING_KEY` (base64 PKCS#8 RSA), kid = its thumbprint; blank makes one at boot, which a restart voids with every token; under 2048 bits or unparseable refuses to boot, `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java:65-94`. `iss=auth-service`, `aud=demo-base` stamped at `:122-123`, `ver` at `:128`.
2. Public half served unauthenticated, `apps/auth-service/src/main/java/com/demo/authservice/token/JwksController.java:19`; URI wired by `AUTH_JWKS_URI`, `compose.yaml:105`, `:133`.
3. `JwtVerifier` builds a cached JWKS source pinned to RS256, requiring `sub` and `exp` and exactly `iss=auth-service`, `aud=demo-base`, `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java:39`, `:31-33`, `:46-47`.
4. `callerOf`, `JwtVerifier.java:52`: signature/expiry/issuer/audience `:55`; `ver` must be ≥ the user's minimum `:58`; missing role reads as USER `:78`; an AGENT token must name its agent `:66` and carry `agentVer` ≥ the agent's minimum `:73`; anything wrong → 401 `:79`.
5. REST controllers get the `Caller` through `CallerResolver`, which also answers an agent token 403, `libs/auth-client/src/main/java/com/demo/auth/client/CallerResolver.java:45`. MCP endpoints call `callerOf` directly: `apps/todo-service/src/main/java/com/demo/todoservice/mcp/TodoMcpServer.java:45`, `apps/agent-service/src/main/java/com/demo/agentservice/mcp/AgentMcpEndpoint.java:139`.
6. `Revocations` re-reads the feed lazily, on the first check after 10 s (`MAX_AGE`), under a lock, 2 s timeouts: `libs/auth-client/src/main/java/com/demo/auth/client/Revocations.java:33`, `:80`, `:88`, `:49-52`. A failed read keeps the last list (`:101-103`) for `STALE_AFTER`, 5 min after the last good read (`:36`, `:99`); past that, and before the first good read, every check throws and the token is 401 (`:74-76`). An expired list sends a check into the refresh too, so one that lands while another's read is in flight waits for it instead of being refused (`:69-73`). URI from `AUTH_TOKEN_VERSIONS_URI`, `compose.yaml:106`, `:134`.
7. Feed: `apps/auth-service/src/main/java/com/demo/authservice/internal/TokenVersionController.java:26` → `UserService.tokenVersions`, users with `ver>0` by id and agents as `agent:<id>`, `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java:399`. Never proxied: `docker/nginx.conf:27`.
8. auth-service's own check re-reads the user: not suspended and `ver` equal, agent token 403, `UserService.java:293` via `apps/auth-service/src/main/java/com/demo/authservice/user/CurrentUserResolver.java:49`.

What bumps `ver` (all tokens of one user):
- admin revoke, `UserService.java:389`; suspend (only on the way in), `:380`; role change (only if it differs), `:361`;
- own password change, `UserService.java:199` (relative bump, `UserRepository.java:23`), answered with a fresh token, `UserController.java:78`;
- a provider claiming an unproven address, `UserRepository.java:56`; an admin's `POST /api/users/create` taking one over, `UserService.java:114` (`UserRepository.java:45-47`).

What bumps `agentVer` (one agent's tokens): `POST /internal/agent-tokens/{id}/revoke`, `apps/auth-service/src/main/java/com/demo/authservice/internal/AgentTokenController.java:51` → `UserService.java:330` → upsert `+1`, `apps/auth-service/src/main/java/com/demo/authservice/user/entities/AgentTokenVersionRepository.java:18`.

## If you change this

- **Hits:** claim names (`ver`, `role`, `agent`, `agentVer`, `sub`) and the `iss`/`aud` values in `Tokens.issue` and `JwtVerifier` together; `STALE_AFTER`, which decides how long an auth-service outage is ridden out; the feed's key format (`<id>` / `agent:<id>`) in `UserService.tokenVersions` and `Revocations`; `auth.jwks-uri` / `auth.token-versions-uri` in each consumer's `application.properties` and `compose.yaml`; any new service that verifies tokens (it needs both URIs).
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
