# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Personal/learning project: the primary user is the developer building it, learning Spring Boot and monitoring architecture. A future web dashboard will be consumed by the same user. No other audiences confirmed.

## Product Purpose

An uptime/API monitoring backend. It tracks HTTP endpoints ("monitors") by scheduled health checks, records per-check results (response times, success/failure), computes status (UP/DOWN) and uptime statistics, opens and resolves incidents on failure, and sends email alerts. Success is a reliable monitor-and-alert loop plus a planned dashboard for reviewing status, history, and statistics.

## Positioning

Self-hosted personal uptime monitor built as a learning exercise in a distributed Spring Boot architecture (Kafka event flow, Redis-based scheduling, email alerting). No positioning claim beyond that; not a competing SaaS.

## Operating Context

- Runs locally: PostgreSQL (localhost:5432, `api_monitor` DB), Redis (localhost:6379), Kafka (localhost:9092), Gmail SMTP for alerts.
- Config via environment variables (DB credentials, mail credentials); `local` profile active by default.
- Checks are scheduled via Redis next-check timestamps and executed by `HealthCheckService`/`MonitorScheduler`.
- Check results flow through Kafka (producer → consumer) before persistence; incident events likewise (`IncidentEventProducer`/`Consumer`).

## Capabilities and Constraints

- CRUD for monitors: REST endpoints under `/api/monitors` (`Monitor`, fields: name, url, httpMethod, interval, timeout, enabled, createdAt). Validation on name/url, positive interval/timeout, method enum.
- Second resource: incidents (`Incident`, `IncidentStatus`) tracked with startedAt/resolvedAt.
- Paginated check results per monitor (`/api/monitors/{id}/results`, page size 20 default), statistics (`/statistics`: total/successful/failed checks, uptimePercentage, average/min/max response time), and current status (`/status` → UP/DOWN).
- Terminology: "monitor", "check"/"result", "incident", UP/DOWN status.
- Undecided: incident-related REST endpoints not yet exposed (`IncidentService` exists); dashboard scope and tech stack not chosen.
- No authentication exists yet on the API.

## Brand Commitments

None. Artifact name "api-monitor" (`com.project.api_monitor`) is the working name.

## Evidence on Hand

Source code only: `src/main/java/com/project/api_monitor` (controller, service, model, repository, exception layers), `pom.xml`, `application.properties`, one empty application test. No screenshots, docs, fixtures, or marketing content. Future work must not fabricate testimonials, customers, or deployment claims.

## Product Principles

1. Correctness of the monitoring loop first: checks, results, incidents, and alerts must be truthful before any UI polish.
2. Learn-the-architecture UX: prefer designs that make the event flow (Kafka, Redis scheduling) observable and understandable.
3. Simple, honest data presentation: status, uptime percentage, and response-time statistics shown exactly as computed, with no fabricated certainty.
4. Local-first operation: design must assume small scale, local infrastructure, and a single user.

## Accessibility & Inclusion

No product-specific requirement established yet; standard web accessibility practices apply to the future dashboard.
