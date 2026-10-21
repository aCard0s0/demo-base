---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: apps/web/src/app/app.routes.ts
---

# Web app

The Angular frontend in `apps/web`: sign-in, profile, user admin, todo and agent pages, talking to the services only
through same-origin `/api/...` calls.

## Why this shape

There is no server-side session: the browser holds auth-service's JWT in localStorage and sends it as a bearer;
signing out is dropping it (`apps/web/src/app/auth/auth.ts:35-38`, `:137-140`). One interceptor attaches it and
treats any 401 as "the token died" (`auth.ts:148-164`), so pages never handle expiry themselves. Calls that must
not carry a token (login, mailed links, the OAuth hand-off) are marked `PUBLIC` (`auth.ts:31-33`).

## Shape

- Routes (`apps/web/src/app/app.routes.ts:4-19`): `login`, `signup` (same component, `data.signup`), `confirm-email`
  (no guard: the link is the proof), and behind `authGuard`: `profile`, `users`, `todos`, `agents`, `agents/:id`; `**` → `agents`.
- `Auth` service (`auth.ts:39-141`): token signal persisted to localStorage key `auth-token` (`:29`, `:43-55`);
  `login`, `register`, `verifyEmail`, `finishOAuth` (spends the HttpOnly `oauth_session` cookie, `:81-84`),
  `refresh` → `GET /api/users/me`, `update`, email change.
- `authInterceptor` (`auth.ts:154-164`): bearer only on `/api/` URLs not marked public; 401 → `logout()`.
  `authGuard` (`:166`) redirects to `/login` with no token; `leaveWhenSignedOut` (`:172-178`) for pages already open.
- Wiring: `apps/web/src/app/app.config.ts:10-17` (router, fetch HttpClient + interceptor, non-blocking `/me` at boot).
- API clients: `agents/agents.ts:87-136` (`/api/agents...`), `users/users.ts:16-43` (`/api/users...`),
  `todos/todos.ts` (`/api/todos...`). The sidebar shows Users only to ADMIN/MODERATOR (`commons/sidebar/sidebar.ts:22-23`).
- Styles: `src/styles.scss:4-10` pulls the partials in `src/styles/` — a selective Bootstrap import
  (`_bootstrap-custom.scss:1-2`), theme tokens as CSS variables keyed on `data-theme` (`_theme.scss:3-4`,
  `index.html:2`). `includePaths: ["src"]` (`apps/web/angular.json:31-34`) is what lets a component stylesheet
  write `@use 'styles/variables'`.
- Build: `npm run build` → `dist/web/browser`, served by nginx (`Dockerfile:35-45`); also a Maven module
  (`apps/web/pom.xml:19-56`) that the Docker build and `./dbase test java` skip.

## Connected to

- **owns:** the browser's copy of the [token](../identity/token.md)
- **owned-by:** [routes](routes.md) (one origin), [stack](stack.md) (`web` service)
- **joins:** [todo](../todos/todo.md), [user](../identity/user.md), [role](../identity/role.md), [email-link](../identity/email-link.md),
  [agent](../agents/agent.md), [agent-mcp-server](../agents/agent-mcp-server.md), [activity](../agents/activity.md)
- **looks-like-but-is-not:** an MCP client — the agent token page only shows the `/mcp?agent=<id>` URL
  (`agents/agent-token/agent-token.ts:31`); external clients connect

## If you change this

- **Hits:** a new page → `app.routes.ts` (+ `authGuard` if signed-in) and the sidebar items. A call to a new prefix
  → [routes](routes.md) in both files. A new external asset host → nginx's CSP. Moving the token out of
  localStorage → the CSP rationale in `docker/nginx.conf:12`.
- **Does not hit:** server-side role checks — hiding Users in the sidebar is cosmetic; auth-service answers 403
  (`app.routes.ts:10`). Global SCSS in `src/styles/` is not per-component: an edit there restyles every page.

## Surfaces

| Surface | Role |
|---|---|
| browser | the only consumer |
| `./dbase dev web` / `test web` | `npm start` on :1000 / Vitest (`dbase:266-270`, `:256-257`) |

## See

- Source: `apps/web/src/app/auth/auth.ts`
