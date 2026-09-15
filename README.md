# Enterprise Platform — Render-friendly consolidated build

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

Same disclaimers as before apply, more so: this is a learning/demo scaffold,
not anything close to PCI-DSS scope.

## Stack

- Backend: Java 17, Spring Boot 3.3, Spring Data JPA, Postgres
- Frontend: React 18 + Vite
- Database: Postgres (Render's free managed instance)

## Local development

One-command version (Postgres + backend + frontend):
```
docker compose up --build
# backend:  http://localhost:8080
# frontend: http://localhost:5173
```

Or run pieces individually:
```
docker run --name enterprise-db -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=enterprise -p 5432:5432 -d postgres:16
cd backend && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173
```

## CI

`.github/workflows/ci.yml` runs on every push/PR: backend build + unit tests
(`mvn verify`), frontend build (`npm run build`), and a build of the exact
root `Dockerfile` Render uses — so a broken build shows up in GitHub Actions
before you ever try deploying it.

Unit tests cover the authorization decision logic (`TransactionServiceTest`):
approve on sufficient balance, decline on insufficient funds, decline on
frozen account, decline on blocked card.

## Deploying to Render (free tier)

1. Push this folder's contents to your GitHub repo (root of the repo — the
   `Dockerfile` needs to be at the repo root, not nested).
2. In Render: **New → Blueprint**, connect the repo. Render reads
   `render.yaml` and proposes: `enterprise-db` (Postgres), `enterprise-backend`
   (Docker web service), `enterprise-frontend` (static site).
3. Click **Apply**.
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

Same gaps as the original repo, unchanged:
- No PCI-DSS audit, no real KMS/HSM, no real OAuth2 provider, no real card
  network connectivity, no production alerting, no security review by humans
  who aren't the AI that wrote it.
