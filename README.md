# Business onboarding — Spring Boot + React starter

A focused, **runnable starter** for a backend take-home assignment: business admin registration, durable asynchronous email verification, a configurable Hunter.io adapter, and a small React UI.

**Important:** Hunter's Email Verifier checks *deliverability*, not email ownership, identity, business registration, or authorization to act on behalf of a company. This project **must not** use `VERIFIED` to grant privileged account access.

## Requirements

- Java **21**, Maven **3.9+**, Node.js **22+**, npm
- Docker with `docker compose` (for PostgreSQL)

## Quick start (3 terminals)

From the repository root:

**Terminal 1 — database**

```bash
docker compose up -d
```

**Terminal 2 — backend**

```bash
cd backend
mvn spring-boot:run
```

**Terminal 3 — frontend**

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. Spring listens at `localhost:8080`; Vite proxies `/api` to Spring. No Hunter credentials are necessary in the default mock mode.

Quick check: `http://localhost:8080/actuator/health`.

## Repository layout

```text
onboarding-starter/
├── backend/                        # Spring Boot 4 + Java 21 + Maven
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/example/onboarding/
│       │       ├── onboarding/         # resource/, data/, service/, repository/
│       │       ├── verification/       # resource/, data/, service/, repository/
│       │       └── exception/          # API exception handling
│       └── resources/db/migration/ # Flyway migrations
├── frontend/                       # React + TypeScript + Vite
├── compose.yml                     # PostgreSQL 16
└── README.md
```

## API

### Create onboarding

```bash
curl -i http://localhost:8080/api/onboardings \
  -H 'Content-Type: application/json' \
  -d '{"adminName":"Alex Smith","adminEmail":"delay@acme.com","companyName":"Acme Ltd","companyDomain":"acme.com"}'
```

Returns `202 Accepted`, with an ID and `PENDING` status. The response means the **job is persisted**, not that verification succeeded.

### Poll status

```bash
curl http://localhost:8080/api/onboardings/<ID>
```

### Correct contact and start new verification revision

```bash
curl -X PATCH http://localhost:8080/api/onboardings/<ID>/contact \
  -H 'Content-Type: application/json' \
  -d '{"adminEmail":"success@acme.com"}'
```

## Deterministic mock scenarios

Use any of these emails in the registration form (no actual emails are sent):

| Email | Simulated integration sequence | Final onboarding status |
|---|---|---|
| `success@acme.com` | `200 valid` | `VERIFIED` |
| `delay@acme.com` | `202`, `202`, `200 valid` | `VERIFIED` |
| `flaky@acme.com` | temporary failure, retry, then success | `VERIFIED` |
| `invalid@acme.com` | `200 invalid` | `ACTION_REQUIRED` |
| `risky@acme.com` | `200 accept_all` | `REVIEW_REQUIRED` |
| `quota@acme.com` | `429` equivalent | `TEMPORARILY_UNAVAILABLE` |

The number after an email's first attempt is stored in PostgreSQL, so `delay` and `flaky` behavior survives an app restart. Stop and restart Spring during an in-progress check to demonstrate recovery.

## Async design and failure recovery

1. The REST API validates the input, inserts `onboardings` and `verification_jobs` in **one database transaction**, and responds with `202`.
2. A Spring `@Scheduled` worker finds due jobs and **atomically claims** each one with a lease and claim token; no HTTP request is made inside a DB transaction.
3. The `EmailVerifier` interface allows mock or real Hunter implementations. An HTTP `202` means **provider still processing**, not failure. Timeout/`403`/`5xx` are bounded retries with backoff.
4. The job state and `next_attempt_at` are persisted; an application crash causes a claimed job to be retried after its **60-second lease** expires.
5. A late result for a corrected email is ignored using the onboarding `revision`, and an expired worker claim cannot overwrite a newer claim.
6. The React page polls `GET /api/onboardings/{id}` every 2.5 seconds and retains the ID in `localStorage` to support a page refresh.

**Important limitation:** This is **at-least-once processing**, not exactly-once execution. Provider calls can be repeated after a crash. Hunter's verifier specifically allows polling the same email after `202`. The demo doesn't have a global rate limiter, tracing, authenticated tenant boundaries, or an outbox/broker.

### States

`PENDING → VERIFYING → VERIFIED | ACTION_REQUIRED | REVIEW_REQUIRED | TEMPORARILY_UNAVAILABLE`

`VERIFYING` can remain active across multiple scheduled attempts. A correction starts a new revision in `PENDING`. `VERIFIED` is only *email deliverability* verification.

## Using the real Hunter adapter (optional)

Set environment variables for the backend before starting it:

```bash
export HUNTER_MODE=real
export HUNTER_API_KEY='your-secret-key'
export PENDING_DELAY_SECONDS=60
cd backend && mvn spring-boot:run
```

Only backend sees the API key. The Hunter API requires it as a query parameter; don't log full request URLs or commit secrets. The provided `.env.example` is a reference: **Spring does not automatically load this root `.env` file.**

Hunter v2 Email Verifier behavior handled by the adapter:

- `200`: `data.status` (`valid`, `invalid`, `accept_all`, `webmail`, `disposable`, `unknown`). All except `valid`/`invalid` go to further review.
- `202`: pending; poll the same endpoint later (about 60 seconds in real mode).
- `222`, `403`, `5xx`, and timeouts: bounded retry.
- `429` (usage quota), `401` (authentication), and `451` (privacy restriction): don't repeatedly retry.

References:

- https://hunter.io/api-documentation.md
- https://help.hunter.io/en/articles/12633706-how-to-verify-emails-via-api-with-code-examples

## Testing

```bash
cd backend
mvn test
```

The included unit tests cover the deterministic mock scenarios. **Add integration tests** with Testcontainers/PostgreSQL and WireMock for the worker, crash recovery, stale revision, and real adapter HTTP mapping before treating this as a finished submission.

## Work deliberately left for the take-home

- [ ] Add `Idempotency-Key` to POST and robust duplicate submission handling (a client retry currently creates a new onboarding).
- [ ] Test concurrent claims, lease expiry, and a stale email result using database integration tests.
- [ ] Add a retry path for `TEMPORARILY_UNAVAILABLE` after the quota/configuration issue is fixed, and alert operators.
- [ ] Add a clear business-domain/email mismatch policy without confusing it with identity verification.
- [ ] Add tenant-aware authentication, authorization, and rate limits. This starter intentionally exposes anonymous demo endpoints.
- [ ] Improve frontend validation, loading state transitions, and error handling.
- [ ] Add metrics, structured logging, and configurable provider delays.

## Tradeoffs for the interview

- **Modular monolith over microservices:** faster to deliver; integration boundaries still explicit.
- **Database worker over Kafka/RabbitMQ:** transactions, leases, and restart durability without extra infrastructure. For high throughput, a real queue and centralized throttling would be better.
- **Polling over WebSockets/SSE:** enough for onboarding volumes; trivial to debug and to resume after navigation.
- **Deterministic mock over live-only verification:** shows hard failure scenarios during the interview and avoids API credits. The real adapter is provided but does **not** replace contract tests.

### AI usage note to personalize

State which assistance you really used. An important example to verify manually is the common recommendation to put the provider call in an `@Async` method and return HTTP `202`: **in-memory tasks can be lost on restart**, so this starter instead commits a database job before responding. Reproduce a restart during delayed verification and validate what the system actually does; do not describe a behavior you haven't tested.
