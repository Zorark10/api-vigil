# IMPLEMENTATION_HANDOFF — Final Security & Database-Migration Verification

## Status: COMPLETE (all items verified; two real bugs found and fixed)

Everything below was performed on the working tree (nothing committed).

## 1) Real-PostgreSQL verification (not H2)

Environment: the local PostgreSQL 18 server on `localhost:5432` (the server the
application actually reaches; Docker Desktop/daemon died mid-session and was
not needed). Two disposable databases (`smoke_fresh`, `smoke_migrate`) were
created, verified, and dropped afterwards. The real `api_monitor` database was
never touched.

### 1a. Fresh installation (`smoke_fresh`, empty DB)
- Booted the real Spring Boot jar (`target/api-monitor-0.0.1-SNAPSHOT.jar`)
  against it.
- Flyway: `flyway_schema_history` records **V1 "user ownership" = SUCCESS as a
  no-op** (`to_regclass` guard short-circuits; no baseline row on an empty DB).
- Hibernate `ddl-auto=update` then created the complete schema: `app_user`
  gets `email` (+ unique), `monitor`/`notification_recipient`/`notification_settings`
  get `owner_id` (+ FKs). Verified via catalog queries.
- HTTP behavior on the fresh DB: provisioned admin logs in with
  `ADMIN_PASSWORD` (role ADMIN); next registration → **HTTP 201, role USER**;
  a registration body containing `"role":"ADMIN"` still produced role USER
  (cannot escalate); case-insensitive duplicate email → 409.

### 1b. Migration of an existing deployment (`smoke_migrate`)
- The DB was seeded with a faithful **pre-migration (legacy) schema**: no
  `email`, no `owner_id` anywhere, ONE global `notification_settings` row,
  recipients under a global `unique(email)` constraint, identity-backed id
  columns with correct sequence positions.
- Seed data: 2 users (ADMIN `admin` id 1 + USER `helper` id 2), 3 monitors,
  5 monitor_result rows, 2 incidents (OPEN + RESOLVED), 3 notification
  recipients, 1 settings row.
- Booted the real jar → Flyway **baselined at version 0** and applied
  **V1 = SUCCESS**.
- Verified after migration:
  - **All row counts preserved** (2 users, 3 monitors, 5 results, 2
    incidents, 3 recipients, 1 settings). Zero orphans.
  - All 3 monitors and all 3 recipients reassigned to the ADMIN (id 1);
    global settings row became that admin's per-owner row;
    `email_enabled=true` preserved.
  - `app_user.email` backfilled: `admin@localhost.local`, `helper@localhost.local`.
  - Constraints present: `fk_monitor_owner`, `fk_recipient_owner`,
    `fk_settings_owner` (all `convalidated=true`), `uk_app_user_email`,
    `uk_recipient_owner_email`, `uk_settings_owner`; legacy global
    `unique(email)` on recipients **dropped**.
  - Indexes created: `idx_monitor_owner`, `idx_monitor_result_monitor_id`,
    `idx_incident_monitor_id`, `idx_recipient_owner`.
- HTTP behavior on the migrated DB: admin (migrated account) saw all 3 legacy
  monitors; new registrations got USER; cross-account GET/PATCH of another
  account's monitor → **404** (no data leak); a rider account created its own
  monitor and saw only it; deleting it left the admin's legacy data intact.

## 2) Bugs found by this verification (both fixed)

### 2a. CRITICAL — Flyway never ran in the packaged app
`pom.xml` used raw `org.flywaydb:flyway-core`. In Spring Boot 4 the Flyway
auto-configuration lives in the separate **`spring-boot-starter-flyway`**
module; with only the raw jars on the classpath no Flyway bean exists, the
migration silently does NOT run, and Hibernate then tries `alter table ...
add column owner_id/email ... not null` on populated tables and fails
(`column contains null values`), leaving the deployment half-migrated.
**Fix:** replaced `flyway-core` with `spring-boot-starter-flyway` in
`pom.xml` (`flyway-database-postgresql` kept). After the fix, booting against
the legacy DB applies baseline + V1 first and Hibernate's update step becomes
a no-op. The failure was reproduced before the fix and is absent after it.

### 2b. Monitor deletion was not "best-effort" w.r.t. Redis
`MonitorService.deleteMonitor` documents that clearing Redis scheduling/
status keys must never undo the deletion, but
`RedisSchedulerService.deleteMonitorCache` propagated
`RedisConnectionException` (reproduced live with Redis down → HTTP 500 and
the transaction rolled back). **Fix:** the method now swallows-and-logs
runtime failures (stale keys for a deleted monitor id are harmless). The
delete path was re-verified live: **HTTP 2xx and the row is gone even with
Redis completely down** (`monitorResultRepo`/`incidentRepo`/`monitorRepo`
deletions were all committed).

### 2c. (not a bug) Idempotent V1 re-run
Booting twice against the migrate DB re-runs V1 safely; every clause is
guarded by existence checks.

## 3) First-user registration concurrency (item 3)

