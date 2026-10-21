---
type: object
cluster: identity
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/auth-service/src/main/java/com/demo/authservice/user/entities/Role.java
---

# Role

What a user may do: the `Role` enum `ADMIN`, `MODERATOR`, `USER`; plus `AGENT`, a plain string that only ever
appears on an agent [token](token.md), never on a user row.

## Why this shape

A flat enum because three fixed answers are the whole requirement (`Role.java:7-8`). The role rides inside the
token so other services can decide without calling auth-service (`Role.java:4-5`). That splits the readers:
**auth-service reads the row** on each request (`UserService.java:293-302`, `UserController.java:101`, `:129-134`);
**todo-service and agent-service read the claim** (`JwtVerifier.java:63`, `:78`). To close that gap a role change
bumps the user's `tokenVersion`, so old tokens die within the 10s revocation poll (`UserService.java:361-365`,
`Revocations.java:33`).

## Shape

- `ADMIN` reads and writes everyone, the only one who gives a role; `MODERATOR` reads everyone; `USER` own data only — `Role.java:16-22`
- `readsEveryone()` / `writesEveryone()` — `Role.java:25-32`; string twins in `Caller.java:22-28`
- `AGENT` is stamped by `Tokens.issueForAgent` as a literal — `Tokens.java:113-115`, `Role.java:10-11`
- Stored as name, default `USER`, DB check `('ADMIN','MODERATOR','USER')` — `User.java:41-43`, `001-init.sql:13`
- Missing/unknown claim reads as `USER`, least privilege — `JwtVerifier.java:78`, `Caller.java:8-10`
- Two doors off `USER`, both admin only (registration has no role field): `PUT /api/users/{id}/role`, where an admin cannot demote itself — `UserController.java:107-114`, `UserService.java:347-368`; and `POST /api/users/create`, a new user with any role, null → `USER` — `UserController.java:52-62`, `UserService.java:111`, `AdminNewUser.java:5-6`

Citations (short names below resolve here): `apps/auth-service/src/main/java/com/demo/authservice/user/entities/Role.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/entities/User.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/UserService.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/UserController.java`, `apps/auth-service/src/main/java/com/demo/authservice/user/dto/AdminNewUser.java`, `apps/auth-service/src/main/java/com/demo/authservice/token/Tokens.java`, `apps/auth-service/src/main/resources/db/changelog/changes/001-init.sql`, `libs/auth-client/src/main/java/com/demo/auth/client/{Caller,JwtVerifier,Revocations}.java`, `apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoService.java`

## Connected to

- **owns:** nothing
- **owned-by:** [user](user.md) (`role` column)
- **joins:** [token](token.md) `role` claim, [caller](caller.md) `role` string
- **looks-like-but-is-not:** the `AGENT` role is not an enum value and no user holds it; an [agent](../agents/agent.md) is not a user

## If you change this

- **Hits:** adding a value: `Role.java`, a new changeset widening the `001-init.sql:13` check, the string checks in `Caller.java:22-28`, the web union type and role picker (`apps/web/src/app/auth/auth.ts:11`, `apps/web/src/app/users/user-list/user-list.ts:22`, `apps/web/src/app/commons/sidebar/sidebar.ts:23`). Changing what a role may do: `Role.java:25-32`, `Caller.java:22-28` (they share no code) and the admin gate, which tests `!= Role.ADMIN` directly (`UserController.java:130`).
- **Does not hit:** `Tokens.java`: it stamps `role.name()` as a string and needs no change (`UserService.java:306`). Agent permissions: READ/WRITE on an agent is agent-service's own setting, not a role.

## Surfaces

| Surface | Role |
|---|---|
| auth-service `UserController` | reads from the row, admin writes (role change, create) |
| todo-service `TodoService` | reads the claim via `Caller` (`TodoService.java:29`, `:75`) |
| web sidebar, `/users` | reads `user.role` from `/api/users/me` |

## See

- Source: `apps/auth-service/src/main/java/com/demo/authservice/user/entities/Role.java`
