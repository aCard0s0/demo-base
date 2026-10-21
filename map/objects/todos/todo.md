---
type: object
cluster: todos
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/todo-service/src/main/java/com/demo/todoservice/todo/entities/Todo.java
---

# Todo

One user's todo item, the `Todo` entity in todo-service (table `todos`), reachable over REST at `/api/todos` and
as four MCP tools at todo-service's own `/mcp`.

## Why this shape

The owner is the user **id** from the token, not the email, so an email change cannot orphan a list
(`apps/todo-service/src/main/java/com/demo/todoservice/todo/entities/Todo.java:13-15`). Every rule about who may
touch a row lives in `TodoService`, and both faces (REST and MCP) call it, so an agent is held to exactly what the
browser is (`apps/todo-service/src/main/java/com/demo/todoservice/mcp/TodoMcpServer.java:28-29`). Someone else's id
is **404, not 403**, so neither answer says whether the todo exists (`apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoService.java:67-77`).

## Shape

- Columns `id`, `owner`, `title`, `done`, all not null: `apps/todo-service/src/main/java/com/demo/todoservice/todo/entities/Todo.java:24-35`
- Schema is Liquibase's, Hibernate only validates: `apps/todo-service/src/main/resources/db/changelog/changes/001-init.sql:5-13`, `apps/todo-service/src/main/resources/application.properties:15`
- Owner-scoped queries plus two unscoped reads for read-everyone roles: `apps/todo-service/src/main/java/com/demo/todoservice/todo/entities/TodoRepository.java:16-21`
- Reads: ADMIN and MODERATOR see everyone's (`libs/auth-client/src/main/java/com/demo/auth/client/Caller.java:22-24`, `apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoService.java:28-32`)
- Writes: only ADMIN reaches others' rows; everyone else goes through `writable()` owner-scoped, else 404 (`libs/auth-client/src/main/java/com/demo/auth/client/Caller.java:26-28`, `apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoService.java:74-77`)
- Create is always for the caller; blank title is 400: `apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoService.java:35-37`, `:79-84`
- REST refuses an agent token with 403 before the controller runs: `apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoController.java:23-26`, `libs/auth-client/src/main/java/com/demo/auth/client/CallerResolver.java:48-49`
- MCP tools: only `list_todos` carries `readOnlyHint(true)`; `add_todo`, `update_todo`, `delete_todo` carry none: `apps/todo-service/src/main/java/com/demo/todoservice/mcp/TodoMcpServer.java:50-84`
- Public count, no token: `apps/todo-service/src/main/java/com/demo/todoservice/stats/StatsController.java:17-20`

## Connected to

- **owned-by:** a [User](../identity/user.md), by id from the [Token](../identity/token.md) `sub`, read as a [Caller](../identity/caller.md)
- **joins:** [Agent MCP server](../agents/agent-mcp-server.md): todo-service's `/mcp` is the default trusted server (`apps/agent-service/src/main/resources/application.properties:36`, `compose.yaml:137`)
- **looks-like-but-is-not:** agent-service's `/mcp?agent=<id>`. The public `/mcp` route goes to agent-service (`docker/nginx.conf:22`); todo-service's `/mcp` is reached only by agent-service over the compose network

## If you change this

- **Hits:** a new column on `Todo.java` needs a new `changes/002-*.sql` included from the master, or the context refuses to start (`ddl-auto=validate`); expose it in `TodoResponse.java:9-13`, the DTOs and the web's `Todo` interface (`apps/web/src/app/todos/todos.ts`). An ownership change in `TodoService.writable()` moves REST and MCP together. A new writing MCP tool must leave `readOnlyHint` off: agent-service's READ keys on that annotation for a trusted server (`apps/agent-service/src/main/java/com/demo/agentservice/mcp/McpTools.java:219-221`). Moving the `/mcp` path breaks `agents.trusted-server-urls`, which is compared exactly as written (`libs/outbound-urls/src/main/java/com/demo/outbound/UrlPolicy.java:86-88`).
- **Does not hit:** the SQLite tests for a missing changeset (they build from the entity); only `PostgresIntegrationTests` or boot catches it. Not `TodoController` for an ownership rule.

## Surfaces

| Surface | Role |
|---|---|
| REST `/api/todos` (user tokens only) | reads, writes |
| agent-service via todo-service `/mcp` (forwarded token) | reads, writes, gated by READ/WRITE upstream |
| landing stats `/api/public/todos/stats` | reads the count |
| [Web app](../platform/web-app.md) `/todos` | lists, adds, toggles, deletes (`apps/web/src/app/todos/todo-list/todo-list.ts`) |

## See

- Source: `apps/todo-service/src/main/java/com/demo/todoservice/todo/TodoService.java`
