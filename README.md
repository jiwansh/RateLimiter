# Distributed Rate Limiter

A standalone rate limiting service built with Spring Boot, designed to be called by other services (not embedded as a library). Built incrementally over 10 Parts to demonstrate real backend engineering: concurrency correctness, distributed state, failure handling, and observability — not just CRUD.

## Part 1 — Foundation: Fixed Window Counter

### What was built

A working `POST /v1/ratelimit/check` endpoint that enforces a request limit per `(key, endpoint)` pair using the **fixed window counter** algorithm, backed by an in-memory `ConcurrentHashMap`.

### API Contract

```
POST /v1/ratelimit/check
Content-Type: application/json

Request:
{
  "key": "user123",
  "endpoint": "/api/search",
  "limit": 3,
  "windowSeconds": 60
}

Response (200 OK - allowed):
{
  "allowed": true,
  "remaining": 2,
  "resetAt": 1786200420
}

Response (429 Too Many Requests - denied):
{
  "allowed": false,
  "remaining": 0,
  "resetAt": 1786200420
}
```

### How Fixed Window Counter works

- Time is divided into fixed, clock-aligned windows (e.g., `12:05:00–12:06:00`, `12:06:00–12:07:00`).
- Each `(key, endpoint)` pair tracks one counter for the current window.
- On each request: if we're still in the same window, increment the counter. If the window has changed, reset the counter to 1.
- If the counter exceeds the configured limit, the request is rejected with HTTP 429.

Window alignment is computed as:
```java
currentWindowStart = (nowSeconds / windowSeconds) * windowSeconds
```
Integer division rounds down, snapping "now" to the start of its window.

### Known limitation: boundary burst

Fixed window counter has a well-known flaw: a client can send up to `2x` the allowed limit in a very short span if the requests straddle a window boundary.

**Example:** limit = 10 requests/minute.
- Client sends 10 requests at `12:05:59` (last second of the window) → all allowed, counter hits 10.
- Client sends 10 more requests at `12:06:01` (first second of the next window) → counter resets, all 10 allowed again.

Result: 20 requests allowed in roughly a 2-second span, against a limit designed to be 10 per 60 seconds.

This flaw is why fixed window is used here only as the baseline/naive implementation. **Sliding window counter** (Part 2) fixes this by weighting the previous window's count based on how far into the current window the request falls, while keeping the same O(1) memory cost per key.

### Concurrency note

`ConcurrentHashMap.compute()` is atomic per key, so two threads hitting the same key simultaneously cannot both win the "start a new window" decision — this was relied on for correctness but not yet stress-tested under real concurrent load. That stress test is Part 3.

### Design decisions made toPart

- **Composite key = `key:endpoint`**, not just `key` — different endpoints have very different cost profiles (e.g., an LLM call vs. a cached search read), so a single global limit per user doesn't reflect real resource usage. Limits are configured per endpoint, not globally.
- **HTTP 429** is returned on rejection rather than `200` with `allowed: false` — using the correct standard status code for "rate limit exceeded" rather than overloading `200`.
- **Request validation** (`@NotBlank`, `@Positive`) rejects malformed input (empty key, negative limit) before it reaches business logic.

### Not yet handled (upcoming Parts)

- Sliding window log and sliding window counter algorithms (Part 2)
- Concurrent stress testing and race condition fixes (Part 3)
- Redis-backed distributed state — current implementation only works correctly on a single instance; horizontal scaling with multiple instances would allow the limit to be exceeded, since each instance has its own separate in-memory map (Part 4)
- Token bucket algorithm for controlled bursts (Part 5)
- Failure handling when the shared state store is unavailable (Part 6)
- Multi-tenant configurable limits (Part 7)
- Observability via Micrometer/Prometheus (Part 8)
- Load testing with real throughput/latency numbers (Part 9)
- Dockerization and final documentation (Part 10)

### Tech stack (so far)

- Java 17
- Spring Boot 3.3.x (Web, Validation, Actuator)
- In-memory `ConcurrentHashMap` (Redis introduced Part 4)