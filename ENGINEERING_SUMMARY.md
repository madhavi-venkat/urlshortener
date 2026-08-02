# Final Engineering Summary

## 1. Plan & rationale

Built a URL shortener as one coherent system that carries all three required scenarios
(greenfield core, brownfield cache, ambiguous analytics), rather than three disposable demos.
The guiding thesis: a shortener's happy path is trivial; the engineering is in the **failure
modes**, which are mostly *silent* (a wrong redirect, a stale cache, a lost click). Every
significant decision is oriented around making silent failure impossible or loud.

Work proceeded in reviewable slices — skeleton/schema → core API → tests → cache → analytics
→ frontend → docs. AI accelerated each slice; the engineer defined intent and acceptance
criteria, then accepted/edited/rejected output with recorded rationale (see
`AI_TRACEABILITY_LOG.md`). Slices were kept small enough to review by reading — AI output that
exceeded a reviewable size was rejected and re-requested smaller.

## 2. Key decisions & trade-offs

| Decision | Chosen | Trade-off accepted |
|---|---|---|
| Code generation | Random Base62 + **DB unique constraint** + bounded retry | Rare retry vs. enumerable sequential codes; chose to own collisions, not enumeration |
| Uniqueness authority | Database, not application logic | Correctness guaranteed by the DB even under concurrency |
| Schema management | Flyway migrations + `ddl-auto: validate` | More ceremony than auto-DDL, but auditable and safe |
| Redirect status | 302 | Every click reaches us (accurate analytics) vs. browser caching |
| Cache | Redis cache-aside, graceful degradation | Redis outage = slower, never wrong or unavailable |
| Cache TTL | min(1h, time-to-expiry) | Cached entry can never outlive URL validity |
| Analytics | Async, non-blocking, discard-on-saturation | A dropped click under extreme load vs. ever slowing a redirect |
| Privacy | Derive geo, **discard raw IP** | Meets geo requirement without PII-retention liability |
| Security | http/https only + SSRF/internal-target guard | Blocks abuse without restricting legitimate external destinations |

## 3. Validation

- **Unit:** code generation, URL-safety guard (hermetic, literal IPs), and the collision-retry
  path proven deterministically by simulating the DB's unique-constraint rejection.
- **Integration (real Postgres + Redis via Testcontainers):** create→redirect **exact-URL
  parity**; 404 on unknown/expired; alias conflict → 409; and a **32-thread / 800-create
  concurrency test** proving no duplicate codes.
- **Quality gates:** the parity and concurrency tests are the core correctness proofs; the
  security suite covers scheme and SSRF guards.

## 4. Risks & failure modes (register)

| Risk | Mitigation | Residual |
|---|---|---|
| Code collision | DB unique constraint + retry | Exhausted budget → 503 (extreme load only) |
| Cache/DB divergence | TTL clamped to expiry; cache-aside | Manual mapping change would need explicit evict (not yet built) |
| Analytics loss | Async, best-effort by design | Clicks dropped under saturation (accepted) |
| Enumeration of codes | Random (non-sequential) codes | — |
| Open redirect / SSRF | Scheme + internal-target guard at create | DNS rebinding at redirect time (documented, out of scope) |
| Redis outage | Graceful fallthrough to Postgres | Degraded redirect latency during outage |
| IP/PII exposure | Raw IP never persisted | — |

## 5. Assumptions
- Single-instance prototype. Code allocation is safe across instances (DB constraint), but
  horizontal scale isn't load-tested here.
- Analytics is best-effort, not guaranteed delivery.
- "A click" = one successful redirect; totals are all-time.
- No authentication / multi-tenancy — deliberately out of scope.

## 6. Limitations & next steps
- **Geo** uses a stub resolver; production plugs in MaxMind GeoLite2 (one-bean swap).
- **Analytics**: no unique-visitor counts (by privacy choice), no bot filtering, no
  time-series — all natural next increments.
- **Cache invalidation** on mapping mutation (delete/deactivate) is designed for (evict exists)
  but delete endpoints aren't built.
- **Rate limiting** is noted as a guard but not implemented in the prototype.
- **Scale**: at very high write volume, random-code retry pressure rises; a segmented counter
  or pre-minted code pool would be the next design step.

## 7. AI usage, honestly stated
AI was used across implementation, tests, and documentation as an accelerator within tasks
I scoped and owned. The traceability log records the concrete accept/edit/reject decisions —
including where I rejected AI defaults (Hibernate auto-DDL; `CallerRunsPolicy` on the async
executor; capturing raw IPs; an oversized multi-backend scope) in favor of choices I can
defend. The engineer owns correctness, maintainability, and production-readiness; AI assisted
within that ownership.
