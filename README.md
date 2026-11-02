# api-monitor

Self-hosted uptime monitoring backend (Spring Boot 4, Java 21) with a planned web dashboard (React + Vite). Monitors watch HTTP endpoints on an interval; the Kafka → Redis → Postgres event flow records check results, computes statistics, opens/resolves incidents, and sends email alerts through EmailJS.

## Configuration & secrets

Real credentials are **never committed**. They are supplied as environment variables resolved by `${...}` placeholders in `application.properties` / `application-local.properties`.

| Env variable | Purpose |
|---|---|
| `DB_USERNAME` | PostgreSQL user |
| `DB_PASSWORD` | PostgreSQL password |
| `EMAILJS_SERVICE_ID` | EmailJS service id — **required for email delivery** |
| `EMAILJS_TEMPLATE_ID` | EmailJS notification template id — **required for email delivery** |
| `EMAILJS_PUBLIC_KEY` | EmailJS public key (sent as `user_id`) — **required for email delivery** |
| `EMAILJS_PRIVATE_KEY` | EmailJS private key (sent as `accessToken` for server-side calls) — **required for email delivery** |
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

Email delivery goes through **EmailJS** (https://www.emailjs.com), called from the backend's REST client — no Java Mail / SMTP integration exists in the project. The backend is the only place that holds EmailJS credentials: dashboard users only ever enter **recipient email addresses**. Nobody using the dashboard ever provides Gmail passwords, SMTP credentials, or any API key.

The dashboard manages recipients independently:

- `GET/PUT /api/notifications/settings` — the global email switch (`{ "emailEnabled": true|false }`, default on).
- `GET/POST/PATCH/DELETE /api/notifications/recipients` — recipient addresses stored in the `notification_recipient` table (PostgreSQL; auto-created by `ddl-auto=update`). Adding validates the address and rejects duplicates with `409`; individual recipients can be disabled without deleting them.
- `POST /api/notifications/test` — sends a test email to all enabled recipients and returns `{ "succeeded", "failed" }`; useful for verifying the EmailJS setup without waiting for an outage.

### EmailJS setup (one-time, done by whoever operates the backend)

Dashboard users configure nothing beyond entering their email address in the dashboard — all of the following is backend setup, and its credentials are only environment variables:

1. **Create an EmailJS account** at https://www.emailjs.com (free tier is enough to start; check its monthly email quota for larger setups).
2. **Connect an email service**: EmailJS dashboard → *Email Services* → *Add New Email Service*, choose Gmail (or any provider EmailJS supports), sign in with the sending mailbox, and copy the resulting **Service ID**. The sending Gmail account signs in with Google OAuth inside EmailJS — no app password is required anywhere in this project.
3. **Create the notification template**: *Email Templates* → *Add New Template* → *Create New Template*:
   - **Subject**: exactly `{{subject}}` (the backend fills the incident subject, e.g. `[api-monitor] DOWN: Billing API`).
   - **Content**: must contain `{{message}}` — that is where the incident details are placed. Suggested content:
     ```
     {{message}}
     ```
   - **To Email**: exactly `{{to_email}}` — the **dynamic recipient field**. Each request passes one recipient address here, so one template serves every dashboard-managed recipient.
   - Save the template and copy its **Template ID**.
4. **Enable API requests for non-browser applications**: EmailJS dashboard → *Account* → *Security* → turn on API access for non-browser applications. Server-side REST calls carry the **Private Key** as `accessToken`; without this setting the backend's requests are rejected. (The frontend never calls EmailJS itself — there is no browser SDK in the React app.)
5. **Set the backend environment variables** before starting the API (`$env:EMAILJS_SERVICE_ID = "..."` on PowerShell / `export EMAILJS_SERVICE_ID=...` on Bash; keep them out of any tracked file):
   - `EMAILJS_SERVICE_ID`, `EMAILJS_TEMPLATE_ID`, `EMAILJS_PUBLIC_KEY`, `EMAILJS_PRIVATE_KEY`.
   - If any is missing, requests are skipped with an `EmailJS is not configured` log entry instead of failing startup.

Behaviour:

- One email when an incident opens (monitor DOWN) and one when it recovers (RESOLVED) — monitor name, URL, incident id and both timestamps included. The incident-events Kafka stream fires exactly once per incident transition (IncidentService opens/resolves at most one incident per monitor), so consecutive failed checks of the same outage never trigger repeated emails; a new outage after recovery starts a new incident and a new email.
- Notifications are disabled globally by turning the switch off — recipients stay saved, incidents are still recorded.
- Reliability: delivery happens on the incident-events consumer thread (asynchronous to monitoring/incident processing), never lets a failure escape (malformed payloads are logged and dropped, delivery failures are logged and counted per recipient), HTTP connect is bounded to 10 s and each request to 15 s, and EmailJS's one-request-per-second limit is honoured by strictly spaced sequential sends with a single retry on `429`. An EmailJS outage cannot stall Kafka processing, monitoring, or incident creation, and email failures don't change what the dashboard shows.

## Running

```bash
./mvnw spring-boot:run            # backend on port 8080
cd frontend && npm install && npm run dev   # dashboard on port 5173 (proxies /api to 8080)
```

## Render deployment (Free web service + Aiven)

The backend is configured for Render's Docker runtime with Java 21. `render.yaml` defines the Free web service, uses Render's injected `PORT`, and checks `GET /api/auth/setup-status`. That public route returns the registration bootstrap status and is permitted by the existing JWT security configuration. Free services may sleep while idle, so the first request after inactivity can take time.

1. Create Aiven PostgreSQL, Kafka, and Valkey services, and allow network access from Render (or use the Aiven service's public endpoints). Copy each service's host, port, database/user, and credentials from its Aiven connection details.
2. In Render, create a Blueprint from this repository and select the `apivigil-api` web service. Enter each `sync: false` value in the service's Environment settings. Keep passwords and keys in Render's secret environment fields; do not add them to Git.
3. Set `CORS_ALLOWED_ORIGINS` to the exact future Cloudflare Pages origin, for example `https://your-project.pages.dev` (no trailing slash). Add any production custom domain as a comma-separated origin when known.
4. Generate a long random `JWT_SECRET` (at least 32 characters). Set a strong `ADMIN_PASSWORD` if you want the initial admin provisioned at startup; otherwise, the first registered account becomes the administrator.
5. Set the four EmailJS values if notifications should send email. If they are empty, monitoring and incident processing continue and email sends are logged as unconfigured.
6. Deploy from Render after all required service credentials are set. Keep the existing `compose.yaml` for local Postgres, Redis and Kafka development.

The `production` Spring profile requires TLS for PostgreSQL (`sslmode=require`), Valkey (`spring.data.redis.ssl.enabled=true`) and Kafka (`SASL_SSL` with `SCRAM-SHA-256`). For Aiven Kafka, use its SCRAM username/password, TLS bootstrap endpoint, and CA certificate PEM. The Kafka PEM truststore is applied to producer, consumer and admin clients. Certificate chain and hostname verification remain enabled. Do not paste an `ssl://` or `kafka://` URL into `KAFKA_BOOTSTRAP_SERVERS`; use `host:port`.

### Render environment variables

Set every required variable below in Render. Database, Kafka and Valkey connection values are available in each Aiven service's connection information. EmailJS values are optional if alert email is not configured.

| Variable | Required | Purpose |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Set by `render.yaml` | Selects production cloud TLS/auth configuration. |
| `PORT` | Render supplied | HTTP listen port; passed to Spring Boot. Do not set manually. |
| `DB_HOST` | Yes | Aiven PostgreSQL hostname. |
| `DB_PORT` | Yes | Aiven PostgreSQL TLS port. |
| `DB_NAME` | Yes | PostgreSQL database name. |
| `DB_USERNAME` | Yes | PostgreSQL user. |
| `DB_PASSWORD` | Yes | PostgreSQL password. |
| `KAFKA_BOOTSTRAP_SERVERS` | Yes | Aiven Kafka TLS bootstrap endpoint as `host:port`. |
| `KAFKA_USERNAME` | Yes | Kafka SASL/SCRAM username. |
| `KAFKA_PASSWORD` | Yes | Kafka SASL/SCRAM password. |
| `KAFKA_CA_CERT` | Yes | Complete Aiven Kafka CA certificate in PEM format (include the `BEGIN CERTIFICATE` / `END CERTIFICATE` lines). |
| `VALKEY_HOST` | Yes | Aiven Valkey hostname. |
| `VALKEY_PORT` | Yes | Aiven Valkey TLS port. |
| `VALKEY_USERNAME` | Yes | Valkey ACL username (usually `default`). |
| `VALKEY_PASSWORD` | Yes | Valkey password. |
| `JWT_SECRET` | Yes | HS256 signing secret, at least 32 characters. |
| `JWT_EXPIRATION` | No | JWT lifetime as ISO-8601 duration; default `PT1H`. |
| `ADMIN_USERNAME` | No | Initial administrator username; default `admin`. |
| `ADMIN_PASSWORD` | Recommended | Initial admin password. Leave empty to make the first registered account ADMIN. |
| `CORS_ALLOWED_ORIGINS` | Yes | Comma-separated exact browser origins, including the future Cloudflare Pages URL. |
| `EMAILJS_SERVICE_ID` | Optional | EmailJS sending service identifier. |
| `EMAILJS_TEMPLATE_ID` | Optional | EmailJS notification template identifier. |
| `EMAILJS_PUBLIC_KEY` | Optional | EmailJS public key. |
| `EMAILJS_PRIVATE_KEY` | Optional | EmailJS server-side private key. |

The container uses a Java 21 runtime and bounds the JVM heap to 50% of detected memory, caps metaspace and code cache, and selects Serial GC to leave room for Spring, Kafka, Redis and native memory within Render's 512 MB / 0.1 CPU Free instance. Flyway remains enabled for the existing ownership migration, and Hibernate retains the current schema-update behavior so an empty database can be initialized with the current migration set.

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
