---
type: object
cluster: identity
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java
---

# Token

The RS256 JWT auth-service signs (`Tokens.java`) and publishes the public key for (`JwksController.java`): a
**login token** (30m) or an **agent token** (30d, role `AGENT`, pinned to one agent).

## Why this shape

Asymmetric so a service that verifies can never mint (`Tokens.java:26-28`); every other service checks it in
process from `/api/jwks.json`, never per-request calls (`JwtVerifier.java:20-26`). A signed token cannot be
recalled, so revocation is a counter in the token (`ver`, `agentVer`) compared against a polled feed
([caller](caller.md)). The keypair is made at startup and never stored: **a restart invalidates every token in
flight** (`Tokens.java:30-31`, `:55`).

## Shape

- Claims: `sub` = user id, `email`, `name`, `role`, `ver` = [user](user.md) `tokenVersion`, `iat`, `exp`; header `kid` — `Tokens.java:83-92`
- Agent token adds `agent` (agent id) and `agentVer` ([agent-token-version](agent-token-version.md)), role `"AGENT"` — `Tokens.java:76-78`
- TTLs: `auth.token-ttl=30m`, `auth.agent-token-ttl=30d` — `Tokens.java:51-52`, `application.properties:19`, `:22`
- Minted by: password login `SessionService.java:70-71`; `UserService.issue` (`:280-283`) for verify-email `SessionController.java:36-39`, profile edit `UserController.java:65`, provider sign-in `OAuthController.java:173`; agent token `UserService.java:290-298` via `POST /internal/agent-tokens` (`AgentTokenController.java:43-48`)
- JWKS: `GET /api/jwks.json`, unauthenticated — `JwksController.java:19-22`, `Tokens.java:127-129`
- auth-service's own check: `claimsFrom` (signature, expiry, `sub`, `ver`, role==AGENT) — `Tokens.java:105-124`; agent tokens get 403 there — `UserService.java:272-274`
- Web keeps the login token in `localStorage['auth-token']` — `apps/web/src/app/auth/auth.ts:29`, `:43`

Citations (short names below resolve here): `apps/auth-service/src/main/java/com/demo/authservice/token/{Tokens,JwksController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/session/{SessionService,SessionController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/{UserService,UserController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/oauth/OAuthController.java`, `apps/auth-service/src/main/java/com/demo/authservice/internal/AgentTokenController.java`, `apps/auth-service/src/main/resources/application.properties`, `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java`

## Connected to

- **owns:** the signing key (memory only)
- **owned-by:** auth-service
- **joins:** [user](user.md) (`sub`, `ver`, `role`), [role](role.md), [agent-token-version](agent-token-version.md) (`agentVer`), [caller](caller.md) (verifier), [agent](../agents/agent.md) (`agent` claim)
- **looks-like-but-is-not:** the [email link](email-link.md) token (HMAC, not a JWT); the `oauth_session` cookie, a 1-minute carrier that holds a login token on its way to the page (`OAuthController.java:80`, `:83`, `:173`)

## If you change this

- **Hits:** a new claim: `Tokens.issue` (`Tokens.java:80-99`) and, if it needs a new argument, both mint paths `SessionService.java:70-71` and `UserService.java:281`; reading it in `JwtVerifier.callerOf` (`JwtVerifier.java:46-76`) and `Caller`. Renaming `ver`/`role`/`agent`/`agentVer`: `Tokens.java` and `JwtVerifier.java` together, or every token is refused. TTL: `application.properties:19`, `:22`, `AUTH_TOKEN_TTL` / `AUTH_AGENT_TOKEN_TTL`.
- **Does not hit:** a persisted key or rotation: none exists (`Tokens.java:33`). The [email-link](email-link.md) key. Web parsing: the page never decodes the JWT, it reads `/api/users/me`.

## Surfaces

| Surface | Role |
|---|---|
| auth-service | signs; verifies its own via `claimsFrom` |
| todo-service, agent-service | verify via `libs/auth-client` |
| web | stores and sends as `Authorization: Bearer` |
| external MCP clients | hold an agent token in their config |

## See

- Source: `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java`
