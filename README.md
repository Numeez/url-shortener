# URL Shortener

A production-style URL shortener API built with Spring Boot, designed as a portfolio project to demonstrate backend engineering practices beyond CRUD: authentication, caching, rate limiting, analytics, observability, and a real integration-test suite backed by Testcontainers.

## Features

- **JWT authentication** — register/login, stateless auth via `Authorization: Bearer <token>`, BCrypt password hashing
- **Short URL management** — create (random or custom alias), list, get, update, delete, all scoped to the owning user
- **Redirects** — `GET /r/{code}` issues a `302` and asynchronously records a click event
- **Click analytics** — per-URL totals, daily breakdown, and top referrers
- **Redis-backed caching** — redirect lookups are cache-first with write-through invalidation on update/delete
- **Rate limiting** — token-bucket limits (via Bucket4j) on auth, create, and redirect traffic, configurable per category
- **QR codes** — PNG QR code generation for any short URL you own
- **Admin stats** — aggregate usage stats and top-performing URLs, gated behind `ROLE_ADMIN`
- **OpenAPI / Swagger UI** — interactive API docs at `/swagger-ui.html`
- **Actuator + Prometheus** — health, metrics, and a `/actuator/prometheus` scrape endpoint (admin-gated)
- **Flyway migrations** — versioned schema, applied automatically on startup
- **CI** — GitHub Actions runs the full unit + integration test suite (`mvn verify`) on every push/PR to `main`

## Tech Stack

| Layer | Choice |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 4.1 (Spring Framework 7) |
| Persistence | PostgreSQL, Spring Data JPA / Hibernate 7 |
| Migrations | Flyway |
| Cache | Redis (Spring Data Redis) |
| Auth | Spring Security + JWT (jjwt) |
| Rate limiting | Bucket4j |
| QR codes | ZXing |
| API docs | springdoc-openapi |
| Observability | Spring Boot Actuator + Micrometer/Prometheus |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers (Postgres + Redis) |
| CI | GitHub Actions |

## Getting Started

### Prerequisites

- Java 21
- Docker (for Postgres/Redis via Docker Compose, and for Testcontainers-backed integration tests)

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` as needed. At minimum, set a real `JWT_SECRET` (a base64-encoded 256-bit key, e.g. `openssl rand -base64 32`) for anything beyond local experimentation — the app falls back to a built-in default secret if unset, which is fine for local dev only.

### 2. Start Postgres + Redis

```bash
docker compose up -d
```

This starts Postgres on `localhost:5434` (not 5432 — see note below) and Redis on `localhost:6379`.

> **Why port 5434?** The default Postgres port is remapped to avoid clashing with a locally installed Postgres. Override via `POSTGRES_PORT` in `.env` if needed.

### 3. Run the app

```bash
./mvnw spring-boot:run
```

Flyway applies all pending migrations automatically on startup. The API is now available at `http://localhost:8080`.

### 4. Explore the API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI spec: `http://localhost:8080/v3/api-docs`

### Optional: bootstrap an admin user

Set `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` in `.env` before starting the app. On startup, a matching user is created (or promoted to `ADMIN` if it already exists) — useful for reaching the `/api/admin/**` and `/actuator/**` endpoints without manually editing the database.

## Running Tests

```bash
./mvnw test      # fast unit tests only (Mockito-based, no Docker required)
./mvnw verify     # unit + integration tests (spins up Testcontainers Postgres/Redis — Docker must be running)
```

`mvn verify` is what CI runs, and is the command that determines an actually-green build — `mvn test` alone skips all `*IT` integration test classes (Maven Surefire vs. Failsafe conventions).

If the dev Docker Compose stack is already running, integration tests still work (Testcontainers spins up its own independent, ephemeral containers) but on memory-constrained machines you may see better performance running `docker compose stop` first.

## API Overview

All endpoints are prefixed `/api` except the redirect endpoint. Authenticated endpoints expect `Authorization: Bearer <jwt>`.

### Auth — `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | — | Create an account, returns a JWT |
| POST | `/login` | — | Authenticate, returns a JWT |
| GET | `/me` | required | Current user's id, email, role |

### Short URLs — `/api/urls`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/` | required | Create a short URL (random or custom alias, optional expiry) |
| GET | `/` | required | List the caller's short URLs (paginated) |
| GET | `/{id}` | required (owner) | Get one short URL |
| PATCH | `/{id}` | required (owner) | Update original URL / active flag / expiry |
| DELETE | `/{id}` | required (owner) | Delete a short URL |
| GET | `/{id}/analytics` | required (owner) | Click totals, daily breakdown, top referrers |
| GET | `/{id}/qrcode` | required (owner) | PNG QR code (`?size=100-1000`, default 300) |

**Create example:**

```bash
curl -X POST http://localhost:8080/api/urls \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://example.com/some/very/long/path", "customAlias": "my-link"}'
```

### Redirect

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/r/{shortCode}` | — | `302` redirect to the original URL; records a click event asynchronously |

Returns `404` for an unknown code, `410 Gone` for a deactivated or expired URL.

### Admin — `/api/admin` (requires `ROLE_ADMIN`)

| Method | Path | Description |
|---|---|---|
| GET | `/stats` | Aggregate stats: total users, URLs, clicks |
| GET | `/urls/top` | Top URLs by click count (`?limit=1-100`, default 10) |

### Observability

| Path | Auth | Description |
|---|---|---|
| `/actuator/health/**`, `/actuator/info` | — | Public health/info |
| `/actuator/**` | `ROLE_ADMIN` | Full actuator surface, including `/actuator/prometheus` |

## Configuration Reference

All configuration is environment-variable driven (see `.env.example`); sensible defaults exist for local development.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_PORT` | `url_shortener` / `url_shortener` / `change-me` / `5434` | Docker Compose Postgres |
| `REDIS_PORT` | `6379` | Docker Compose Redis |
| `JWT_SECRET` | *(dev default, insecure)* | Base64 256-bit signing key for JWTs |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | JWT token lifetime |
| `APP_BASE_URL` | `http://localhost:8080` | Base URL used when building short URLs |
| `SHORT_URL_CACHE_TTL_SECONDS` | `3600` | Redis TTL for cached redirect lookups |
| `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` | *(unset)* | Auto-provision an admin user on startup |
| `RATE_LIMIT_CREATE_*` | 10 tokens / 60s | Rate limit for `POST /api/urls` |
| `RATE_LIMIT_REDIRECT_*` | 60 tokens / 60s | Rate limit for `GET /r/{code}` |
| `RATE_LIMIT_AUTH_*` | 5 tokens / 60s | Rate limit for register/login |

## Project Structure

```
src/main/java/com/example/url_shortener/
├── admin/          # Admin stats endpoint + service
├── auth/           # Register/login/me, JWT issuance
├── common/         # Shared exceptions + global exception handler
├── config/         # Async config, OpenAPI config
├── ratelimit/      # Bucket4j token-bucket filter
├── security/       # JWT filter, Spring Security config, user details
├── shorturl/       # Core domain: create/redirect/analytics/QR/caching
└── user/           # User entity + admin bootstrap runner
```

Tests mirror this package structure under `src/test/java`, split into fast Mockito unit tests (`*Test.java`) and Testcontainers-backed integration tests (`*IT.java`) that exercise real Postgres, Redis, and the full Spring context via MockMvc.

## Roadmap

- [ ] Remaining test coverage: click analytics, QR codes, rate limiting, admin
- [ ] Dockerize the Spring Boot app itself
- [ ] Full `docker-compose` stack (app + Postgres + Redis in one command)
