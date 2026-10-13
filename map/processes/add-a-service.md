---
type: process
status: verified
verified: 2026-10-13
at: 3532829
consumes: [../objects/platform/stack.md, ../objects/platform/settings.md, ../objects/platform/databases.md, ../objects/platform/routes.md, ../objects/platform/project-cli.md, ../objects/identity/caller.md]
produces: [../objects/platform/stack.md, ../objects/platform/databases.md, ../objects/platform/routes.md, ../objects/platform/settings.md]
---

# add-a-service

The developer movement this base exists for: a new Spring Boot service `apps/<svc>`, built, run, routed and authenticated like todo-service.

## Input → Movement → Output

Copy `apps/todo-service` (port, package, database name changed), then register the name in every list below;
nothing discovers services on its own. The output is a service that compose builds and health-checks, that owns
one Postgres database, that verifies tokens in process, and, if it serves MCP, that agent-service may forward to.

## Why this shape

Each list is explicit on purpose (one Maven `-pl`, one db list, one route table); a name missing from any one of
them fails at a different stage: build, boot, first request or first tool call.

## Steps

1. Module: add `apps/<svc>` to `<modules>`, `pom.xml:20`; its pom's parent is the root (`apps/todo-service/pom.xml:6`) with `web-errors`, `auth-client`, and `mcp-server` + `mcp` if it serves MCP (`apps/todo-service/pom.xml:24`, `:30`, `:36`, `:42`).
2. Boot class scans `com.demo.auth.client` too, or `JwtVerifier`/`CallerResolver` never load: `apps/todo-service/src/main/java/com/demo/todoservice/TodoServiceApplication.java:6`.
3. Errors: one empty `@RestControllerAdvice extends ErrorBodyAdvice`, `apps/todo-service/src/main/java/com/demo/todoservice/support/TodoExceptionAdvice.java:7`.
4. Settings: `server.port`, `auth.jwks-uri`, `auth.token-versions-uri`, datasource, `apps/todo-service/src/main/resources/application.properties:2`, `:4`, `:6`, `:10`. Schema: todo-service's `ddl-auto=update` (`:13`) is the leftover; agent-service's Liquibase + `validate` is the live pattern, `apps/agent-service/src/main/resources/application.properties:25`.
5. A public stats endpoint for the healthcheck, like `apps/todo-service/src/main/java/com/demo/todoservice/stats/StatsController.java:17`.
6. Dockerfile: add it to the build stage's `-pl` list, `Dockerfile:12`, and a runtime stage like `Dockerfile:25`.
7. compose.yaml: a block like todo-service's, `compose.yaml:77`: `build.target`, `mem_limit`/`cpus`, `env_file`, `SPRING_DATASOURCE_URL`, `AUTH_JWKS_URI`, `AUTH_TOKEN_VERSIONS_URI` (`:87`), `depends_on` db-init + auth-service healthy (`:91`), healthcheck on its port (`:96`). Add it to `web.depends_on` if the web routes to it, `compose.yaml:141`.
8. Database: add the name to the services list, `docker/initdb.sql:7` (db-init runs on every `up`, `compose.yaml:27`).
9. Routes (only with an HTTP API): `location /api/<thing>` (and `/api/public/<thing>`) proxied to the service; longest prefix wins, so it beats the `/api` catch-all wherever it sits, `docker/nginx.conf:23`; the same prefix in `apps/web/proxy.conf.json:8`.
10. `.env.example`: the per-service role note, `.env.example:7`, plus any new setting it reads.
11. `./demo-base`: `SERVICES` and `DEV_TARGETS`, `demo-base:36`, `:37`; the `db shell` database list and its help, `demo-base:338`, `:71`.
12. If it serves MCP: `@Bean` returning `McpEndpoint.servlet(name, tools)` with `jwt::callerOf`, `apps/todo-service/src/main/java/com/demo/todoservice/mcp/TodoMcpServer.java:43`; mark every non-writing tool `readOnlyHint(true)` and no other, `TodoMcpServer.java:54`; add `http://<svc>:<port>/mcp` to `agents.trusted-server-urls`, `compose.yaml:117`, `apps/agent-service/src/main/resources/application.properties:36`, `.env.example:28`; add it to `agent-service.depends_on`, `compose.yaml:118`.

## If you change this

- **Hits:** every file above, one line each; a trusted MCP server's `readOnlyHint` choices are a security boundary (READ agents rely on them, see [mcp-tool-call](mcp-tool-call.md)).
- **Does not hit:** auth-service code (it serves any verifier through `/api/jwks.json` and the feed); the web app's code unless it gets a page; nginx's `/internal` block, which already covers every service.

## Surfaces

| Surface | Role |
|---|---|
| `pom.xml`, `Dockerfile` | build |
| `compose.yaml`, `docker/initdb.sql` | run, database |
| `docker/nginx.conf`, `apps/web/proxy.conf.json` | routes (prod, dev) |
| `./demo-base`, `.env.example` | operator surface |

## See

- Objects: [stack](../objects/platform/stack.md), [databases](../objects/platform/databases.md), [routes](../objects/platform/routes.md), [settings](../objects/platform/settings.md), [project-cli](../objects/platform/project-cli.md)
- Source: `apps/todo-service/`
