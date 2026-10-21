# How to walk this map

Read `CLAUDE.md`, then one index (`objects/_index.md` or `effects/CONTEXT.md`), then one card, then the source
file its `See` names. Never load a whole cluster: the index exists so you do not.

## Universes

| Universe | Meaning | Here |
|---|---|---|
| **live** | in force; implement and cite against it | the three services, the six libs, `apps/web`, compose + Dockerfile, `./dbase` |
| **leftover** | present, not the main path | comments in agent-service about its Flyway era (`application.properties`, `001-init.sql`): the schema is Liquibase now |
| **ghost** | named, not wired | none known. `wallet-service` and the `data/` SQLite folder were ghosts and were removed |

## Card rules

- `status: verified` carries `verified:` (date) and `at:` (commit) and cites `path:line`. `stub` is fine. A
  confident wrong date is not.
- **If you change this** is first-order only: **Hits** what moves, **Does not hit** the obvious wrong guess.
- Cards point at code; they never copy it. When a card and the code disagree, the code wins and the card is fixed.
- What points INTO the repo from outside (CI, other repos, scheduled jobs) is invisible from here. None is known
  today; record any you find on the card it lands on.

## Layout of the subject

```
apps/auth-service   users, sign-in, tokens          :1001   Liquibase   README per service
apps/todo-service   todos + their MCP server        :1002   Liquibase
apps/agent-service  agents, MCP gateway             :1003   Liquibase
apps/web            Angular pages                   :1000
libs/*              auth-client, web-errors, mcp-server, outbound-urls, jpa-crypto, auth-provider
compose.yaml · Dockerfile · docker/ · .env.example · dbase (CLI)
```
