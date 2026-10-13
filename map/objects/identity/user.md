---
type: object
cluster: identity
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/auth-service/src/main/java/com/demo/authservice/user/entities/User.java
---

# User

A registered person, the `users` row in auth-service's database (`User.java`); the only service that holds one.

## Why this shape

Every revocation is a counter on this row: `tokenVersion` is stamped into each [token](token.md) as `ver`, and
bumping it kills every token issued before, in every service (`User.java:50-57`). The flags exist because sign-in
with a provider matches users by email alone: `passwordSet=false` marks a hash nobody holds
(`User.java:59-62`, `UserService.java:254-256`), and `emailVerified=false` blocks sign-in until the signup
[email link](email-link.md) comes back (`User.java:64-67`, `SessionService.java:62-69`).

## Shape

- `id` identity PK, the token's `sub`; `email` unique; `name`; `passwordHash` (BCrypt) — `User.java:24-35`
- `role` stored as its name, default `USER` — `User.java:41-43`; see [role](role.md)
- `suspended`: no login, and every held token dies (suspend bumps the version) — `User.java:45-48`, `UserService.java:349-360`
- `tokenVersion` bumped by admin revoke, suspend, role change, new password, provider claim — `UserService.java:363-368`, `:336-340`, `:174`, `UserRepository.java:45-47`
- Schema is Liquibase, Hibernate only validates — `001-init.sql:8-16`, `002-password-set.sql:7`, `003-email-verified.sql:7`, `application.properties:11`
- Writes that race are single conditional UPDATEs (`edit`, `verifyEmail`, `reRegister`, `claim`, `moveEmail`) — `UserRepository.java:21-53`
- **AdminSeeder**: on every boot, `admin.email`/`admin.password` create the admin, or promote an existing address without touching its password; blank logs a warning — `AdminSeeder.java:42-50`, `UserService.java:389-404`, `application.properties:37-38`

Citations (short names below resolve here): `apps/auth-service/src/main/java/com/demo/authservice/user/entities/User.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/entities/UserRepository.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java`, `apps/auth-service/src/main/java/com/demo/authservice/session/SessionService.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/AdminSeeder.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/CurrentUserResolver.java`, `apps/auth-service/src/main/resources/application.properties`, `apps/auth-service/src/main/resources/db/changelog/changes/{001-init,002-password-set,003-email-verified}.sql`, `libs/auth-client/src/main/java/com/demo/auth/client/JwtVerifier.java`

## Connected to

- **owns:** its role, its `tokenVersion`
- **owned-by:** auth-service only; other services know just the id (a `Caller.userId`, see [caller](caller.md))
- **joins:** [token](token.md) (`sub`, `ver`), [email-link](email-link.md), [agent](../agents/agent.md) and [todo](../todos/todo.md) by owner id, no FK across databases
- **looks-like-but-is-not:** [agent-token-version](agent-token-version.md), a per-agent counter with no user column

## If you change this

- **Hits:** a new or changed column needs a changeset under `apps/auth-service/src/main/resources/db/changelog/changes/` (`ddl-auto=validate` refuses a mismatch, `application.properties:11`); `UserResponse` and the web `User` type if it is shown (`apps/web/src/app/auth/auth.ts:11`); anything new that must kill sessions bumps `tokenVersion`.
- **Does not hit:** todo-service or agent-service: they never read this row, only the token's claims (`JwtVerifier.java:52-72`). Changing the admin password in `.env` does not reset an existing admin (`UserService.java:393-399`).

## Surfaces

| Surface | Role |
|---|---|
| auth-service `UserService`, `SessionService`, `OAuthController` | read / write |
| `CurrentUserResolver` | reads the row on every request (`CurrentUserResolver.java:49-55`) |
| `/internal/token-versions` | publishes `id -> tokenVersion` for versions > 0 (`UserService.java:377`) |
| web `/users`, `/profile` | reads / writes through `/api/users` |

## See

- Source: `apps/auth-service/src/main/java/com/demo/authservice/user/entities/User.java`
