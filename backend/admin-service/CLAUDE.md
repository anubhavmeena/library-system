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
| `POST` | `/api/admin/students/import/single` | Manually add one student (name + phone) |
| `GET` | `/api/admin/seats/map?shift=&date=` | 110-seat grid with occupant details |
| `GET` | `/api/admin/seats/{seatNumber}/history` | Every booking ever made against a seat, newest first |
| `GET` | `/api/admin/memberships/expiring?withinDays=7` | Students whose membership expires within N days |
| `PATCH` | `/api/admin/memberships/{membershipId}/release` | Force-release a currently-occupied seat (ACTIVE or GRACE membership) |
| `POST` | `/api/admin/memberships/run-expiry-check` | Manually run the grace-transition job on demand (see `ExpiryReminderScheduler` below) |
| `POST` | `/api/admin/reminders/send` | Publish renewal reminder Kafka events (bulk or targeted) |
| `GET` | `/api/admin/reports/revenue?from=&to=` | Revenue totals + daily breakdown between two dates |

Note: there is no student enable/disable endpoint. `User.isActive` is set once at creation and never updated — it has no auth/login check reading it. The admin-facing notion of student status is the read-only, computed `StudentStatusResolver.Status` (`NEW/PAID/PENDING/GRACE/EXPIRED/RELEASED`), derived from `Membership`/`Payment` state, not a persisted user flag.

## Read Model Design

`admin-service` has its **own copies** of the `User`, `Membership`, `Plan`, and `Payment` entity classes, all mapping to the same PostgreSQL tables as the other services. This is intentional — the admin service is a cross-domain read model that avoids HTTP calls to sibling services. Do not add HTTP client calls to other services; query the DB directly instead.

The `admin-service` entities are mostly read-only. The only writes are:
- `ImportService.importSingleStudent()` / `processRow()` (bulk CSV/XLSX import) — insert new `User` rows
- `AdminMembershipService.releaseSeat()` — sets `membership.status = EXPIRED` and the linked `SeatBooking.status = RELEASED`; can be called on an `ACTIVE` or `GRACE` membership (not GRACE-only — see below)
- `sendBulkReminders()` — does **not** set `reminderSent`; that is only set by the scheduler
- `ExpiryReminderScheduler.sendExpiryReminders()` — sets `membership.reminderSent = true`
- `ExpiryReminderScheduler.markExpiredAndStartGrace()` — sets `membership.status = GRACE` (or `EXPIRED` when a queued renewal takes over) and pushes the linked `SeatBooking.endDate` to a far-future sentinel

## `AdminMembershipService.releaseSeat()`

Admin-only "force release" action, exposed at `PATCH /api/admin/memberships/{membershipId}/release`. Accepts a membership in `ACTIVE` or `GRACE` status (rejects `PENDING`/`QUEUED`/`EXPIRED`/`CANCELLED` with a 400 — no currently-occupied seat to release). Sets `Membership.status = EXPIRED` and `SeatBooking.status = RELEASED`, busts the seat-availability cache for the next 14 days. Dues, if any, are **not** waived — they remain on the membership record. Releasing an `ACTIVE` membership immediately frees a currently-paying student's seat (used when an admin needs to force a seat open, not just clean up an already-lapsed `GRACE` one). Either way, the student's computed `StudentStatusResolver.Status` resolves to `RELEASED` afterward, since their latest membership is now `EXPIRED`.

## `ExpiryReminderScheduler`

Two independent daily jobs in the same class — order between them doesn't matter functionally, they touch disjoint membership rows.

- **`sendExpiryReminders()`** — Cron: `0 0 9 * * *`, JVM-default timezone (the container runs UTC, so this actually fires at 9:00 AM UTC / 2:30 PM IST).
  - Query: `findExpiringMemberships(today, today + 7)` — only fetches memberships where `reminderSent = false`
  - Only fires if `daysLeft == 7` or `daysLeft == 3`. On any other day in the window (6, 5, 4, 2, 1), the record is skipped but `reminderSent` remains `false` so tomorrow's run reconsiders it
  - After firing at the 7-day or 3-day mark, sets `reminderSent = true` — the membership is excluded from all future scheduler runs
  - Publishes `RenewalReminderEvent` to Kafka topic `renewal-reminder` (key: `userId`)
  - Bulk-loads users with `findAllById` to avoid N+1 queries
- **`markExpiredAndStartGrace()`** — Cron: `0 0 5 * * *`, explicit `zone = "Asia/Kolkata"` (fires at 5:00 AM IST regardless of the container's OS timezone). Finds `ACTIVE` memberships whose `endDate` has passed; moves them to `GRACE` (charging dues, holding the seat indefinitely) unless the student already has a `QUEUED` renewal, in which case the old membership is finalized `EXPIRED` and the queued one activates immediately. Also callable on demand via `POST /api/admin/memberships/run-expiry-check` (returns the count of memberships that entered grace) — useful for picking up a newly-expired membership immediately instead of waiting for the next scheduled run.
  - `AdminService.getSeatMap()` independently treats an `ACTIVE` membership whose `endDate` has already passed (as of the real current date) the same as `GRACE` for display purposes — this closes the timing gap between midnight (when a membership becomes overdue) and this cron actually running, so the seat map doesn't show a just-expired seat as available in the meantime.

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
