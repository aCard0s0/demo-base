---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: .env.example
---

# Settings

The deployment's knobs: `.env` (template `.env.example`), whose names are Spring's relaxed-binding environment
names for the properties in each service's `application.properties`.

## Why this shape

One file drives compose and every service run from source (`.env.example:1-5`). `AUTH_INTERNAL_SECRET` binds
`auth.internal-secret`, and so on: no mapping table to keep. Every property carries a development default so the
stack starts with no `.env` at all (`compose.yaml:3-4`) — right on a laptop, wrong anywhere else.

## Shape

| `.env` name | Property → read by | Dev default | Set before going real? |
|---|---|---|---|
| `ADMIN_EMAIL` / `_PASSWORD` | `admin.*` (`auth…/application.properties:40-41`) → `AdminSeeder.java:34-43`; blank seeds none, create-only (`.env.example:11-14`) | blank | **yes** |
| `AUTH_INTERNAL_SECRET` | `auth.internal-secret` in auth (`:28`) **and** agent (`agent…/application.properties:13`) → `AgentTokenController.java:38`, `AgentTokens.java:33`; blank closes the mint endpoint | `dev-internal-secret` | **yes**, same value both |
| `AUTH_SIGNING_KEY` | `auth.signing-key` (auth `:25`) → `Tokens.java:65-73`; base64 PKCS#8 RSA ≥ 2048 bits, else refuses start (`Tokens.java:74-94`); blank = a fresh key per start, every token dies on restart; rotating it voids every token out (`.env.example:22-25`). `./dbase setup` adds one when absent or blank (`dbase:192-195`), `down -v` replaces it (`:362-363`), `start` warns without (`dbase:313-315`); only auth-service sees it: compose blanks it for the others, with `AUTH_EMAIL_LINK_KEY` and `ADMIN_PASSWORD` (`compose.yaml:100-103`, `:130-132`), and `dev` unsets all three (`dbase:278`) | blank | **yes**, once |
| `AGENTS_AUTH_HEADER_KEY` | `agents.auth-header-key` (agent `:16`) → `AuthHeaderCrypto.java:18`; blank refuses start; rotating breaks stored headers (`.env.example:27-29`) | `dev-auth-header-key` | **yes**, once |
| `AUTH_EMAIL_LINK_KEY` | `auth.email-link-key` (auth `:36`) → `EmailLinks.java:68-70`; blank = fresh per start | blank | **yes** |
| `SPRING_MAIL_*`, `AUTH_MAIL_FROM` | Spring's `spring.mail.*` (not in the file), `auth.mail-from` (auth `:33`); no host refuses signup (auth `:30-32`) | compose → mailpit (`compose.yaml:65-66`); `./dbase dev auth-service` → Mailpit's SMTP on `localhost:1005` when unset (`dbase:276`, `compose.yaml:86`) | **yes** |
| `OAUTH_REDIRECT_BASE_URL`, `OAUTH_<P>_*` | `oauth.*` (auth `:47-72`) → `@ConfigurationProperties` per provider (`libs/auth-provider/google/…/GoogleProvider.java:13`); base also builds mailed links (`EmailLinks.java:66`) | `http://localhost:1000`, all off | **yes** if used |
| `AGENTS_TRUSTED_SERVER_URLS` | `agents.trusted-server-urls` (agent `:36`) → read per check, `ServerUrls.java:32` | todo's `/mcp` (`compose.yaml:137`) | **yes** if MCP servers change |
| `POSTGRES_PASSWORD` | compose only: the db superuser (`compose.yaml:21`, `:40`), not the service roles (`docker/initdb.sql:6-9`) | `postgres` | yes |

Paths: `auth…` = `apps/auth-service/src/main/resources`, `agent…` = `apps/agent-service/src/main/resources`.
Not in `.env.example` but bindable the same way: `AUTH_TOKEN_TTL`, `AUTH_AGENT_TOKEN_TTL` (auth `:19-22`).

## Connected to

- **owns:** the env-name ↔ property contract
- **owned-by:** each service's `application.properties`
- **joins:** [stack](stack.md) (env_file), [token](../identity/token.md), [email-link](../identity/email-link.md),
  [user](../identity/user.md) (admin seed), [agent-mcp-server](../agents/agent-mcp-server.md) (header key, trusted URLs)
- **looks-like-but-is-not:** compose's own `${VAR:-default}` interpolation (`compose.yaml:65-66`, `:137`), which
  also reads `.env` but only for the keys compose names: a `SPRING_MAIL_HOST` in `.env` moves the container's mail
  too, which is why `dev` sets the from-source one in its own shell (`dbase:276`)

## If you change this

- **Hits:** a new setting → a property with a dev default in `application.properties` plus a commented line in
  `.env.example`. A renamed property → its env name, so `.env.example`, every real `.env`, and any compose
  `environment:` key. `AUTH_INTERNAL_SECRET` is read by two services: both must see it (they do, via the whole `.env`).
  `POSTGRES_PASSWORD` on an existing volume: the superuser keeps its old password, but db-init logs in with the
  new one (`compose.yaml:40`), fails, and every service waits on it.
- **Does not hit:** `compose.yaml` — `.env` is passed whole (`compose.yaml:58-60`), so a new key needs no compose
  edit. Changing `ADMIN_PASSWORD` does not reset an admin that already exists (`.env.example:11-14`).

## Surfaces

| Surface | Role |
|---|---|
| `./dbase setup` | seeds `.env` from `.env.example` (`dbase:190-191`), then writes `AUTH_SIGNING_KEY` when it is absent or blank (`:192-195`) |
| `./dbase start` / `restart` | warn when `.env` has no non-blank `AUTH_SIGNING_KEY` and auth-service is a target (`dbase:313-315`) |
| `./dbase dev` | sources `.env` into the shell (`dbase:164-165`, `:273`); drops `AUTH_SIGNING_KEY`, `AUTH_EMAIL_LINK_KEY` and `ADMIN_PASSWORD` for todo/agent, defaults auth-service's mail to `localhost:1005` (`:274-278`) |
| `./dbase down -v` | replaces `AUTH_SIGNING_KEY`, when set, as it deletes the database (`dbase:362-363`) |

## See

- Source: `.env.example`
