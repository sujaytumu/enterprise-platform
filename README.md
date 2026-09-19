# Enterprise Platform — Render-friendly consolidated build

[![CI](https://github.com/sujaytumu/enterprise-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/sujaytumu/enterprise-platform/actions/workflows/ci.yml)

This is a simplified, deployment-friendly rebuild of the original 7-microservice
reference platform, collapsed into **one Spring Boot backend + one React
frontend + one Postgres database** — sized to fit entirely on Render's free
tier.

## What changed from the original design

The original repo modeled a real payment network's shape: a separate
API gateway, core processing engine, ISO 8583 payment switch, fraud/risk
engine, tokenization vault, card management, and clearing/settlement service,
wired together with Kafka. That's realistic architecture, but it needs
7 running services + a message broker + a database just to boot — not
something that fits a free hosting tier.

This version keeps the **domain logic** (accounts, tokenized cards,
transaction authorization with balance/status checks) as REST endpoints in a
single Spring Boot app, and drops:
- Kafka / event streaming (calls are synchronous now)
- The separate fraud-scoring, clearing/settlement, and tokenization-vault
  *services* (their core logic — balance checks, card issuance, decline
  reasons — lives in the one backend instead)
- The ISO 8583 switch simulation

This is a learning/demo scaffold, not anything close to PCI-DSS scope.

## Stack

- Backend: Java 17, Spring Boot 3.3, Spring Data JPA, Postgres
- Frontend: React 18 + Vite
- Database: Postgres (Render's free managed instance)

## Local development

### Configuration (environment variables)

Nothing secret is committed. The backend reads its config from the environment:

| Variable          | Used by  | Default                 | Notes |
|-------------------|----------|-------------------------|-------|
| `PGPASSWORD`      | backend  | **none — required**     | Database password; the app won't start without it |
| `PGHOST`          | backend  | `localhost`             | |
| `PGPORT`          | backend  | `5432`                  | |
| `PGDATABASE`      | backend  | `enterprise`            | |
| `PGUSER`          | backend  | `postgres`              | |
| `FRONTEND_ORIGIN` | backend  | `http://localhost:5173` | Allowed CORS origin — set to your deployed frontend URL |
| `PORT`            | backend  | `8080`                  | |
| `VITE_API_URL`    | frontend | `http://localhost:8080` | Backend base URL, baked in at build time |

### One command (Postgres + backend + frontend)

```
cp .env.example .env        # then set POSTGRES_PASSWORD in .env
docker compose up --build
# backend:  http://localhost:8080
# frontend: http://localhost:5173
```

### Run the pieces individually

```
docker run --name enterprise-db -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=enterprise -p 5432:5432 -d postgres:16
cd backend && PGPASSWORD=change-me mvn spring-boot:run   # http://localhost:8080
cd frontend && npm install && npm run dev                 # http://localhost:5173
```

## API

| Method | Path                          | Description |
|--------|-------------------------------|-------------|
| GET    | `/api/accounts`               | List accounts |
| GET    | `/api/accounts/{id}`          | Get one account |
| POST   | `/api/accounts`               | Create account: `holderName`, `openingBalance`, optional `currency` |
| GET    | `/api/cards?accountId=`       | List cards (optionally for one account) |
| POST   | `/api/cards`                  | Issue a demo card: `accountId` |
| POST   | `/api/cards/{id}/block`       | Block a card |
| GET    | `/api/transactions?accountId=`| List transactions, newest first |
| POST   | `/api/transactions/authorize` | Authorize: `accountId`, optional `cardId`, `amount`, optional `merchant` |
| GET    | `/actuator/health`            | Health check (status only) |

Request bodies are validated (required fields, positive amounts with at most
2 decimal places, 3-letter currency codes, length limits). Invalid input gets a
`400` with `{"error": "..."}`; unknown IDs get a `404`. A card can only be used
on the account it was issued for — otherwise the authorization is declined.

## Health check

`GET /actuator/health` returns `{"status":"UP"}` (HTTP 200) when the app and
database are reachable, and `503` otherwise. It reports status only — no
connection details. Kubernetes-style probes are also available at
`/actuator/health/liveness` and `/actuator/health/readiness`. Render uses this
path (`healthCheckPath` in `render.yaml`), and the Docker image has a matching
`HEALTHCHECK`.

## CI and tests

`.github/workflows/ci.yml` runs on every push to `main` and on every PR:
backend build + tests (`mvn verify`), frontend build (`npm ci && npm run build`),
and a build of the exact root `Dockerfile` Render uses — so a broken build shows
up in GitHub Actions before you ever try deploying it.

Run the backend tests locally with `cd backend && mvn verify` (no database
needed — integration tests use in-memory H2). The suite covers:

- **Authorization logic** (`TransactionServiceTest`): approve on sufficient
  balance; decline on insufficient funds, frozen account, blocked card, or a
  card belonging to a different account.
- **Request validation** (`RequestValidationTest`): bad/missing fields,
  malformed JSON, invalid UUIDs.
- **End-to-end** (`PlatformIntegrationTest`): the app booting against H2, the
  health endpoint, and the create account → issue card → authorize flow.

## Deploying to Render (free tier)

1. Make sure the repo is on GitHub with the `Dockerfile` at the repo root
   (it already is).
2. In Render: **New → Blueprint**, connect the repo. Render reads
   `render.yaml` and proposes: `enterprise-db` (Postgres), `enterprise-backend`
   (Docker web service), `enterprise-frontend` (static site).
3. Click **Apply**. Database credentials are injected into the backend
   automatically from `enterprise-db` (`fromDatabase` in `render.yaml`) — no
   passwords are stored in the repo.
4. Once the backend is live, copy its URL and set it as `VITE_API_URL` on the
   frontend service (and the frontend's URL as `FRONTEND_ORIGIN` on the
   backend) if the auto-generated names in `render.yaml` don't match what
   Render assigns.

### Free tier limitations, honestly

- The web service and static site spin down after 15 minutes of inactivity;
  the next request cold-starts in ~30-60s.
- Render's free Postgres instance expires after 30 days unless upgraded to a
  paid plan — fine for a demo, not for anything long-lived.
- Everything runs as a single backend instance — no horizontal scaling, no
  message queue, no separate fraud/settlement processing.

## Before this touches real money or real cardholder data

Known gaps:
- No authentication or authorization on the API — every endpoint is open.
- Authorization isn't concurrency-safe yet (no row locking on balance updates).
- The schema is managed by Hibernate `ddl-auto: update`, not migrations.
- No PCI-DSS audit, no real KMS/HSM, no real OAuth2 provider, no real card
  network connectivity, no production alerting, no security review by humans
  who aren't the AI that wrote it.
