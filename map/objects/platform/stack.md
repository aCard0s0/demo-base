---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: compose.yaml
---

# Stack

The compose stack (`compose.yaml`) and the one multi-stage `Dockerfile` it builds every image from: seven
services, one network, one volume, and only loopback ports.

## Why this shape

Start order is health-gated so nothing boots against a missing dependency: db healthy → db-init exits 0 →
auth-service healthy → todo-service healthy → agent-service healthy → web (`compose.yaml:36-38`, `:58-62`,
`:91-95`, `:118-124`, `:141-147`). The JVM sizes its heap from the cgroup (`MaxRAMPercentage=50`,
`compose.yaml:44-53`), so `mem_limit` *is* the heap knob. Ports are bound to 127.0.0.1 because the dev defaults
(admin, secrets) must not face the LAN (`compose.yaml:138-140`). One Maven build feeds every service stage, so the
reactor compiles once (`Dockerfile:1`).

## Shape

| Service | Image / stage | Gate it waits on | Health | mem |
|---|---|---|---|---|
| db | postgres:18-alpine, volume at `/var/lib/postgresql` | — | `pg_isready` | 192m |
| db-init | postgres:18-alpine, runs `docker/initdb.sql` once per `up` | db healthy | exits | 32m |
| auth-service | stage `auth-service` | db-init done, mailpit started | `/api/public/stats` | 352m |
| mailpit | axllent/mailpit, `127.0.0.1:8025` | — | — | 64m |
| todo-service | stage `todo-service` | db-init, auth healthy | `/api/public/todos/stats` | 352m |
| agent-service | stage `agent-service` | db-init, auth + todo healthy | `/actuator/health` | 384m |
| web | stage `web` (nginx), `127.0.0.1:3000:80` | all three services healthy | — | 32m |

- `.env` is handed to each Java service whole and optional: `env_file: [{ path: .env, required: false }]`
  (`compose.yaml:49-51`, `:84`, `:109`). Keys under `environment:` win over it: the per-service
  `SPRING_DATASOURCE_URL` and `AUTH_*_URI` (`compose.yaml:54`, `:87-90`, `:112-115`).
- Dockerfile stages: `build` (JDK 26, `-pl` the three services `-am`, web excluded, `Dockerfile:3-12`) →
  `runtime` (JRE, non-root `app` user, `:15-18`) → `auth-service` / `todo-service` / `agent-service` (copy the jar,
  EXPOSE 9081/9082/9083, `:20-33`); `web-build` (node 24, `npm ci` + `npm run build`, `:35-40`) → `web` (nginx with
  `docker/nginx.conf`, `:43-45`).
- `compose.override.yaml` (git-ignored, `.gitignore:9`) publishes db on `127.0.0.1:5432`; `./demo-base setup`
  writes it (`demo-base:182-191`). `.dockerignore` keeps `.env` out of every image.

## Connected to

- **owns:** the start order, the container limits, the compose network names (`auth-service:9081`, ...)
- **owned-by:** [project-cli](project-cli.md) drives it (`start` = `up -d --wait`, `demo-base:288-291`)
- **joins:** [settings](settings.md) (env_file), [databases](databases.md) (db, db-init), [routes](routes.md)
  (web image), [agent-mcp-server](../agents/agent-mcp-server.md) (`AGENTS_TRUSTED_SERVER_URLS` default `compose.yaml:117`)
- **looks-like-but-is-not:** a "server" in the map's sense — that is an `AgentMcpServer` row

## If you change this

- **Hits:** a new or renamed service → its compose block, a Dockerfile stage plus the `-pl` list
  (`Dockerfile:12`), the `{auth,todo,agent}` list in `docker/initdb.sql:7`, both route files ([routes](routes.md)),
  `SERVICES`/`DEV_TARGETS` and `db shell`'s list in `demo-base:36-37`, `:338`. A renamed host or port → the
  in-network URIs (`compose.yaml:89-90`, `:113-117`) and nginx's `proxy_pass` lines. A healthcheck path → it must
  stay unauthenticated in that service. A `mem_limit` → the heap, and README's measured table (`README.md:140-146`).
- **Does not hit:** `server.port` / the localhost URLs in each `application.properties` — those are the
  run-from-source defaults that compose overrides. Putting `SPRING_DATASOURCE_URL` or `AUTH_JWKS_URI` in `.env`
  does nothing under compose: `environment:` beats `env_file`.

## Surfaces

| Surface | Role |
|---|---|
| `./demo-base start/stop/restart/down/logs/status` | drives it |
| humans | `docker compose up -d --build --wait` (`compose.yaml:1`) |

## See

- Source: `compose.yaml`, `Dockerfile`
