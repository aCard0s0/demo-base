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

Asymmetric so a service that verifies can never mint (`Tokens.java:33-35`); every other service checks it in
process from `/api/jwks.json`, never per-request calls (`JwtVerifier.java:20-26`). A signed token cannot be
recalled, so revocation is a counter in the token (`ver`, `agentVer`) compared against a polled feed
([caller](caller.md)). The private key is `auth.signing-key` / `AUTH_SIGNING_KEY`, so tokens outlive a restart
(`Tokens.java:33-36`, `:65-73`); left blank, a key is made at startup and **a restart invalidates every token in
flight**, the 30-day agent tokens included (`:38-39`, `:68-70`).

## Shape

- Claims: `iss` = `auth-service`, `aud` = `demo-base` (both checked exactly by `JwtVerifier`), `sub` = user id, `email`, `name`, `role`, `ver` = [user](user.md) `tokenVersion`, `iat`, `exp`; header `kid` = the key's thumbprint, stable across restarts on the same key — `Tokens.java:120-132`, `:70`, `:90`
- Agent token adds `agent` (agent id) and `agentVer` ([agent-token-version](agent-token-version.md)), role `"AGENT"` — `Tokens.java:113-115`
- TTLs: `auth.token-ttl=30m`, `auth.agent-token-ttl=30d` — `Tokens.java:63-64`, `application.properties:19`, `:22`
- Key: base64 PKCS#8 RSA private key, public half rebuilt from it; under 2048 bits or unparseable refuses to boot — `Tokens.java:74-79`, `:84-94`, `application.properties:25`, `.env.example:22-25`. `./dbase setup` writes one to `.env` with openssl when it is absent or blank (`dbase:144-153`, `:192-195`), and `./dbase down -v` replaces it, since a new database hands the same ids to other people (`:362-363`); `./dbase start` warns when `.env` has none (`dbase:313-315`). Only auth-service holds it: compose hands `.env` whole to every Java service but blanks `AUTH_SIGNING_KEY` for todo- and agent-service (`compose.yaml:100-103`, `:130-132`), and `./dbase dev` unsets it for them (`dbase:274-278`)
- Minted by: password login `SessionService.java:70-71`; `UserService.issue` (`apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java:305-308`) for verify-email `SessionController.java:36-39`, profile edit `UserController.java:78`, provider sign-in `OAuthController.java:173`; agent token `UserService.java:315-323` via `POST /internal/agent-tokens` (`AgentTokenController.java:43-48`)
- JWKS: `GET /api/jwks.json`, unauthenticated — `JwksController.java:19-22`, `Tokens.java:167-169`
- auth-service's own check: `claimsFrom` (signature, expiry, `sub`, `ver`, role==AGENT; not `iss`/`aud`) — `Tokens.java:145-164`; agent tokens get 403 there — `UserService.java:297-299`
- Web keeps the login token in `localStorage['auth-token']` — `apps/web/src/app/auth/auth.ts:29`, `:43`

Citations (short names below resolve here): `apps/auth-service/src/main/java/com/demo/authservice/token/{Tokens,JwksController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/session/{SessionService,SessionController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/{UserService,UserController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/oauth/OAuthController.java`, `apps/auth-service/src/main/java/com/demo/authservice/internal/AgentTokenController.java`, `apps/auth-service/src/main/resources/application.properties`, `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java`, `.env.example`, `dbase`, `compose.yaml`

## Connected to

- **owns:** the signing key (`AUTH_SIGNING_KEY`; memory only when blank)
- **owned-by:** auth-service
- **joins:** [user](user.md) (`sub`, `ver`, `role`), [role](role.md), [agent-token-version](agent-token-version.md) (`agentVer`), [caller](caller.md) (verifier), [agent](../agents/agent.md) (`agent` claim)
- **looks-like-but-is-not:** the [email link](email-link.md) token (HMAC, not a JWT); the `oauth_session` cookie, a 1-minute carrier that holds a login token on its way to the page (`OAuthController.java:80`, `:83`, `:173`)

## If you change this

- **Hits:** a new claim: `Tokens.issue` (`Tokens.java:117-139`) and, if it needs a new argument, both mint paths `SessionService.java:70-71` and `UserService.java:306`; reading it in `JwtVerifier.callerOf` (`JwtVerifier.java:52-82`) and `Caller`. Renaming `ver`/`role`/`agent`/`agentVer`, or the `iss`/`aud` values: `Tokens.java` and `JwtVerifier.java` (`:31-33`) together, or every token is refused. TTL: `application.properties:19`, `:22`, `AUTH_TOKEN_TTL` / `AUTH_AGENT_TOKEN_TTL`. A new `AUTH_SIGNING_KEY` voids every token out, agent tokens in MCP configs included.
- **Does not hit:** rotation: one key, no previous kid published beside it (`Tokens.java:41`). The [email-link](email-link.md) key. Web parsing: the page never decodes the JWT, it reads `/api/users/me`.

## Surfaces

| Surface | Role |
|---|---|
| auth-service | signs; verifies its own via `claimsFrom` |
| todo-service, agent-service | verify via `libs/auth-client` |
| web | stores and sends as `Authorization: Bearer` |
| external MCP clients | hold an agent token in their config |

## See

- Source: `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java`
