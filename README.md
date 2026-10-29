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

Setup:
1. Copy `src/main/resources/application-local.properties.example` to `application-local.properties` (this file is gitignored; never commit it).
2. Set the environment variables above (`$env:DB_PASSWORD = "..."` on PowerShell / `export ...` on Bash).
3. Prerequisites: PostgreSQL on 5432, Redis on 6379, Kafka on 9092.

## Running

```bash
./mvnw spring-boot:run            # backend on port 8080
cd frontend && npm install && npm run dev   # dashboard on port 5173 (proxies /api to 8080)
```

## API endpoints

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
