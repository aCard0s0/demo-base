---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: docker/initdb.sql
---

# Databases

One Postgres server, one database and one login role per service (`auth`, `todo`, `agent`), created by
`docker/initdb.sql`; each service's schema is then its own business.

## Why this shape

A database per service means no service can read another's tables even by accident (`docker/initdb.sql:1`).
db-init runs the script on every `up`, not only on a fresh volume, and creates only what is missing, so a service
added later gets its database on the volume that already exists (`docker/initdb.sql:4-5`, `compose.yaml:32-46`).
Every service owns its schema as Liquibase changesets and let Hibernate only validate, so an entity edit
without a changeset fails at boot instead of silently altering a table.

## Shape

- Roles and databases: `\set services '{auth,todo,agent}'` (`docker/initdb.sql:7`); each role's password is its
  name (`:6`, `:9`); `\gexec` because `CREATE DATABASE` cannot run in a DO block (`:12-13`).
- Service URLs: `jdbc:postgresql://db:5432/<name>` under compose (`compose.yaml:63`, `:99`, `:129`);
  `localhost:1006` plus role/password in each `application.properties` for running from source, the port
  `compose.override.yaml` publishes (`dbase:196-207`).
- auth-service: Liquibase master `apps/auth-service/src/main/resources/db/changelog/db.changelog-master.yaml:2-11`
  (001-init, 002-password-set, 003-email-verified), `ddl-auto=validate` (`apps/auth-service/src/main/resources/application.properties:11`).
- agent-service: master `apps/agent-service/src/main/resources/db/changelog/db.changelog-master.yaml:2-8`
  (001-init, 002-agent-activity-index), `ddl-auto=validate` (`apps/agent-service/src/main/resources/application.properties:25`).
- todo-service: master `apps/todo-service/src/main/resources/db/changelog/db.changelog-master.yaml:2-5`
  (001-init), `ddl-auto=validate` (`apps/todo-service/src/main/resources/application.properties:15`).
- 001-init in all three is `onFail:MARK_RAN` on "table already exists", so a database Hibernate built earlier adopts
  the changelog (`apps/auth-service/src/main/resources/db/changelog/changes/001-init.sql:3-7`).
- Tests: SQLite files, Hibernate `create-drop`, Liquibase off, pool of 1, set inline per test class
  (`apps/agent-service/src/test/java/com/demo/agentservice/agent/AgentServiceTests.java:30-36`; driver is test-scoped,
  `pom.xml:80-85`). `PostgresIntegrationTests` in every service run the real changesets on Testcontainers and skip
  without Docker (`apps/auth-service/src/test/java/com/demo/authservice/PostgresIntegrationTests.java:27-46`).

## Connected to

- **owns:** the three databases and roles
- **owned-by:** [stack](stack.md) (db, db-init, the `db-data` volume)
- **joins:** [user](../identity/user.md), [agent-token-version](../identity/agent-token-version.md),
  [todo](../todos/todo.md), [agent](../agents/agent.md), [agent-mcp-server](../agents/agent-mcp-server.md),
  [activity](../agents/activity.md) — the tables live here
- **looks-like-but-is-not:** Flyway — agent-service comments mention it (`apps/agent-service/src/main/resources/application.properties:24`); it is gone

## If you change this

- **Hits:** an entity column → a new changeset file plus an `include` in that master, and
  `PostgresIntegrationTests.theChangesetsRan` (`apps/auth-service/src/test/java/com/demo/authservice/PostgresIntegrationTests.java:43-46`).
  A new service → one name in `docker/initdb.sql:7` and `db shell`'s list (`dbase:371`).
- **Does not hit:** the SQLite tests — they build the schema from the entities and never run a changeset, so a
  missing changeset passes them; only `PostgresIntegrationTests` (with Docker) or boot catches it. Editing an
  already-applied changeset does not migrate an existing database.

## Surfaces

| Surface | Role |
|---|---|
| auth / todo / agent services | read + write their own database only |
| `./dbase db shell [auth\|todo\|agent]` | psql as the superuser (`dbase:368-373`) |
| `./dbase down -v` | deletes the volume, and replaces `AUTH_SIGNING_KEY` when set, so no old token fits a new user (`dbase:358-363`) |

## See

- Source: `docker/initdb.sql`
