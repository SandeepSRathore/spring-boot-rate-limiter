# Spring Boot Rate Limiter

A distributed, server-side rate limiter built from the design in *System Design Interview* by Alex Xu,
**Volume 1, Chapter 4 "Design a Rate Limiter"**. It implements the chapter's "detailed design" (Figure 4-13).

```
                      ┌──────────────── rules on disk ─────────────────┐
                      │  config/rate-limit-rules.yaml                  │
                      └──────────────────────┬─────────────────────────┘
                                             │ RulesRefreshWorker pulls every 10s
                                             ▼
                                        ┌──────────┐
                                        │ RuleCache│ (in memory)
                                        └────┬─────┘
                                             │ load rules
 ┌────────┐  request   ┌─────────────────────┴───────┐  not limited   ┌───────────────┐
 │ client ├───────────►│ rate limiter middleware     ├───────────────►│ API servers   │
 │        │◄───────────┤ (RateLimitFilter)           │                │ DemoController│
 └────────┘  429 +     └─────────────┬───────────────┘                └───────────────┘
           X-Ratelimit-*             │ counters / timestamps (atomic Lua scripts)
                                     ▼
                                ┌─────────┐
                                │  Redis  │  shared by every app instance
                                └─────────┘
```

## How the book maps to the code

| Book concept | Where |
|---|---|
| Rate limiter as middleware in front of the API servers | `middleware/RateLimitFilter` (servlet filter, runs before controllers) |
| Rules stored on disk (Lyft-style YAML) | `config/rate-limit-rules.yaml`, parsed by `rules/RuleLoader` |
| Workers pull rules into a cache | `rules/RulesRefreshWorker` (`@Scheduled`) → `rules/RuleCache` |
| Counters in an in-memory store (Redis `INCR`/`EXPIRE`) | `algorithm/*RateLimiter` + `src/main/resources/scripts/*.lua` |
| Token bucket, leaking bucket, fixed window counter, sliding window log, sliding window counter | one class + one Lua script each |
| HTTP 429 + `X-Ratelimit-Remaining`, `X-Ratelimit-Limit`, `X-Ratelimit-Retry-After` | `RateLimitFilter` |
| Race conditions in a distributed environment | every check-and-update is one Lua script, and Redis runs a script atomically |
| Synchronization between rate limiter servers | all instances share one Redis; keys use `{hash tags}` so it also works on Redis Cluster |
| Rate limiter failure shouldn't take down the system | `ratelimiter.fail-open=true` + 250 ms Redis timeout |
| Monitoring | Micrometer counter `ratelimiter.requests{rule,algorithm,outcome}` at `/actuator/metrics` |

### Algorithm notes

- **Token bucket**: tokens are refilled lazily from elapsed time, so no background refiller is needed.
- **Leaking bucket**: a queued request is *delayed* until its slot, not rejected; only a full queue rejects. The
  filter holds the request (cheap, thanks to virtual threads), so a burst reaches the API at a steady rate.
- **Fixed window counter**: `INCR` on a per-window key. `FixedWindowCounterRateLimiterTest` shows the edge-burst weakness.
- **Sliding window log**: a Redis sorted set of timestamps. As in the book, a rejected request's timestamp stays in the
  log. The log is trimmed to the newest `limit` entries, which caps memory without changing any decision.
- **Sliding window counter**: `current + previous × overlap`, rounded down like the book's example (6.5 → 6).

The tests under `src/test/java/.../algorithm` walk through the book's examples step by step.

## Run it

Requires Java 21+, Maven, and Redis.

```bash
docker compose up -d          # Redis on localhost:6379
mvn spring-boot:run
```

Try it:

```bash
# login: 5 per minute per IP (sliding window log). The 6th call returns 429.
for i in $(seq 1 6); do curl -s -i -X POST localhost:8080/api/login | grep -E "HTTP|X-Ratelimit"; done

# messages: token bucket, bursts of 5, then 10/min, per user
for i in $(seq 1 7); do curl -s -o /dev/null -w "%{http_code}\n" -H "X-User-Id: alice" localhost:8080/api/messages; done

# orders: leaking bucket, 2/sec outflow, queue of 5. Watch the responses come back spaced out.
for i in $(seq 1 8); do curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" -X POST -H "X-API-Key: k1" localhost:8080/api/orders & done; wait

# metrics
curl -s "localhost:8080/actuator/metrics/ratelimiter.requests?tag=outcome:throttled"
```

**Distributed setup:** start a second instance with `SERVER_PORT=8081 mvn spring-boot:run`. Both share the same
counters in Redis, so a client can't get twice the limit by switching instances.

**Hot reload:** edit `config/rate-limit-rules.yaml` while the app runs. The change applies within 10 seconds. An
invalid file is logged and ignored, and the last good rules stay active.

## Configuration (`application.yml`)

| Property | Default | |
|---|---|---|
| `ratelimiter.enabled` | `true` | turn the middleware off |
| `ratelimiter.rules-location` | `file:config/rate-limit-rules.yaml` | any Spring resource location |
| `ratelimiter.rules-refresh-interval` | `10s` | how often workers re-read the rules |
| `ratelimiter.fail-open` | `true` | Redis down: let traffic through (`true`) or return 503 (`false`) |
| `ratelimiter.trust-forwarded-for` | `false` | use `X-Forwarded-For` for the client IP (only behind a trusted proxy) |

## Tests

```bash
mvn test
```

No Docker needed: the tests run the real Lua scripts against [jedis-mock](https://github.com/fppt/jedis-mock),
an in-JVM Redis server, with a controllable clock.

## Not implemented (extension ideas from the chapter)

- Forwarding throttled requests to a message queue for later processing, instead of dropping them.
- Rate limiting at other layers (e.g. IP-level with iptables, layer 3).
- A shared rules store (e.g. a database or config service) instead of a local file per instance.
