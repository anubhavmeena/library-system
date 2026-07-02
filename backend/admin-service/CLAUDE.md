# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Admin-only service with two responsibilities: (1) HTTP API for dashboard stats, student management, seat map, and revenue reports; (2) scheduled daily expiry reminder job. All endpoints require `ADMIN` role (enforced by `AdminRoleFilter` at the gateway).

## Port & Entry

- Port: **8086**
- Main class: `AdminServiceApplication`
- Dependencies: PostgreSQL (read + write), Kafka (producer only)
- **Replicas: 1** — must not be scaled beyond 1 without adding a distributed lock (e.g. ShedLock) to prevent the scheduler from firing multiple times.

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Endpoints

All routes require `X-User-Role: ADMIN` (enforced upstream by the gateway).

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/admin/dashboard` | Aggregated stats: students, memberships, seats, revenue |
| `GET` | `/api/admin/students?page=&size=&status=` | Paginated student list with active membership joined |
| `GET` | `/api/admin/students/{userId}` | Single student with active membership details |
| `PATCH` | `/api/admin/students/{userId}/status` | Enable/disable a student account |
| `GET` | `/api/admin/seats/map?shift=&date=` | 110-seat grid with occupant details |
| `GET` | `/api/admin/seats/{seatNumber}/history` | Every booking ever made against a seat, newest first |
| `GET` | `/api/admin/memberships/expiring?withinDays=7` | Students whose membership expires within N days |
| `POST` | `/api/admin/reminders/send` | Publish renewal reminder Kafka events (bulk or targeted) |
| `GET` | `/api/admin/reports/revenue?from=&to=` | Revenue totals + daily breakdown between two dates |

## Read Model Design

`admin-service` has its **own copies** of the `User`, `Membership`, `Plan`, and `Payment` entity classes, all mapping to the same PostgreSQL tables as the other services. This is intentional — the admin service is a cross-domain read model that avoids HTTP calls to sibling services. Do not add HTTP client calls to other services; query the DB directly instead.

The `admin-service` entities are mostly read-only. The only writes are:
- `updateStudentStatus()` — sets `user.isActive`
- `sendBulkReminders()` — does **not** set `reminderSent`; that is only set by the scheduler
- `ExpiryReminderScheduler` — sets `membership.reminderSent = true`

## `ExpiryReminderScheduler`

- Cron: `0 0 9 * * *` (every day at 9:00 AM)
- Query: `findExpiringMemberships(today, today + 7)` — only fetches memberships where `reminderSent = false`
- Only fires if `daysLeft == 7` or `daysLeft == 3`. On any other day in the window (6, 5, 4, 2, 1), the record is skipped but `reminderSent` remains `false` so tomorrow's run reconsiders it
- After firing at the 7-day or 3-day mark, sets `reminderSent = true` — the membership is excluded from all future scheduler runs
- Publishes `RenewalReminderEvent` to Kafka topic `renewal-reminder` (key: `userId`)
- Bulk-loads users with `findAllById` to avoid N+1 queries

## `sendBulkReminders` (manual send via API)

Called by `POST /api/admin/reminders/send`. Accepts an optional list of `userIds`:
- Empty/null list → sends to **all** students expiring within 7 days (regardless of `reminderSent`)
- Non-empty list → sends only to the specified students

This intentionally bypasses the `reminderSent` flag — it is a manual override for the admin. It does **not** set `reminderSent = true` after sending.

## Dashboard Stats

`getDashboardStats()` runs multiple aggregate queries. `totalSeats` is hardcoded as `110L` — it is not derived from the `seats` table. Revenue queries use `PaymentRepository.sumRevenueForPeriod()` which returns `null` (not zero) when there are no transactions; null-coalesced to `BigDecimal.ZERO`.

## `getSeatMap()` — Performance Note

Currently loads all ACTIVE memberships expiring before `today + 1 year`, then filters in-memory by shift and date. For the current scale (110 seats) this is acceptable. If the membership table grows large, push the shift/date filter into the JPA query.

## Key Config

| Property | Env var | Default |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `app.reminder.days-before` | _(yml only)_ | `7` (currently unused in scheduler — hardcoded in query) |
