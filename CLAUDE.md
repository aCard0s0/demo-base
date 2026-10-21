# demo-base

Three Spring Boot services (auth, todo, agent), their shared libs, and an Angular frontend, in one Maven build
and one compose stack. A base to start other projects from.

| If you want to | Go to |
|---|---|
| run, build, test, or start the stack | `./dbase` (bare prints every command; `<cmd> --help` for one) |
| know what a thing is, or what a change will hit | [map/CLAUDE.md](map/CLAUDE.md) |
| the full story of one service or lib | its `README.md` (`apps/*/`, `libs/*/`) |
| the settings a deployment changes | [.env.example](.env.example) |
| connect an MCP client to an agent | [docs/connect-an-agent.md](docs/connect-an-agent.md) |

Before editing: open `map/effects/CONTEXT.md` for the change you are making. A schema change in any service is
a new Liquibase changeset, never an entity edit alone.
