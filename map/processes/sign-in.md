---
type: process
status: verified
verified: 2026-10-13
at: 3532829
consumes: [../objects/identity/user.md, ../objects/identity/role.md, ../objects/platform/settings.md, ../objects/platform/web-app.md]
produces: [../objects/identity/token.md, ../objects/identity/user.md]
---

# sign-in

Credentials or a provider's word go in at auth-service; a 30-minute login token comes out and lands in localStorage.

## Input → Movement → Output

A password login posts email and password to `POST /api/login` and gets the token in the JSON answer. A
provider login walks the browser through `/api/oauth/{p}/start` and `/callback`, which leaves the token in an
HttpOnly `oauth_session` cookie that the login page spends with `POST /api/oauth/session`. Either way the web
calls `Auth.signIn` and from then on sends `Authorization: Bearer` on every `/api` call.

## Why this shape

The OAuth token is never put in a URL: a link carrying one would sign the clicker into the link-maker's account
(login CSRF). State and PKCE verifier ride in cookies so a forged callback or a lifted code is worthless. The
lockout is per email because every browser request reaches the service from the proxy's one address.

## Steps

1. Password: `apps/web/src/app/auth/login/login.ts:70` → `Auth.login`, `apps/web/src/app/auth/auth.ts:61`.
2. `SessionController.login`, `apps/auth-service/src/main/java/com/demo/authservice/session/SessionController.java:24`; empty result → 401 `:26`.
3. `SessionService.login`, `apps/auth-service/src/main/java/com/demo/authservice/session/SessionService.java:43`: 5 failures per email per 15 min → 429 (`:22`, `:45`, window logic `apps/auth-service/src/main/java/com/demo/authservice/support/AttemptWindow.java:49`).
4. One BCrypt check even for an unknown email; failure recorded, `SessionService.java:51`.
5. Only after the password matches: suspended → 403 `:58`; unverified email → link re-mailed, 403 `:62`.
6. Token: `SessionService.java:70` → `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java:103` (claims `iss,aud,sub,email,name,role,ver`, `:120-130`; TTL `auth.token-ttl` 30m, `:63`).
7. OAuth: buttons from `GET /api/oauth/providers`, `auth.ts:57` → `apps/auth-service/src/main/java/com/demo/authservice/oauth/OAuthController.java:116` → only configured providers, `libs/auth-provider/core/src/main/java/com/demo/auth/provider/OAuthFlow.java:49`. Each button is a plain link to `/start`, `apps/web/src/app/auth/login/login.html:51`.
8. `start`: random state and verifier, both set as HttpOnly cookies (10 min, Lax, or None for form-post providers; `__Host-` over https), 302 to consent, `OAuthController.java:124`, cookies `:221`; consent URL carries the S256 challenge, `OAuthFlow.java:63`, `:117`.
9. `callback` (GET or POST), `OAuthController.java:144`: state/verifier cookies spent `:154`; provider error, state mismatch (constant-time `:241`) or no code each 302 to `/login#error=<code>` `:158`.
10. Code + verifier exchanged back-channel, `OAuthFlow.java:84`; identity read by the provider class, `OAuthFlow.java:76`.
11. User found or created by email, `OAuthController.java:169` → `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java:267`; an unproven address is claimed and its `ver` bumped (`UserRepository.java:56`). Suspended → `#error=suspended`, `OAuthController.java:170`.
12. Token set in `oauth_session` (HttpOnly, Strict, 1 min), 302 to `/login#oauth=done`, `OAuthController.java:173`.
13. Login page sees `#oauth=done`, scrubs it, `login.ts:59` → `Auth.finishOAuth`, `auth.ts:81` → `POST /api/oauth/session` returns the token once, clears the cookie, `no-store`; no cookie → 401, `OAuthController.java:187`.
14. Web adopts the token, `auth.ts:87`; persisted to localStorage `auth-token`, `auth.ts:50`; bearer added and a 401 drops it, `auth.ts:154`.

## If you change this

- **Hits:** `LoginResponse` / `{token}` shapes read by `auth.ts`; the `#oauth` / `#error` codes, which `OAUTH_ERRORS` in `login.ts:9` must name; cookie names/paths (`__Host-` needs `Path=/`, else `/api/oauth`) and `OAUTH_REDIRECT_BASE_URL` (`.env.example:64`), which providers have registered; token claims, which `JwtVerifier` reads in todo-/agent-service.
- **Does not hit:** todo-/agent-service code for a login change that keeps the claims; the agent token (minted separately, see [agent-token-mint](agent-token-mint.md)); any server-side session store (there is none).

## Surfaces

| Surface | Role |
|---|---|
| `/login` (web) | form, provider buttons, fragment reader |
| `POST /api/login`, `/api/oauth/*` | auth-service entry points |
| OAuth providers (`libs/auth-provider/*`) | consent, token exchange, identity |

## See

- Objects: [user](../objects/identity/user.md), [token](../objects/identity/token.md), [role](../objects/identity/role.md), [web-app](../objects/platform/web-app.md)
- Source: `apps/auth-service/src/main/java/com/demo/authservice/session/SessionService.java`, `.../oauth/OAuthController.java`, `libs/auth-provider/core/.../OAuthFlow.java`