- **Race existed** (`RegistrationService#register`: `count()==0` decision via
  `@Transactional` — two simultaneous registrations could both see an empty
  user table and both become ADMIN; additionally the commit happened outside
  any lock).
- **Fix (minimal, single-instance-appropriate):** `register()` no longer uses
  proxy-managed `@Transactional`. The registration body runs inside one
  JVM-wide monitor (`registrationLock`) **and** the transaction is committed
  by an injected `TransactionTemplate` *inside* the synchronized block before
  the lock is released — so the empty-table check and the ADMIN insert can
  never interleave between two registrations. Repository use is
  `saveAndFlush` so constraint violations surface inside the transaction and
  translate to `DuplicateUserException` (409) as before.
- **New concurrency test** `service/RegistrationConcurrencyTest` (`mvnw test`
  count is now **65**, see §5): empties the shared test DB, then launches
  **10 simultaneous registrations** from 10 threads behind a `CountDownLatch`.
  Asserts: all 10 persisted with distinct usernames, each got its per-account
  settings row, **exactly one ADMIN** and it is one of the racers, the rest
  USER; DB state matches what callers received. This passed (and would fail
  against the old code under contention).
- Subsequent-registration rule: `RegistrationServiceTest.firstAccountOnEmptyInstance
  BecomesAdministrator`/`laterAccountsArePlainUsers` (unit), the new
  integration test, and **live checks on the real Postgres DB** (§1a/§1b)
  all confirm later accounts are USER and a client-supplied `role` field is
  ignored.

## 4) Files changed in this review task

- `pom.xml` — `spring-boot-starter-flyway` instead of raw `flyway-core`
  (critical fix 2a).
- `src/main/java/com/project/api_monitor/service/RegistrationService.java`
  — atomic first-account decision (lock + commit-in-lock via
  `TransactionTemplate`; `saveAndFlush`).
- `src/main/java/com/project/api_monitor/service/RedisSchedulerService.java`
  — `deleteMonitorCache` best-effort (critical fix 2b).
- `src/test/java/com/project/api_monitor/service/RegistrationServiceTest.java`
  — adapted to the new constructor (mock `PlatformTransactionManager`,
  `beforeEach` stub returns `SimpleTransactionStatus`; `save`→`saveAndFlush`).
- `src/test/java/com/project/api_monitor/service/RegistrationConcurrencyTest.java`
  — NEW: 10-thread simultaneous-registration integration test.
- Helper scripts created OUTSIDE the repo in `%TEMP%\opencode\`
  (`legacy_seed.sql`, `SqlProbe.java`, `ExecScript.java`, verification SQL) —
  not committed.

## 5) Full test suite & builds

- `mvnw test`: **65 tests, 0 failures, 0 errors — BUILD SUCCESS**
  (was 64 before; +1 = `RegistrationConcurrencyTest`).
- `npm run build` (frontend production): **✓ built** (no errors).
- `npm run lint`: not re-run in this pass (0-failure baseline from previous
  session remains).
- No frontend source changes were made in this task; the monitoring pipeline
  (Kafka→Redis→Postgres scheduler/consumer code) was not modified — only the
  documented best-effort semantics of cache cleanup at deletion were restored.

## 6) Remaining limitations (known, honest)

1. **Fresh installs get no `idx_*` helper indexes.** They are added by V1 only
   on the migration path (guard `IF NOT EXISTS` runs when legacy tables
   exist). On a genuinely empty DB Hibernate creates the schema without them
   and later re-runs of V1 no-op. Purely a performance nit for small instances.
2. **Concurrency is only locally atomic.** The registration lock guarantees
   correctness for a single JVM instance — accurate for this deployment
   (background scheduler/consumer share the process). If the app is ever run
   as multiple instances against one DB, a DB-level advisory lock (e.g.
   `pg_advisory_xact_lock`) should supplement it; H2 tests cannot express
   that portably.
3. Polling/notifications were exercised during these boots with the
   EmailJS env placeholders and seeded disabled monitors; no email actually
   sent. EmailJS keys were NOT real.
4. During the boot an upstream quirk was noted but left alone: the app's
   `application.properties` uses `${EMAILJS_SERVICE_ID:}`-style defaults,
   which Spring Framework 7 fails to resolve when the env var is completely
   unset (production always sets them; smoke boots provided dummy values).
5. Everything remains uncommitted (including the prior registration/
   isolation work). Commit when asked.

## 7) Exact next steps for any future session

1. Re-run `./mvnw test` (65/65) and `npm run build` to confirm a green tree.
2. Decide whether the fresh-install index gap (§6.1) warrants a small V2
   migration or a Postgres-only Hibernate `import` — discussion-level change,
   not required for correctness.
3. If deploying: run the new jar against the REAL `api_monitor` DB during a
   maintenance window and watch `flyway_schema_history` (expect baseline 0 +
   V1 success, all rows preserved).

_This handoff describes work performed up to 2026-11-01; nothing was
committed or discarded._
