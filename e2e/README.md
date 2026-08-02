# E2E tests (Playwright)

Browser-driven end-to-end tests against the real app — real backend, real
Postgres, real Redis. This replaces the old Testcontainers-based
`UrlShortenerIntegrationTest` (removed — Testcontainers' Docker client has a
compatibility issue with recent Docker Desktop builds on this project's dev
machines; `docker compose` itself is unaffected, which is what these tests
run against).

## Prerequisites

```bash
# From the repo root:
docker compose up -d      # Postgres + Redis
mvn spring-boot:run       # backend on :8080
```

The frontend dev server (`:5173`) does **not** need to be started manually —
Playwright starts it for you (see `webServer` in `playwright.config.ts`) and
reuses one that's already running if you have `npm run dev` going in another
terminal.

## Install (first time)

```bash
cd e2e
npm install
npx playwright install chromium
```

## Run

```bash
npm test              # headless, all specs
npm run test:headed   # see the browser
npm run test:ui       # Playwright's interactive UI mode — best for writing/debugging
npm run report        # open the last HTML report
```

Run a single file: `npx playwright test tests/create-url.spec.ts`

## What's covered

| Spec | Flow |
|---|---|
| `create-url.spec.ts` | Generated code, custom alias, duplicate-alias rejection, client-side and server-side validation |
| `redirect.spec.ts` | 302 + correct `Location`, 404 on an unknown code |
| `dashboard.spec.ts` | Created links appear in the table, analytics overview KPI tiles, column sorting, row expand |
| `edit.spec.ts` | Editing a destination updates the dashboard; the code itself has no edit field |
| `analytics.spec.ts` | A real click shows up in the per-code stats; period tabs (Day/Week/Month) |

## Notes

- **Windows: the Vite dev server Playwright starts for you can outlive the test run.**
  `webServer` spawns `npm run dev`, and on Windows the underlying `vite` process
  sometimes isn't cleaned up when the `node.exe` wrapper is (a process-tree quirk,
  not a Playwright bug specifically). If port 5173 is still listening after a run
  and you want it gone, find and stop it:
  `Get-Process -Id (Get-NetTCPConnection -LocalPort 5173).OwningProcess | Stop-Process`.
  Harmless to leave running if you're about to run the suite again.

- **No test-database reset between runs.** Every spec that creates data uses
  a unique alias/destination (`tests/helpers.ts`) so runs don't collide with
  leftover data from a previous run or from manual testing — but the DB does
  accumulate rows over time. Fine for now; revisit if that becomes a problem
  (e.g. a `docker compose down -v && up -d` before a CI run).
- Specs seed data via direct API calls (`createShortUrl` in `helpers.ts`)
  when creation itself isn't the behavior under test, so each spec stays
  focused on the one flow it's named for.
- `redirect.spec.ts` checks the HTTP response directly (status + `Location`
  header) rather than navigating a real browser to the destination — the
  seeded destinations are synthetic `example.com` URLs with no guarantee
  they're reachable, and what's under test is *our* redirect, not whether
  the destination page loads.
