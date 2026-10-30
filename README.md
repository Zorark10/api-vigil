# api-monitor

Self-hosted uptime monitoring backend (Spring Boot 4, Java 21) with a planned web dashboard (React + Vite). Monitors watch HTTP endpoints on an interval; the Kafka → Redis → Postgres event flow records check results, computes statistics, opens/resolves incidents, and sends Gmail email alerts.

## Configuration & secrets

Real credentials are **never committed**. They are supplied as environment variables resolved by `${...}` placeholders in `application.properties` / `application-local.properties`.

| Env variable | Purpose |
|---|---|
| `DB_USERNAME` | PostgreSQL user |
| `DB_PASSWORD` | PostgreSQL password |
| `MAIL_USERNAME` | Gmail address for SMTP alerts |
| `MAIL_PASSWORD` | Gmail **App Password** (2FA required) |
| `JWT_SECRET` | HMAC secret for JWT signing — **required**, at least 32 characters (generate: `openssl rand -base64 48`) |
| `JWT_EXPIRATION` | Token lifetime (ISO-8601 duration, default `PT1H`) |
| `ADMIN_USERNAME` | Initial administrator username (default `admin`) |
| `ADMIN_PASSWORD` | Initial administrator password — required on first startup while the user table is empty |
| `CORS_ALLOWED_ORIGINS` | Browser origins allowed to call the API (default `http://localhost:5173`) |

Setup:
1. Copy `src/main/resources/application-local.properties.example` to `application-local.properties` (this file is gitignored; never commit it).
2. Set the environment variables above (`$env:DB_PASSWORD = "..."` on PowerShell / `export ...` on Bash).
3. Prerequisites: PostgreSQL on 5432, Redis on 6379, Kafka on 9092.

## Authentication

All API endpoints except `POST /api/auth/login` require a valid Bearer JWT. The dashboard uses the same session flow.

- `POST /api/auth/login` with `{ "username": ..., "password": ... }` returns `{ "token", "tokenType": "Bearer" }`. Bad credentials return `401` with the standard error body.
- `GET /api/auth/me` returns the authenticated user (`username`, `role`, `createdAt`).
- On the **first startup with an empty user table**, the backend provisions the initial administrator from `ADMIN_USERNAME` / `ADMIN_PASSWORD`; the password is stored only as a BCrypt hash. Existing users are never overwritten or recreated. Startup fails fast if `ADMIN_PASSWORD` is unset on a fresh database — this is intentional so no account can be silently created without a configured password.
- Tokens are stateless (HS256, 1 hour by default). To rotate the secret, change `JWT_SECRET` and restart; all existing tokens become invalid. Never reuse a secret that has ever been committed to git — if one ever was, you must also change any credentials that appeared alongside it.

## Email notifications

Incident emails are sent over the existing Gmail SMTP setup (`spring-boot-starter-mail`; `MAIL_USERNAME`, `MAIL_PASSWORD` stay in environment variables and are never exposed to the frontend). The dashboard manages recipients independently:

- `GET/PUT /api/notifications/settings` — the global email switch (`{ "emailEnabled": true|false }`, default on).
- `GET/POST/PATCH/DELETE /api/notifications/recipients` — recipient addresses stored in the `notification_recipient` table (PostgreSQL; auto-created by `ddl-auto=update`). Adding validates the address and rejects duplicates with `409`; individual recipients can be disabled without deleting them.
- `POST /api/notifications/test` — sends a test email to all enabled recipients and returns `{ "succeeded", "failed" }`; useful for verifying the SMTP login without waiting for an outage.

Behaviour:

- One email when an incident opens (monitor DOWN) and one when it recovers (RESOLVED) — monitor name, URL, incident id and both timestamps included. The incident-events Kafka stream fires exactly once per incident transition (IncidentService opens/resolves at most one incident per monitor), so consecutive failed checks of the same outage never trigger repeated emails; a new outage after recovery starts a new incident and a new email.
- Notifications are disabled globally by turning the switch off — recipients stay saved, incidents are still recorded.
- Reliability: the incident-events consumer never lets a failure escape (malformed payloads are logged and dropped, delivery failures are logged and counted per recipient), and SMTP connect/read/write are bounded to 10 s each, so an email outage cannot stall Kafka processing, monitoring, or incident creation. Email failures don't change what the dashboard shows.

## Running

```bash
./mvnw spring-boot:run            # backend on port 8080
cd frontend && npm install && npm run dev   # dashboard on port 5173 (proxies /api to 8080)
```

## API endpoints

### Auth
| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | public; returns JWT |
| GET | `/api/auth/me` | authenticated user from Bearer token |

Every other endpoint below requires an `Authorization: Bearer <token>` header.

### Monitors
| Method | Path | Notes |
|---|---|---|
| POST | `/api/monitors` | create (validated) |
| GET | `/api/monitors` | list all |
| GET | `/api/monitors/{id}` | 404 if unknown |
| PATCH | `/api/monitors/{id}` | update; 404 if unknown |
| DELETE | `/api/monitors/{id}` | 404 if unknown |
| GET | `/api/monitors/{id}/results` | paginated check results (default page size 20) |
| GET | `/api/monitors/{id}/statistics` | aggregate stats |
| GET | `/api/monitors/{id}/status` | current cached status |

### Incidents
| Method | Path | Notes |
|---|---|---|
| GET | `/api/monitors/{id}/incidents` | paginated incident history, newest first |
| GET | `/api/incidents?status=OPEN\|RESOLVED` | paginated; filter optional |

### Email notifications
| Method | Path | Notes |
|---|---|---|
| GET | `/api/notifications/settings` | global on/off switch |
| PUT | `/api/notifications/settings` | update switch |
| GET | `/api/notifications/recipients` | list recipients |
| POST | `/api/notifications/recipients` | add (validated, 409 on duplicate) |
| PATCH | `/api/notifications/recipients/{id}` | enable/disable, 404 if unknown |
| DELETE | `/api/notifications/recipients/{id}` | remove, 404 if unknown |
| POST | `/api/notifications/test` | test email to enabled recipients |

## JSON contract

- Check dates are serialized as `yyyy-MM-dd HH:mm:ss.SSS`; incident dates as `yyyy-MM-dd HH:mm:ss`.
- Paginated responses use standard Spring Data `Page` shape: `content`, `totalElements`, `totalPages`, `number`, `size`.
- Errors use `{ "status": int, "message": string, "timestamp": ... }` (`@RestControllerAdvice`).

### Contract change: `averageResponetime` → `averageResponseTime`

The statistics response previously exposed a typo in its JSON field name. It was renamed
before the dashboard was built; **any existing consumer of `/api/monitors/{id}/statistics`
must read `averageResponseTime` instead of `averageResponetime`.**

```json
GET /api/monitors/{id}/statistics
{
  "monitorId": 1,
  "totalChecks": 42,
  "successfulChecks": 40,
  "failedChecks": 2,
  "uptimePercentage": 95.24,
  "averageResponseTime": 210.5,
  "minResponseTime": 88,
  "maxResponseTime": 903
}
```

Consumers of the old field name: none known (the dashboard is built after this change and reads the new name).
