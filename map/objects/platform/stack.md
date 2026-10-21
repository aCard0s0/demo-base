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

The compose stack (`compose.yaml`, project `demo-base`) and the one multi-stage `Dockerfile` it builds every image
from: seven services, one network, one volume, and only loopback ports.

## Why this shape

Start order is health-gated so nothing boots against a missing dependency: db healthy → db-init exits 0 →
auth-service healthy → todo-service healthy → agent-service healthy → web (`compose.yaml:44-46`, `:67-71`,
`:107-111`, `:138-144`, `:162-168`). The JVM sizes its heap from the cgroup (`MaxRAMPercentage=50`,
`compose.yaml:53-62`), so `mem_limit` *is* the heap knob. Ports are bound to 127.0.0.1 because the dev defaults
(admin, secrets) must not face the LAN (`compose.yaml:159-161`). One Maven build feeds every service stage, so the
reactor compiles once (`Dockerfile:1`).

## Shape

| Service | Image / stage | Gate it waits on | Health | mem |
|---|---|---|---|---|
| db | postgres:18-alpine, volume at `/var/lib/postgresql` | — | `pg_isready` | 192m |
| db-init | postgres:18-alpine, runs `docker/initdb.sql` once per `up` | db healthy | exits | 32m |
| auth-service | stage `auth-service` | db-init done, mailpit started | `/api/public/stats` | 352m |
| mailpit | axllent/mailpit, `127.0.0.1:1004:8025` (inbox), `127.0.0.1:1005:1025` (SMTP, for auth-service run from source) | — | — | 64m |
| todo-service | stage `todo-service` | db-init, auth healthy | `/api/public/todos/stats` | 352m |
| agent-service | stage `agent-service` | db-init, auth + todo healthy | `/actuator/health` | 384m |
| web | stage `web` (nginx), `127.0.0.1:1000:80` | all three services healthy | — | 32m |

- Ports, all 100X (listed at `compose.yaml:6-7`): web 1000 (nginx on 80 inside), auth-service 1001, todo-service
  1002, agent-service 1003 — each service's own `server.port`, the same inside compose and from source —
  Mailpit 1004 (inbox) and 1005 (SMTP), Postgres 1006 (override below). Only web, Mailpit and the override publish.
- Names: top-level `name: demo-base` (`compose.yaml:10`) and `container_name: ${COMPOSE_PROJECT_NAME:-demo-base}-<service>`
  on every service (`:14`, `:35`, `:49`, `:80`, `:89`, `:119`, `:153`): containers are `demo-base-auth-service` and
  so on, no `-1`; `COMPOSE_PROJECT_NAME` renames project and containers together (`:7-9`).
- `.env` is handed to each Java service whole and optional: `env_file: [{ path: .env, required: false }]`
  (`compose.yaml:58-60`, `:96`, `:126`). Keys under `environment:` win over it: the per-service
  `SPRING_DATASOURCE_URL` and `AUTH_*_URI` (`compose.yaml:63`, `:99-106`, `:129-135`), and an empty
  `AUTH_SIGNING_KEY`, `AUTH_EMAIL_LINK_KEY` and `ADMIN_PASSWORD` for todo- and agent-service, so auth-service's
  secrets stay with it (`:100-103`, `:130-132`).
- Dockerfile stages: `build` (JDK 26, `-pl` the three services `-am`, web excluded, `Dockerfile:3-12`) →
  `runtime` (JRE, non-root `app` user, `:15-18`) → `auth-service` / `todo-service` / `agent-service` (copy the jar,
  EXPOSE 1001/1002/1003, `:20-33`); `web-build` (node 24, `npm ci` + `npm run build`, `:35-40`) → `web` (nginx with
  `docker/nginx.conf`, `:43-45`).
- `compose.override.yaml` (git-ignored, `.gitignore:9`) publishes db on `127.0.0.1:1006:5432`; `./dbase setup`
  writes it (`dbase:196-207`). `.dockerignore` keeps `.env` out of every image.

## Connected to

- **owns:** the start order, the container limits and names, the compose network names (`auth-service:1001`, ...)
- **owned-by:** [project-cli](project-cli.md) drives it (`start` = `up -d --wait`, `dbase:312-318`)
- **joins:** [settings](settings.md) (env_file), [databases](databases.md) (db, db-init), [routes](routes.md)
  (web image), [agent-mcp-server](../agents/agent-mcp-server.md) (`AGENTS_TRUSTED_SERVER_URLS` default `compose.yaml:137`)
- **looks-like-but-is-not:** a "server" in the map's sense — that is an `AgentMcpServer` row

## If you change this

- **Hits:** a new or renamed service → its compose block with its own `container_name`, a Dockerfile stage plus the
  `-pl` list (`Dockerfile:12`), the `{auth,todo,agent}` list in `docker/initdb.sql:7`, both route files
  ([routes](routes.md)), `SERVICES`/`DEV_TARGETS` and `db shell`'s list in `dbase:36-37`, `:371`. A renamed host or
  a service's port (`server.port`) → its healthcheck (`compose.yaml:73`, `:113`, `:146`), the in-network URIs
  (`:105-106`, `:133-137`), `EXPOSE` (`Dockerfile:22`, `:27`, `:32`), nginx's `proxy_pass` lines and
  `proxy.conf.json`, and the header list (`compose.yaml:6`). A healthcheck path → it must stay unauthenticated in
  that service. A `mem_limit` → the heap, and README's measured table (`README.md:159-167`). A second copy of the
  stack on one host → its own `COMPOSE_PROJECT_NAME` and host ports, or every checkout drives the same project,
  containers and volume (`compose.yaml:8-9`).
- **Does not hit:** the localhost datasource and auth URLs in each `application.properties` — run-from-source
  defaults that compose's `environment:` overrides. Putting `SPRING_DATASOURCE_URL` or `AUTH_JWKS_URI` in `.env`
  does nothing under compose: `environment:` beats `env_file`.

## Surfaces

| Surface | Role |
|---|---|
| `./dbase start/stop/restart/down/logs/status` | drives it |
| humans | `docker compose up -d --build --wait` (`compose.yaml:1`) |

## See

- Source: `compose.yaml`, `Dockerfile`
