---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: docker/nginx.conf
---

# Routes

The one-origin prefix table that sends browser and MCP traffic to the right service: `docker/nginx.conf` in the
web image, `apps/web/proxy.conf.json` under the dev server. Same table, two files; they change together.

## Why this shape

Everything sits behind one origin, so no service sets CORS and the bearer token travels in a header, not a URL
(`README.md:381-387`). `/internal` is for service-to-service calls on the compose network only, so nginx refuses it
outright (`docker/nginx.conf:26-27`). The provider callback `<base>/api/oauth/<p>/callback` lands on auth-service
through the plain `/api` rule, which is why the OAuth redirect base is the web's address.

## Shape

| Prefix | Service | nginx | dev proxy |
|---|---|---|---|
| `/api/agents`, `/api/public/agents`, `/mcp` | agent-service :9083 | `docker/nginx.conf:20-22` | `apps/web/proxy.conf.json:2-7`, `:17-19` |
| `/api/todos`, `/api/public/todos` | todo-service :9082 | `:23-24` | `:8-13` |
| `/api` (the rest) | auth-service :9081 | `:25` | `:14-16` |
| `/internal` | none: 404 | `:27` | not listed, never proxied |
| `/` | the Angular bundle, SPA fallback | `:29-31` | dev server |

- nginx picks the longest prefix, so its order is free (`docker/nginx.conf:2`); the dev proxy (wired at
  `apps/web/angular.json:90`) lists the specific prefixes above `/api`.
- Headers, nginx only (`docker/nginx.conf:15-17`): CSP `script-src 'self'` (the token is in localStorage, so script
  injection is the risk), `style-src 'unsafe-inline'` + Google Fonts for Angular's style tags and `index.html:10-12`;
  `nosniff`; `Referrer-Policy same-origin`. No HSTS: plain :80 (`:18`). The dev server sends no CSP.
- The CSP holds because the production build turns off inline critical CSS (`apps/web/angular.json:62-65`).

## Connected to

- **owns:** which service answers which path, the browser security headers
- **owned-by:** [stack](stack.md) (the `web` stage copies nginx.conf, `Dockerfile:43-45`); [web-app](web-app.md) (dev server)
- **joins:** [agent](../agents/agent.md) (`/mcp?agent=<id>`), [todo](../todos/todo.md), [token](../identity/token.md)
- **looks-like-but-is-not:** the services' own controller paths — each service already serves its full `/api/...`
  path; the proxy strips nothing

## If you change this

- **Hits:** a new or moved prefix → both files, same line each; a prefix under `/api/` but more specific than
  another service's → it must be listed before `/api` in proxy.conf.json. A path the web calls outside `/api/`
  gets no bearer from `authInterceptor` (`apps/web/src/app/auth/auth.ts:157`). A new external script, font or
  image host → the CSP line, or the browser blocks it in compose only.
- **Does not hit:** the services' security config or CORS (there is none to change), and compose's internal URLs
  (`compose.yaml:89-90`), which call `auth-service:9081` directly, bypassing both files.

## Surfaces

| Surface | Role |
|---|---|
| browser, MCP clients | send everything to `:3000` |
| [web-app](web-app.md) | calls `/api/...` relative to its origin |

## See

- Source: `docker/nginx.conf`, `apps/web/proxy.conf.json`
