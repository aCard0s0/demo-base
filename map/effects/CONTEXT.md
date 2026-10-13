# Effects — which cards to open before a change

A catalog, not a waterfall: find the change, open the cards, read their **If you change this**. If this index and
a card disagree, fix the card. Inward edges (CI, other repos, scheduled jobs pointing into this tree): none known.

| If you are changing | Open first | Then |
|---|---|---|
| a column on users, agents, servers, activity | [user](../objects/identity/user.md) · [agent](../objects/agents/agent.md) · [agent-mcp-server](../objects/agents/agent-mcp-server.md) · [activity](../objects/agents/activity.md) | [databases](../objects/platform/databases.md): a new Liquibase changeset, or boot refuses |
| a column on todos | [todo](../objects/todos/todo.md) | nothing else: `ddl-auto=update` adds it |
| an enum value (`Role`, `Access`, `OthersAccess`) | [role](../objects/identity/role.md) · [agent-mcp-server](../objects/agents/agent-mcp-server.md) · [agent](../objects/agents/agent.md) | a changeset widening the check constraint; the web's union types |
| a token claim, TTL, or the signing key | [token](../objects/identity/token.md) · [caller](../objects/identity/caller.md) | [verify-and-revoke](../processes/verify-and-revoke.md) |
| who can revoke, or when tokens die | [user](../objects/identity/user.md) · [agent-token-version](../objects/identity/agent-token-version.md) | [verify-and-revoke](../processes/verify-and-revoke.md) · [agent-token-mint](../processes/agent-token-mint.md) |
| password login, lockout, OAuth providers | [sign-in](../processes/sign-in.md) | [settings](../objects/platform/settings.md) for `OAUTH_*` |
| signup, mailed links, change of email | [email-link](../objects/identity/email-link.md) | [signup-and-verify](../processes/signup-and-verify.md) · [settings](../objects/platform/settings.md) for `SPRING_MAIL_*` |
| what an agent may call, READ/WRITE, trusted servers | [agent-mcp-server](../objects/agents/agent-mcp-server.md) | [mcp-tool-call](../processes/mcp-tool-call.md) |
| a todo MCP tool or its `readOnlyHint` | [todo](../objects/todos/todo.md) | [mcp-tool-call](../processes/mcp-tool-call.md): READ agents rely on the hint |
| a URL prefix, a proxy route, the CSP | [routes](../objects/platform/routes.md) | both `docker/nginx.conf` and `apps/web/proxy.conf.json`, together |
| a page, the sidebar, the token store, styles | [web-app](../objects/platform/web-app.md) | [routes](../objects/platform/routes.md) if it calls a new prefix |
| a setting or env var | [settings](../objects/platform/settings.md) | `.env.example` and the service's `application.properties` together |
| compose, Dockerfile, ports, memory | [stack](../objects/platform/stack.md) | README's resources table |
| a database, role, or migration | [databases](../objects/platform/databases.md) | `docker/initdb.sql` |
| adding a whole service | [add-a-service](../processes/add-a-service.md) | every file it lists |
| a `./demo-base` command | [project-cli](../objects/platform/project-cli.md) | the registry row is the only place the name lives |
| the map's entry file | `../CLAUDE.md` | `./demo-base map sync` regenerates `AGENTS.md` and `routing.md` |
