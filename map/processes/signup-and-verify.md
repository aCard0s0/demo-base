---
type: process
status: verified
verified: 2026-10-13
at: 3532829
consumes: [../objects/identity/user.md, ../objects/identity/email-link.md, ../objects/platform/web-app.md]
produces: [../objects/identity/user.md, ../objects/identity/email-link.md, ../objects/identity/token.md]
---

# signup-and-verify

A stranger becomes a signed-in user by proving an inbox and the signup password together.

## Input → Movement → Output

Name, email and password go to `POST /api/users`, which writes an unverified user and mails a signed VERIFY link
to `/login#verify=…`. The login page reads the fragment and posts it back with the password to
`POST /api/login/verify-email`. The answer is a login token, which the web stores like any other sign-in.

## Why this shape

Provider sign-in matches users by email alone (`UserService.java:267`), so an unproven address would be a door
into whoever proves it later; link plus password means neither the inbox owner nor the typist can verify alone.
Answering a taken address with the same 201 keeps the endpoint from listing who has an account.

## Steps

1. Signup form submits: `apps/web/src/app/auth/login/login.ts:73` → `Auth.register` posts `/api/users` with no token, `apps/web/src/app/auth/auth.ts:67`.
2. `UserController.register` answers 201 with the address only, `apps/auth-service/src/main/java/com/demo/authservice/user/UserController.java:46`.
3. Service-wide cap, 30 per 15 min, counted before validation: `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java:40`, `:67`.
4. No mail server → 503 before anything is written: `UserService.java:77` → `apps/auth-service/src/main/java/com/demo/authservice/user/EmailLinks.java:88`.
5. Address on file but never proven: name and password overwritten, a fresh link follows, `UserService.java:81` (`UserRepository.java:36`).
6. Address proven by someone: the owner is mailed "already registered" and the caller gets the same 201, `UserService.java:87` → `EmailLinks.java:128`.
7. Free address: user saved with `emailVerified=false`, `UserService.java:92`.
8. Link mailed as `<base>/login#verify=<token>`: `EmailLinks.java:97`; stateless HMAC over purpose/user/expiry/from/to, `EmailLinks.java:153`; 30 min TTL `:51`; key from `auth.email-link-key` or random per boot `:65`. A send failure is 503, `:160`.
9. Login page takes `#verify` from the fragment and scrubs it from the URL, `login.ts:59`; submit then asks only for the password, `login.ts:69` → `auth.ts:72`.
10. `POST /api/login/verify-email`: `apps/auth-service/src/main/java/com/demo/authservice/session/SessionController.java:36`.
11. `UserService.verifyEmail`, `UserService.java:129`: link must be ours, unexpired, VERIFY (`EmailLinks.java:135`); then in `UserService.java`: user id and address must still match (line 131); already verified → 400 (134); 5 wrong passwords per 15 min → 429 (46, 137); suspended → 403 (146); marked verified only if the hash is unchanged (150, `UserRepository.java:31`).
12. Token minted with the current `ver`, `SessionController.java:39` → `UserService.java:305`; web adopts it, `auth.ts:87`, and persists it to localStorage, `auth.ts:50`.
13. Side door: a password login on an unverified user re-mails the link (3 per 15 min) and answers 403, `apps/auth-service/src/main/java/com/demo/authservice/session/SessionService.java:62`.
14. Bypass: an admin's `POST /api/users/create` skips all of this — no cap, no mail, verified at once, any role; it takes over an unproven address in one statement like step 5's, but clean (the admin's role, proven, unsuspended, `ver` bumped), and answers a proven one with a plain 400, `UserController.java:52-62`, `UserService.java:100-123` (`UserRepository.java:39-48`).

## If you change this

- **Hits:** the link format (`EmailLinks.sign`/`read`) and the web's `#verify` parsing in `login.ts` together; `auth.email-link-key` / `AUTH_EMAIL_LINK_KEY` in `.env.example:45`; the 503 when `SPRING_MAIL_HOST` is unset (compose defaults it to Mailpit, `compose.yaml:65`; `./dbase dev auth-service` to Mailpit's SMTP on `localhost:1005`, `dbase:276`, `compose.yaml:86`); `LoginResponse` shape, read by `auth.ts:73`.
- **Does not hit:** the change-of-email links (AUTHORIZE/CHANGE, `/confirm-email` page) beyond sharing `EmailLinks`; todo-/agent-service, which never see an unverified user (no token is issued before step 12).

## Surfaces

| Surface | Role |
|---|---|
| `/signup`, `/login#verify=` (web) | form, fragment reader |
| `POST /api/users`, `POST /api/login/verify-email` | auth-service entry points |
| SMTP (Mailpit in compose) | carries the link |

## See

- Objects: [user](../objects/identity/user.md), [email-link](../objects/identity/email-link.md), [token](../objects/identity/token.md)
- Source: `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java`, `.../user/EmailLinks.java`
