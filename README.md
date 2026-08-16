# Distributed Rate Limiter

A standalone rate limiting service built with Spring Boot, designed to be called by other services (not embedded as a library). Built incrementally over 10 parts to demonstrate real backend engineering: concurrency correctness, distributed state, failure handling, and observability — not just CRUD.

## API Contract

```
POST /v1/ratelimit/check
Content-Type: application/json

Request:
{
  "algorithm": "sliding_window_counter",
  "key": "user123",
  "endpoint": "/api/search",
  "limit": 3,
  "windowSeconds": 60
}

Response (200 OK - allowed):
{ "allowed": true, "remaining": 2, "resetAt": 1786200420 }

Response (429 Too Many Requests - denied):
{ "allowed": false, "remaining": 0, "resetAt": 1786200420 }
```

`resetAt` is Unix epoch seconds — matches GitHub/Stripe's `X-RateLimit-Reset` convention. Composite key = `key:endpoint`, since different endpoints (e.g. an LLM call vs. a cached read) have very different cost and deserve different limits.

## Part 1 — Fixed Window Counter

Simplest algorithm: time divided into clock-aligned windows (e.g. `12:05:00–12:06:00`), one counter per `(key, endpoint)`, reset on window change, reject over limit with HTTP 429. Backed by `ConcurrentHashMap` + `AtomicInteger`, O(1) memory per key.

**Known flaw — boundary burst:** up to 2x the limit can get through if requests straddle a window edge (e.g. 10 requests at `12:05:59`, 10 more at `12:06:01` — both allowed, 20 requests in ~2 seconds against a 10/min limit). Root cause: the counter hard-resets to zero at each window boundary with no memory of prior activity.

## Part 2 — Strategy Pattern + Sliding Window Algorithms

Refactored fixed window logic behind a `RateLimiterStrategy` interface so algorithms are pluggable — adding a new one (e.g. token bucket, Part 5) means one new class + one line in a lookup map, no changes to existing code. Algorithm is now selected per-request via the `"algorithm"` field. Kept internal `RateLimitResult` separate from the external `RateLimitResponse` DTO, so algorithm logic isn't coupled to the API's JSON shape.

**Sliding window log** — stores every request timestamp per key, prunes anything older than the window on each request. Exact, no boundary burst, but O(n) memory per key.

**Sliding window counter** — approximates log's accuracy in O(1) memory using two counters (current + previous window), weighted by how far into the current window the request falls:
```
overlapPercentage = 1 - (timeElapsedInCurrentWindow / windowSeconds)
estimatedCount = (previousWindowCount × overlapPercentage) + currentWindowCount
```
No hard reset at window edges, so the boundary burst is fixed while keeping fixed window's memory cost. This is the approach production systems like Cloudflare use.

**Verified with a live side-by-side test**: same burst scenario (5 requests, wait past window reset, fire 5 more) run against both algorithms on fresh keys — fixed window allowed the full second burst (reproducing the Part 1 bug on demand), sliding window counter correctly rejected most of it.

## Part 3 — Concurrency Stress Testing

Wrote JUnit tests using `ExecutorService` + `CountDownLatch` to fire 100+ threads at the same key simultaneously (all released at the same instant via a start-gate latch), and asserted the total allowed count never exceeds the configured limit — proving correctness under real parallel load instead of assuming it from reading the code.

**Fixed window** and **sliding window counter** held up cleanly — exactly the limit allowed, every run, even at 1000 concurrent threads across 10 repeated runs for sliding window counter.

**Sliding window log leaked** — 11 allowed against a limit of 10. Root cause turned out to be a plain sequential off-by-one, not a race condition: the boundary check used `size() > limit` instead of `size() >= limit`, so a request landing exactly at the limit was incorrectly allowed through before rejection kicked in one request too late. Fixed by changing the comparison and moving the check before the mutation (never mutate shared state for a request that might get rejected). Re-verified with the same stress test — exactly 10 allowed, every run, after the fix.

Notably, the bug wasn't timing-dependent — it would have reproduced with a single thread sending requests one at a time. Manual Postman testing on Part 2 missed it because it only tested exactly up to the limit and stopped at the first `429`, never precisely probing the "one past what should be rejected" boundary. The stress test caught it because it fired far more requests than the limit allowed.

## Part 4 — Redis-Backed Distributed State

In-memory `ConcurrentHashMap` only tracks counts within a single JVM — horizontally scaled instances would each keep separate counts, letting N instances × limit through. Redis provides the shared state across instances (Redis Cloud free tier used here, not Docker).

**Naive implementation** (deliberate): same overlap formula as Part 2, but `GET` (previous/current window) and `INCR` are separate network round-trips. Each command is individually atomic (Redis is single-threaded), but the gap *between* commands isn't — a classic **TOCTOU bug** (time-of-check to time-of-use), not a locking issue.

**Stress test result:** 100 threads, limit 10 → **100/100 allowed**. Every thread read the same stale pre-increment count before any `INCR` landed — an unbounded leak, unlike Part 3's off-by-one.

**Fix — Lua scripting:** bundled GET + compute + compare + INCR into a single script executed via `redisTemplate.execute(RedisScript, ...)`. Redis runs an entire script as one atomic unit (same single-threaded guarantee that makes a lone `INCR` atomic, applied to the whole sequence) — no client can interleave a read mid-script.

**Re-verified:** same stress test → 10/10 allowed, confirmed at 500 threads.

**Failure handling:** wrapped in try/catch — Redis unreachable fails **open** by default (avoids turning a Redis outage into a full API outage), with critical endpoints expected to override to fail-closed.

## Not yet handled

- Token bucket for controlled bursts (Part 5)
- Failure handling when shared state is unavailable (Part 6)
- Multi-tenant configurable limits (Part 7)
- Observability via Micrometer/Prometheus (Part 8)
- Load testing with real throughput/latency numbers (Part 9)
- Dockerization and final documentation (Part 10)

## Tech stack (so far)

Java 17, Spring Boot 3.3.x (Web, Validation, Actuator), in-memory `ConcurrentHashMap` (Redis introduced Part 4)