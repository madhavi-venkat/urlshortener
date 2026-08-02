# Stub — URL Shortener

A URL shortener service built as an AI-assisted engineering exercise: create short codes,
redirect, custom aliases, expiry, and click analytics — with the engineering judgment,
validation, and AI-usage traceability documented alongside the code.

**Java 21 · Spring Boot 3 · PostgreSQL · Redis · React (Vite)**

## Documents
- `ARCHITECTURE.md` — components, decisions, failure modes, risk register.
- `SCENARIOS.md` — the greenfield / brownfield / ambiguous scenarios.
- `AI_TRACEABILITY_LOG.md` — where AI was used, and every accept/edit/reject with rationale.
- `ENGINEERING_SUMMARY.md` — plan, trade-offs, validation, assumptions, limitations.

---

## Prerequisites
- JDK 21, Maven 3.9+
- Docker (for Postgres + Redis)
- Node 18+ (for the frontend)

## Run the backend

```bash
# 1. Start Postgres + Redis
docker compose up -d

# 2. Run the service (Flyway applies migrations on startup)
mvn spring-boot:run
# API on http://localhost:8080
# Swagger UI on http://localhost:8080/swagger-ui.html
# Health check on http://localhost:8080/actuator/health
```

## Run the frontend

```bash
cd frontend
npm install
npm run dev
# UI on http://localhost:5173
```

## Run the tests

```bash
mvn test
# Unit tests only (service logic, validation, code generation) — no Docker needed.
```

End-to-end coverage (create → redirect → dashboard → edit → analytics, against the real
backend/Postgres/Redis) lives in a separate Playwright suite — see
[`e2e/README.md`](./e2e/README.md).

---

## API docs

Full request/response schemas, validation rules, and every error shape live in the
OpenAPI spec — generated from the code, so it can't drift from what's actually
deployed:

- **Swagger UI** (interactive, try-it-out): http://localhost:8080/swagger-ui.html
  (with the backend running)
- **Raw OpenAPI JSON**: http://localhost:8080/v3/api-docs
- **Static copy**: [`openapi.yaml`](./openapi.yaml) at the repo root — the same spec,
  portable/offline (paste into Swagger Editor, Postman, Redoc, etc. without running
  the app).

### Endpoints at a glance

| Method  | Path                              | Purpose                                   |
|---------|-----------------------------------|--------------------------------------------|
| `POST`  | `/api/v1/urls`                    | Create a short URL                        |
| `GET`   | `/{code}`                         | Redirect to the destination (302)         |
| `GET`   | `/api/v1/admin/urls`              | List every short URL                      |
| `GET`   | `/api/v1/admin/urls/{code}`       | One short URL's details                   |
| `PATCH` | `/api/v1/admin/urls/{code}`       | Edit destination and/or expiry            |
| `GET`   | `/api/v1/admin/urls/{code}/stats` | Analytics: totals, geo, day/week/month    |

**Limitation:** `/api/v1/admin/**` has no access control yet — anyone who can reach the
API can reach it. Acceptable for this prototype; noted as the next thing to add (e.g.
HTTP Basic) before this is exposed anywhere but localhost.

## Health checks

| Endpoint | Answers |
|---|---|
| `GET /actuator/health` | Aggregate status only (`{"status":"UP"}`) — no DB/Redis details exposed, since there's no auth in front of it yet. |
| `GET /actuator/health/liveness` | Is the JVM itself alive? Independent of Postgres/Redis — a dependency outage should never make an orchestrator restart an otherwise-healthy instance. |
| `GET /actuator/health/readiness` | Can this instance actually serve traffic? Gated on Postgres (the source of truth). **Deliberately not gated on Redis** — `ShortUrlCache` is designed to degrade to Postgres-only on a Redis outage, so a Redis blip shouldn't pull instances out of the load-balancer rotation while the app is still fully correct. |

Wire `liveness`/`readiness` to your orchestrator's probes (e.g. Kubernetes
`livenessProbe`/`readinessProbe`) rather than the plain `/health` endpoint.

## Quick smoke test
```bash
curl -s -X POST localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://spring.io/projects/spring-boot"}'

curl -si localhost:8080/<code-from-above>   # -> 302 with Location header
```
