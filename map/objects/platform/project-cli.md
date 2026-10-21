---
type: object
cluster: platform
universe: live
status: verified
verified: 2026-10-13
at: 3532829
entity: dbase
---

# Project CLI

`./dbase`: the one bash script that sets up, builds, tests, runs from source and drives the compose stack.
Bare, it prints every command (`dbase:398`).

## Why this shape

Every command name lives in exactly one place, the `COMMANDS` registry; help, `<cmd> --help`, lookup and
dispatch are all generated from it, so they cannot drift (`dbase:5-9`, `:54-112`). The dispatcher only
resolves a name to a `cmd_` function and holds no logic (`dbase:385-414`). bash 3.2 so it runs on stock macOS
(`dbase:20-21`).

## Shape

- Registry rows `name|alias|args|summary`, `#Label` = section (`dbase:55-77`). Commands: `setup`, `doctor`,
  `build`, `test`, `dev`, `clean`, `start` (`up`), `stop`, `restart`, `status` (`ps`), `logs`, `down`, `db shell`,
  `map sync` (regenerates `map/AGENTS.md` and `map/routing.md` from `map/CLAUDE.md`), `version` (`-V`), `help`.
- Name → function: spaces and dashes become underscores, `db shell` → `cmd_db_shell` (`dbase:81`); optional
  long help in `cmd_<fn>_help` (`dbase:122-123`). A namespaced name consumes two tokens (`dbase:407`).
- Exit codes (`dbase:50`): 0 ok · 1 runtime failure · 2 usage error (unknown command/option, refused confirm)
  · 3 missing dependency (`require_docker`, `require_java`, `require_node`, `:127-140`; `openssl` for `setup`,
  `:189`, and for `down -v` over a key, `:359`)
  · 4 bad state (Java < 26, no `node_modules`, nothing on :1006; `:136`, `:142`, `:272`). `doctor` exits 3 or 4 by
  the same rule (`:230-231`).
- Side effects go through `run` (free `--dry-run`/`--verbose`), destructive ones through `confirm` (`--yes`, refuses
  on non-interactive stdin) (`dbase:155-161`). Values on stdout, prose on stderr (`dbase:45-49`).
- Lists it keeps: `SERVICES` (compose targets), `DEV_TARGETS`, `SUITES` (`dbase:36-38`); `db shell`'s
  databases (`dbase:371`).
- Signing key: `setup` writes `AUTH_SIGNING_KEY` (RSA 2048, base64 PKCS#8, from openssl via `signing_key`) into
  `.env` when it is absent or blank (`""` counts as blank), rewriting the file in place, mode 600, with no copy left
  beside it; openssl making nothing is exit 1 with `.env` untouched (`dbase:144-153`, `:192-195`). `down -v` replaces
  one that is there, since a new database hands the same ids to other people (`:358-363`). `start`/`restart` warn
  when `.env` has no non-blank one and auth-service is among the targets (`:313-315`); `doctor` reports it and
  checks openssl (`:227-228`).
- `dev <java service>` loads `.env`, then unsets auth-service's secrets (`AUTH_SIGNING_KEY`, `AUTH_EMAIL_LINK_KEY`,
  `ADMIN_PASSWORD`) for todo- and agent-service and, for auth-service with no `SPRING_MAIL_HOST`, points mail at
  Mailpit's SMTP on `localhost:1005`, warning when nothing listens there (`dbase:273-278`); its help says to start
  `db db-init mailpit` first (`:284`).
- Drift lint: not in the repo and not wired to any CI. It is the `project-cli` skill's two checks (every `cmd_`
  function is a registry row and back; every row has 4 fields). Both pass at this commit.

## Connected to

- **owns:** the command vocabulary
- **owned-by:** nothing; it is the repo's entry point (`CLAUDE.md:8`)
- **joins:** [stack](stack.md) (`compose` with no `-f`, so `compose.override.yaml` and `.env` apply, `dbase:33-35`),
  [settings](settings.md) (`setup` seeds `.env`, `dev` sources it), [databases](databases.md) (`db shell`, `down -v`)
- **looks-like-but-is-not:** a Makefile or npm scripts — `apps/web/package.json` scripts are what it calls for `web`

## If you change this

- **Hits:** a new command → one registry row + one `cmd_<name>` (+ optional `cmd_<name>_help`), arguments
  validated before the first side effect (`dbase:5-16`). A new compose service → `SERVICES` (and `DEV_TARGETS`
  if it runs from source); a new database → `db shell`'s list.
- **Does not hit:** `usage` or the help text — they are generated from the registry; hand-editing them, or adding
  a `case` to `main`, is the bug the registry exists to prevent.

## Surfaces

| Surface | Role |
|---|---|
| humans, agents | run it; `./dbase <cmd> --help` for one command |
| `map/CLAUDE.md` | routes "running, building, testing" here |

## See

- Source: `dbase`
