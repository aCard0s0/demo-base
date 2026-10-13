---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: demo-base
---

# Project CLI

`./demo-base`: the one bash script that sets up, builds, tests, runs from source and drives the compose stack.
Bare, it prints every command (`demo-base:365`).

## Why this shape

Every command name lives in exactly one place, the `COMMANDS` registry; help, `<cmd> --help`, lookup and
dispatch are all generated from it, so they cannot drift (`demo-base:5-9`, `:54-112`). The dispatcher only
resolves a name to a `cmd_` function and holds no logic (`demo-base:352-381`). bash 3.2 so it runs on stock macOS
(`demo-base:20-21`).

## Shape

- Registry rows `name|alias|args|summary`, `#Label` = section (`demo-base:55-77`). Commands: `setup`, `doctor`,
  `build`, `test`, `dev`, `clean`, `start` (`up`), `stop`, `restart`, `status` (`ps`), `logs`, `down`, `db shell`,
  `map sync` (regenerates `map/AGENTS.md` and `map/routing.md` from `map/CLAUDE.md`), `version` (`-V`), `help`.
- Name → function: spaces and dashes become underscores, `db shell` → `cmd_db_shell` (`demo-base:81`); optional
  long help in `cmd_<fn>_help` (`demo-base:122-123`). A namespaced name consumes two tokens (`demo-base:374`).
- Exit codes (`demo-base:50`): 0 ok · 1 runtime failure · 2 usage error (unknown command/option, refused confirm)
  · 3 missing dependency (`require_docker`, `require_java`, `require_node`, `:126-139`) · 4 bad state (Java < 26,
  no `node_modules`, nothing on :5432; `:135`, `:141`, `:252`). `doctor` exits 3 or 4 by the same rule (`:212-213`).
- Side effects go through `run` (free `--dry-run`/`--verbose`), destructive ones through `confirm` (`--yes`, refuses
  on non-interactive stdin) (`demo-base:145-151`). Values on stdout, prose on stderr (`demo-base:45-49`).
- Lists it keeps: `SERVICES` (compose targets), `DEV_TARGETS`, `SUITES` (`demo-base:36-38`); `db shell`'s
  databases (`demo-base:338`).
- Drift lint: not in the repo and not wired to any CI. It is the `project-cli` skill's two checks (every `cmd_`
  function is a registry row and back; every row has 4 fields). Both pass at this commit.

## Connected to

- **owns:** the command vocabulary
- **owned-by:** nothing; it is the repo's entry point (`CLAUDE.md:8`)
- **joins:** [stack](stack.md) (`compose` with no `-f`, so `compose.override.yaml` and `.env` apply, `demo-base:33-35`),
  [settings](settings.md) (`setup` seeds `.env`, `dev` sources it), [databases](databases.md) (`db shell`, `down -v`)
- **looks-like-but-is-not:** a Makefile or npm scripts — `apps/web/package.json` scripts are what it calls for `web`

## If you change this

- **Hits:** a new command → one registry row + one `cmd_<name>` (+ optional `cmd_<name>_help`), arguments
  validated before the first side effect (`demo-base:5-16`). A new compose service → `SERVICES` (and `DEV_TARGETS`
  if it runs from source); a new database → `db shell`'s list.
- **Does not hit:** `usage` or the help text — they are generated from the registry; hand-editing them, or adding
  a `case` to `main`, is the bug the registry exists to prevent.

## Surfaces

| Surface | Role |
|---|---|
| humans, agents | run it; `./demo-base <cmd> --help` for one command |
| `map/CLAUDE.md` | routes "running, building, testing" here |

## See

- Source: `demo-base`
