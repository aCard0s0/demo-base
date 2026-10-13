---
type: object
cluster: identity
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/auth-service/src/main/java/com/demo/authservice/user/EmailLinks.java
---

# Email link

The signed, stateless link mailed to prove someone reads an inbox, at signup and on every change of email
(`EmailLinks.java`).

## Why this shape

Provider sign-in matches users by email alone, so an unproved address is a way into whoever proves it later
(`EmailLinks.java:29-30`). The link carries everything (purpose, user, from, to, expiry) plus an HMAC, so no
table is needed; naming the address it changes *from* makes it good once (`EmailLinks.java:40-42`). No mail
server means each flow is refused with 503, never skipped (`EmailLinks.java:88-94`).

## Shape

- Purposes: `VERIFY` (signup, spent with the signup password), `AUTHORIZE` (provider-only user's current inbox approves), `CHANGE` (new address confirms) — `EmailLinks.java:33-37`, `:53`
- Token = base64url(`purpose\nuserId\nexpiry\nfrom\nto`) + `.` + HMAC-SHA256 — `EmailLinks.java:153-158`, `:174-182`; TTL 30 min — `:51`
- Key: SHA-256 of `auth.email-link-key`; blank → random per start, so a restart voids every link out and two instances reject each other's — `EmailLinks.java:68-81`, `application.properties:33`, `.env.example:40`
- `read` checks MAC (constant time), allowed purpose, expiry → else 400 — `EmailLinks.java:135-150`
- URLs: `<oauth.redirect-base-url>/login#verify=…`, `/confirm-email#token=…` — `EmailLinks.java:100`, `:111`, `:120`, `application.properties:44`
- Spent at: `POST /api/login/verify-email` → `UserService.verifyEmail` (`SessionController.java:36-39`, `UserService.java:104-129`); `POST /api/users/email-confirm` → `confirmEmailChange` (`UserController.java:80-83`, `UserService.java:204-228`)
- Sent from: `register` (`UserService.java:96`), login of an unverified user (`SessionService.java:62-69`), `update` (`UserService.java:163-172`), `requestEmailChange` (`UserService.java:186-197`)

Citations (short names below resolve here): `apps/auth-service/src/main/java/com/demo/authservice/user/{EmailLinks,UserService,UserController}.java`, `apps/auth-service/src/main/java/com/demo/authservice/session/{SessionService,SessionController}.java`, `apps/auth-service/src/main/resources/application.properties`

## Connected to

- **owns:** the HMAC key (memory)
- **owned-by:** auth-service
- **joins:** [user](user.md) (`emailVerified`, `email`), web `login.ts:61`, `confirm-email.ts:20`
- **looks-like-but-is-not:** a [token](token.md): not a JWT, not RS256, never a bearer credential

## If you change this

- **Hits:** the payload format: `sign` and `read` together (`EmailLinks.java:140-158`); a URL path: the web routes (`apps/web/src/app/app.routes.ts:5`, `:8`) and fragment readers (`apps/web/src/app/auth/login/login.ts:61`, `apps/web/src/app/auth/confirm-email/confirm-email.ts:20`); the key: `AUTH_EMAIL_LINK_KEY` voids every outstanding link.
- **Does not hit:** the JWT signing key or [token](token.md) TTLs. The `users` schema: links are not stored. Mail transport (`SPRING_MAIL_*`, Mailpit in `compose.yaml:56-57`) is separate config.

## Surfaces

| Surface | Role |
|---|---|
| auth-service `UserService`, `SessionService` | write (send), read (spend) |
| web `/login#verify`, `/confirm-email` | posts the token back |
| Mailpit / SMTP | carries it |

## See

- Source: `apps/auth-service/src/main/java/com/demo/authservice/user/EmailLinks.java`
