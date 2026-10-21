# auth-client

auth-service's client half: the one copy of how a service checks a caller's token without asking
auth-service. A plain jar, not a service: no port, no database, no main class. todo-service, agent-service
and `libs/mcp-server` depend on it; auth-service only in its tests, because it is the side that mints.

```
com.demo.auth.client   JwtVerifier   the Authorization header -> a Caller, or 401
                       Revocations   the /internal/token-versions feed, polled at most every 10s
                       Caller        user id, role, and the agent an agent token is pinned to
                       CallerResolver  a `Caller` controller parameter: the user, 401, or 403 for an agent
```

`JwtVerifier` fetches auth-service's JWKS once, caches it, and verifies RS256 only -- a token asking for
`none` or a symmetric algorithm is refused before its signature is looked at. `sub` and `exp` are required, and
`iss` must be `auth-service` and `aud` `demo-base` exactly, so a token another stack minted is refused.
A token whose `ver` is older than the user's last revocation is refused. An `AGENT` token must name its
agent in the `agent` claim, or it is refused rather than read as "every agent of the owner", and must carry
`agentVer` at or above that agent's own last revocation -- a token with no `agentVer` at all is refused,
because one minted before the claim existed cannot be told from one revoked since.

`Revocations` reads one map: users by id, agents as `agent:<id>`, so the two cannot collide. It **fails
closed, late**: if auth-service cannot be reached the last list stands for 5 minutes (`STALE_AFTER`), then every
token is 401 until a read succeeds; before the first good read there is no list, so nothing passes. A revocation
made while auth-service is down is therefore unseen for at most those 5 minutes. A request that arrives while
another's read is in flight waits for that read rather than being refused.

`Caller` answers the questions the services ask -- `isAgent`, `readsEveryone`, `writesEveryone`,
`mayActAs`, `describe` -- and every one of them says "no" for a role it does not know.

`CallerResolver` lets a controller method take `Caller caller` instead of reading the header itself. It
refuses an agent token with 403: an agent's only way in is `/mcp` through agent-service, where its owner's
READ/WRITE setting and activity log apply, so every REST endpoint refuses one without having to remember to.
The MCP servers are not controllers; they call `JwtVerifier` directly.

## Using it

The beans are `@Component`s under `com.demo.auth.client`, outside each service's own package, so the service's
`@SpringBootApplication` lists both packages in `scanBasePackages`. Two properties:

| Property | Environment | Default |
|---|---|---|
| `auth.jwks-uri` | `AUTH_JWKS_URI` | `http://localhost:1001/api/jwks.json` |
| `auth.token-versions-uri` | `AUTH_TOKEN_VERSIONS_URI` | `http://localhost:1001/internal/token-versions` |

## Tests

`./mvnw -pl libs/auth-client test`. `JwtVerifierTests` runs against a real throwaway JWKS server rather than a
mock: the advertised key and nothing else, expiry, the revocation feed for users and for one agent alone,
and the agent claims.
