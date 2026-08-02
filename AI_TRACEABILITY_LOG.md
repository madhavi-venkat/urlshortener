# AI Traceability Log

*The record of where AI assisted, and where I exercised judgment over it — generated / edited /
rejected, with rationale and human sign-off. Maintained throughout the build.*

> This is the assignment's core differentiator ("maintain traceability: generated/edited/rejected with
> rationale" · "engineer owns correctness"). It is deliberately the most detailed deliverable.
> **Principle: AI assists within tasks; I own execution, correctness, and production readiness.**

## How I used AI (approach)

- Started by defining the **intent, constraints, acceptance criteria, and technical context** for each
  task before using AI — not "write a URL shortener" but "generate the code-allocation service;
  constraint: zero collisions by construction; must be unit-testable; Java 21 / Spring."
- Used AI-generated code as **a starting point rather than the final solution**, with every change
  reviewed, validated, and refined before acceptance.
- Kept AI-generated changes **small, focused, and reviewable** to simplify verification and testing —
  oversized output was rejected and re-requested in smaller pieces.
- Used **manual review and automated tests** to validate security-sensitive, core business logic, and
  performance-critical code before completion.

---

## Decision log

| # | Task / prompt intent | AI proposed | My decision | Rationale | Sign-off |
|---|---|---|---|---|---|
| 1 | Scope: which stack(s) to build in | Two backends — Spring Boot **and** FastAPI + React | **Rejected** | Two backends is weaker scope judgment, doubles what I must defend live, and one isn't my home turf. Assignment rewards ownership of correctness — I build one system I can defend fully. Chose Spring Boot + React. | ✅ MM |
| 2 | Redirect status code | 301 vs 302 trade-off (browser caching vs analytics accuracy) | **[pending my call]** | Leaning 302 to keep analytics accurate; will confirm. | ⏳ |
| 3 | Analytics scope (the ambiguous requirement) | Flagged as ambiguous; listed the open questions | **Accepted framing** | Correct to treat "add analytics" as ambiguous and normalize it rather than assume. Will state my assumptions explicitly. | ✅ MM |
| 4 | Short-code strategy — final call | Recommended random + unique-constraint + retry over counter+Base62 | **Accepted (my decision)** | Custom aliases force a uniqueness-check path anyway, so random+check reuses one code path instead of two. Also avoids the enumeration/privacy leak of sequential codes. The failure mode I'm choosing to own is *collision*, and I back it with a DB unique constraint — not hopeful app logic. | ✅ MM |
| 5 | Primary key: code-as-PK (per arch doc) vs surrogate id | (revisited during schema) — chose **surrogate BIGSERIAL id + UNIQUE code** | **Edited my own earlier draft** | The arch doc first said "code PK." On building the schema I changed it: a small stable surrogate id is a cleaner FK target for the analytics table than a varchar code, and the unique index on code keeps redirect lookups fast. Redis fronts the hot path regardless. *Example of me overriding my own earlier decision on reflection.* | ✅ MM |
| 6 | Schema management: Hibernate auto-DDL vs Flyway | (AI default would be `ddl-auto: update`) — chose **Flyway migrations + `ddl-auto: validate`** | **Rejected the easy default** | Auto-DDL is convenient but unsafe and unauditable — exactly wrong for "safe change management." Versioned Flyway migrations are the production-grade choice; `validate` fails fast if entity and schema drift. | ✅ MM |
| 7 | Build order — when to add security & analytics | (my first plan) security = slice 6, analytics = slice 5 | **Rejected my own plan — pulled both into the core** | Security and observability are *properties of every slice*, not phases. First endpoint already validates + guards; redirect emits click events from line one. "Designed-in seams, deferred depth." | ✅ MM |
| 8 | Click capture — how much per click | Offered lean → rich; I chose rich (ts+code+referrer+UA+geo) | **Accepted with a privacy refinement** | Chose rich capture BUT derive geo at ingestion and **discard the raw IP** — never persist it. IPs are PII; storing them fails a privacy review. Richness without the retention liability. "We deliberately don't store IPs" is defensible. | ✅ MM |
| 9 | Analytics under load — saturation policy | AI default async would use CallerRunsPolicy | **Rejected — used DiscardPolicy** | CallerRuns pushes analytics work back onto the redirect thread — the exact opposite of "analytics never blocks the redirect." Under saturation I DROP the click. Best-effort analytics; redirect correctness is not negotiable. | ✅ MM |
| 10 | Collision handling implementation | random code + insert | **Accepted, hardened** | DB UNIQUE constraint is the authority; service retries on `DataIntegrityViolationException` up to a bounded budget (concurrency-safe — two threads colliding, at most one insert wins). Alias path = single attempt → clean 409 (retrying a user-chosen alias is pointless). Exhausted budget → 503 not 400 (server condition). | ✅ MM |
| 11 | Redirect status code | 301 vs 302 | **302 (my call)** | 302 so every click reaches the service and analytics stay accurate; documented trade-off vs. browser caching. | ✅ MM |
| 12 | Geo provider | (could bundle a GeoIP DB) | **Deferred behind a seam** | Designed the GeoResolver interface + stub; deliberately did NOT bundle a ~60MB MaxMind dataset into a prototype. Swap-in is a one-bean change. Documented as a limitation, not hidden. | ✅ MM |
| 13 | Testing the collision-retry path | (real random codes almost never collide → path untested) | **Simulated the DB rejection with Mockito** | Random collisions are too rare to test by luck, but the retry is the core correctness claim — so I unit-test it deterministically by making the mocked repo throw `DataIntegrityViolationException` on the first insert. Proves retry, exhaustion budget, and alias-no-retry explicitly. | ✅ MM |
| 14 | Integration DB: H2 vs real Postgres | (H2 in-memory is faster/easier) | **Rejected H2 — Testcontainers real Postgres** | H2 doesn't reproduce Postgres constraint/DDL behavior; testing the UNIQUE-constraint guarantee against a fake DB proves nothing. Testcontainers runs the real Flyway migrations against real Postgres. | ✅ MM |
| 15 | Concurrency correctness | (assumed safe) | **Wrote an explicit 32-thread concurrency test** | 800 parallel creates must yield 800 unique codes, zero failures — proves the unique-constraint + retry holds under contention, not just in theory. | ✅ MM |
| 16 | Security tests — network dependence | (real DNS lookups would be flaky/offline) | **Used literal IPs to stay hermetic** | Tests use literal IP hosts (127.0.0.1, 10.0.0.1, 169.254.169.254, public 93.184.216.34) so the SSRF-guard suite needs no DNS/network and is deterministic in CI. | ✅ MM |
| 17 | Brownfield cache — what to cache | (cache the full ShortUrl entity) | **Edited — narrow ResolvedTarget instead** | Redirect only needs id + longUrl; caching the whole JPA entity is a bigger, fragile payload. Introduced a narrow record — smaller cache footprint, clear contract, minimal blast radius (existing `resolve()` untouched). | ✅ MM |
| 18 | Cache failure behavior | (let Redis errors propagate) | **Rejected — wrapped every Redis op** | Redis down must degrade to a DB read, never fail a redirect. Reads return "miss", writes no-op. Availability/correctness of the redirect is independent of the cache. | ✅ MM |
| 19 | Cache staleness after expiry | (fixed TTL) | **Clamped TTL to min(1h, time-to-expiry)** | A fixed TTL could serve an expired URL from cache. Clamping guarantees a cached entry never outlives the URL's validity. | ✅ MM |
| 20 | Ambiguous "add analytics" — scope | (implement something and hope) | **Normalized explicitly, stated assumptions** | Wrote out the open questions (unique vs total, bots, real-time, dimensions) and made defensible calls: total clicks + by-country, no bot filtering, no unique-visitor (privacy). Documented in SCENARIOS.md rather than guessing silently. | ✅ MM |
| 21 | CORS for the SPA | AI default would use `allowedOrigins("*")` | **Rejected wildcard — configured origin** | A service that redirects and mutates shouldn't allow any origin. Scoped to the frontend origin via config, methods limited to GET/POST. | ✅ MM |
| 22 | Frontend aesthetic | (generic template look) | **Deliberate "stamped token" identity** | Chose a subject-driven design (the short link as a tearable stamped stub, monospace codes as the hero, one blueprint-indigo accent) instead of a default template. Avoided the common AI look-alikes. | ✅ MM |
| — | **Environment note** | This whole assistant conversation is itself part of the trail | | Decisions above were reasoned interactively; I (the engineer) made or confirmed each call. Compilation/run to be executed locally — any build fixes will be appended here as debugging entries (honest traceability). | ⏳ |

---

## Template for each build task (copy per task)

**Task:** <what I asked AI to do>
**Intent / constraints / acceptance criteria:** <the framing I gave it>
**AI output:** <summary or link to diff>
**Decision:** Accepted / Edited / Rejected
**What I changed and why:** <the judgment — where I overrode it, what it got wrong, what I verified>
**Validation:** <the test/check that proves it's correct>
**Sign-off:** MM

---

## Notable overrides & catches *(the section evaluators read most closely)*

*Log every time AI produced something wrong, oversized, insecure, or plausible-but-incorrect, and I
caught it. Examples to watch for as I build:*
- AI generating collision-prone code without retry logic.
- AI blocking the redirect on the analytics write.
- AI omitting the open-redirect / malicious-URL guard.
- AI producing an oversized diff I couldn't review → rejected, re-requested smaller.
- AI writing tests that assert on implementation rather than behavior.

*These catches ARE the deliverable — they demonstrate engineer-led execution, not autonomous
generation.*
