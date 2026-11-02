# ApiVigil

**API Monitoring & Incident Management Platform**

ApiVigil is a full-stack monitoring platform that tracks API availability, measures response times, detects failures, and manages incidents through a centralized dashboard.

## Features

- **API Monitoring** – Schedule HTTP health checks with configurable intervals and timeouts.
- **Incident Management** – Automatically detect failures and track service recovery.
- **Analytics** – Monitor uptime, response times, and historical check results.
- **Authentication** – JWT-based authentication with role management.
- **Account Isolation** – Separate monitors, incidents, and notification settings for each user.
- **Email Notifications** – Receive alerts for monitoring incidents and recovery events.

## Tech Stack

| Category | Technologies |
|---|---|
| Frontend | React, JavaScript, Vite, Tailwind CSS |
| Backend | Java 21, Spring Boot |
| Database | PostgreSQL |
| Messaging | Apache Kafka |
| Caching | Redis / Valkey |
| Security | Spring Security, JWT |
| Notifications | EmailJS |
| Deployment | Vercel, Render, Aiven, Docker |

## Architecture

ApiVigil uses an asynchronous monitoring pipeline:

```text
Scheduler
    ↓
Apache Kafka
    ↓
Monitoring Workers
    ↓
HTTP Health Checks
    ↓
Kafka → Redis/Valkey → PostgreSQL
    ↓
Incident Detection & Notifications
```

## Local Development

**Prerequisites:** Java 21, Node.js, npm, Docker.

Clone the repository:

```bash
git clone https://github.com/Zorark10/api-monitor-java.git
cd api-monitor-java
```

Start infrastructure:

```bash
docker compose up -d
```

Start the backend:

```bash
.\mvnw.cmd spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm install
npm run dev
```

Configure the required environment variables before starting the application. See the application configuration files for details.

## Testing

Backend:

```bash
.\mvnw.cmd test
```

Frontend:

```bash
npm run lint
npm run build
```

---

**ApiVigil** · Monitor your APIs. Detect failures. Stay informed.
