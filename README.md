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

## Not yet handled

- Concurrency stress testing under real parallel load (Part 3)
- Redis-backed distributed state — current implementation only works correctly on a single instance (Part 4)
- Token bucket for controlled bursts (Part 5)
- Failure handling when shared state is unavailable (Part 6)
- Multi-tenant configurable limits (Part 7)
- Observability via Micrometer/Prometheus (Part 8)
- Load testing with real throughput/latency numbers (Part 9)
- Dockerization and final documentation (Part 10)

## Tech stack (so far)

Java 17, Spring Boot 3.3.x (Web, Validation, Actuator), in-memory `ConcurrentHashMap` (Redis introduced Part 4)