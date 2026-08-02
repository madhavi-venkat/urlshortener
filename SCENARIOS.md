# Three Scenarios

The assignment asks for three engineering scenarios, each showing decomposition,
AI-assisted execution, and validation. This project deliberately builds all three into
one coherent system rather than three throwaway demos.

---

## Scenario 1 — Greenfield: the core service

**Requirement:** build a URL shortener from scratch — create a short code for a long URL,
and redirect on lookup.

**Decomposition:**
1. Data model + schema (Flyway migration, `short_url`).
2. Code generation strategy (random Base62) with a correctness backstop (DB unique constraint).
3. Create endpoint (`POST /api/v1/urls`) with validation + security guard.
4. Redirect endpoint (`GET /{code}`) returning 302, with 404 for unknown/expired.
5. Error contract (RFC-7807 ProblemDetail).

**AI-assisted execution:** each unit was defined with intent + acceptance criteria before
prompting (e.g. "generate the code allocator; constraint: uniqueness guaranteed by the DB,
not app logic; must be unit-testable"). AI drafts were accepted, edited, or rejected — see
the traceability log (entries 5–13). Notable engineer overrides: rejected Hibernate auto-DDL
for Flyway; hardened the collision path to lean on the DB constraint with bounded retry.

**Validation:** unit tests for generation and validation; integration tests against real
Postgres for the create→redirect **exact-URL parity** proof and 404 semantics; a 32-thread
concurrency test proving no duplicate codes.

---

## Scenario 2 — Brownfield: add a Redis cache to the redirect hot path

**Requirement (enhancement):** redirects are the dominant, latency-sensitive operation
(~100:1 read:write). Add caching without regressing correctness.

**Codebase reasoning / impact analysis:**
- *Impacted:* the redirect read path only. The create path, schema, and existing `resolve()`
  contract are untouched — deliberately minimal blast radius.
- *Approach:* introduced a narrow `ResolvedTarget` (id + longUrl) rather than caching the
  full JPA entity — smaller payload, clear boundary for what the redirect actually needs.
- *New:* `ShortUrlCache` (cache-aside) + `resolveForRedirect()` which reuses the existing
  `resolve()` on a miss. One new method + one controller line changed.

**Key design decisions:**
- **Graceful degradation:** every Redis call is wrapped — Redis down ⇒ read returns "miss"
  (fall through to Postgres), write no-ops. A cache outage costs latency, never correctness
  or availability. *We never fail a redirect because the cache is unavailable.*
- **TTL clamped to expiry:** cache TTL = min(1h, time-until-URL-expiry), so a cached entry
  can never outlive the URL's validity — no stale redirect after expiry.

**Validation:** the existing integration tests now run with a real Redis container; behavior
is identical whether served from cache or DB (the parity tests still pass through the cached
path).

---

## Scenario 3 — Ambiguous: "add analytics"

**Requirement as given:** "add analytics." Under-specified on purpose.

**Normalization (turning ambiguity into a defined problem):** I identified the open
questions and made explicit, defensible decisions rather than guessing silently:

| Ambiguity | Decision | Rationale |
|---|---|---|
| What is "a click"? | One successful redirect = one click | Simplest correct definition; matches user intuition |
| Unique vs. total? | **Total** (all-time) | Unique visitors need identity (cookie/IP) we deliberately **don't** store for privacy |
| Real-time vs. batch? | Real-time from the event table | No batch infra needed at this scale |
| Bot filtering? | **Out of scope**, documented | Meaningful bot detection is a project of its own |
| What dimensions? | Total + by-country | Country is already derived at click time; high value, low cost |
| Time-series? | Out of scope, noted as next step | Keeps the prototype focused |

**Designed-in, not bolted-on:** analytics was a first-class concern from the start — the
`click_event` table and the async, non-blocking click hook existed from the first version of
the redirect path. This scenario is the **reporting** layer on a stream that was already
flowing.

**Privacy decision:** geo is derived from IP at ingestion and the **raw IP is discarded** —
never persisted. We meet the geo-analytics requirement without inheriting an IP-retention
liability that the requirement didn't ask for.

**Execution:** `StatsService` aggregates total + by-country via repository queries;
`GET /api/v1/urls/{code}/stats` exposes it; the frontend renders it.

**Validation:** stats reflect recorded clicks; async recording is failure-isolated so a
stats/analytics fault can never affect the redirect.

**Stated assumptions & limitations:** total (not unique) clicks; no bot filtering; geo
depends on a GeoIP provider not bundled in the prototype (stub returns UNKNOWN); no
time-series. All are deliberate scope choices, documented rather than hidden.
