# Business Onboarding — Setup and Run

This project is a self-service business onboarding prototype. A company administrator can enter their contact and company information, then check the deliverability of their email address using Hunter.io or a mock verification provider. The application displays the verification outcome and keeps a history of previous submissions.

Users can choose synchronous-first verification for an immediate result when possible, or asynchronous verification to submit new requests while earlier ones are still processing. The Spring Boot backend stores verification jobs in PostgreSQL and processes unfinished work in the background, with retries and recovery after application restarts. The React frontend provides the registration form and status views.

## Requirements

### Option A — Run everything with Docker (recommended)

- **Docker Desktop** (Windows/macOS) or **Docker Engine** (Linux)
- **Docker Compose v2** (`docker compose`)

You **do not** need to install Java, Maven, Node.js, npm, or PostgreSQL separately for this option.

### Option B — Run the backend and frontend locally

- **Java 21** (JDK)
- **Maven 3.9+** (`mvn`)
- **Node.js 22+** and npm
- **PostgreSQL 16** (either installed locally or running through Docker Compose)

A Hunter.io API key is **not required** in the default mock mode. An API key is required only for live Hunter verification.

## 1. Run the complete project with Docker Compose

Open a terminal in the **project root** (the directory containing `compose.yml`).

**1. Create the environment file:**

macOS / Linux:

```bash
cp .env.example .env
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

**2. Build and start all services:**

```bash
docker compose up --build -d
```

**3. Open the application:**

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api/onboardings
- Backend health check: http://localhost:8080/actuator/health
- PostgreSQL (for DBeaver): `localhost:5432`

The default PostgreSQL **database, username, and password** are all `onboarding` (unless changed in `.env`).

**Useful commands:**

```bash
docker compose ps                  # Container status
docker compose logs -f backend     # Backend logs
docker compose logs -f frontend    # Frontend logs
docker compose up --build -d       # Rebuild after source changes
docker compose down                # Stop; keep database data
```

**Do not use `docker compose down -v` unless you want to delete the database volume and its data.**

## 2. Start the services individually (local development)

Open **three terminals** in the project root.

### Terminal 1 — PostgreSQL

```bash
docker compose up -d postgres
```

If you already run PostgreSQL locally, you can use that instead. The default backend expects a database named `onboarding` on `localhost:5432`, with username and password `onboarding`.

### Terminal 2 — Spring Boot backend

```bash
cd backend
mvn spring-boot:run
```

Backend: http://localhost:8080

By default, Spring connects to `jdbc:postgresql://localhost:5432/onboarding` and uses mock Hunter verification. If you changed the database credentials, set `DB_URL`, `DB_USER`, and `DB_PASSWORD` for the backend process.

### Terminal 3 — React frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:5173

Vite forwards frontend `/api` requests to the backend at `localhost:8080`. Start the backend before making verification requests.

To stop the local backend and frontend, press **Ctrl+C** in their terminals. To stop PostgreSQL:

```bash
docker compose stop postgres
```

## 3. Start Docker Compose services separately

If you prefer containers but want to start each part step by step, run these from the project root:

```bash
docker compose up -d postgres

docker compose up --build -d backend

docker compose up --build -d frontend
```

The Compose file includes health-based dependencies, so starting `backend` or `frontend` may also start their required services automatically.

## 4. Optional: Use the real Hunter.io API

The application starts in **mock mode** without credentials.

For the **Docker Compose** setup, edit `.env`:

```dotenv
HUNTER_MODE=real
HUNTER_API_KEY=your_hunter_api_key
PENDING_DELAY_SECONDS=60
```

Then recreate the backend container:

```bash
docker compose up -d --force-recreate backend
```

For a **locally running backend**, set `HUNTER_MODE=real`, `HUNTER_API_KEY`, and `PENDING_DELAY_SECONDS=60` in the environment of the Spring Boot process (or in your IDE run configuration) before starting it. The root `.env` file is read by Docker Compose, **not automatically by Spring Boot**.

Do not commit the `.env` file or your API key to source control.
