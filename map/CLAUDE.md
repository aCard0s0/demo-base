# demo-base map

The edit map of this repo: what the nouns are, how they move, and what else moves when you change one. The code
is the source of truth; every card cites it. AGENTS.md and routing.md here are generated twins of this file
(`./dbase map sync`) — edit this one only.

## Where things live

| Folder | What it holds |
|---|---|
| `CONTEXT.md` | how to walk the map; live / leftover / ghost |
| `objects/_index.md` | one line per noun, with its card and status |
| `objects/<cluster>/` | noun cards: identity, todos, agents, platform |
| `processes/` | verb cards: the movements that actually run |
| `effects/CONTEXT.md` | "I am changing X" → which cards to open first |
| `_meta/schema.md` | the closed set of card types and their frontmatter |
| `_templates/` | blank object and process cards; a new card is a copy |

## Route by task

| If you are | Open | Then |
|---|---|---|
| about to change something | `effects/CONTEXT.md` | the cards it names, then their `See` source |
| asking "what is X" | `objects/_index.md` | one card |
| tracing a request end to end | `processes/` | the steps' citations |
| adding a service to this base | `processes/add-a-service.md` | every file it lists |
| running, building, testing | `../dbase` (bare prints every command) | — |

## Names that collide

| Word | Means here | Not |
|---|---|---|
| token | an RS256 JWT auth-service signs: a **login token** (30m) or an **agent token** (30d, role `AGENT`) | the signed **email link** token (HMAC, not a JWT); the `oauth_session` cookie only carries a login token for a minute |
| agent | a user-owned MCP server at `/mcp?agent=<id>` in agent-service; no model runs here | the `AGENT` role, which is only ever on a token |
| server | an `AgentMcpServer` row: an MCP server one agent may call | a compose service |
| trusted | on `agents.trusted-server-urls`: the deployment's own MCP servers | any user-typed URL |
| session | the sign-in exchange (`SessionService`): credentials in, token out | there is no server-side session |
| version | `ver` / `agentVer` token claims, the revocation counters | the app or schema version |
