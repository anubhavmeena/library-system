# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Manages the physical seat inventory and bookings. Owns the `seats` (110 fixed seats) and `seat_bookings` tables. Caches seat availability grids in Redis to avoid repeated DB scans for every student browsing the booking page.

## Port & Entry

- Port: **8084**
- Main class: `SeatServiceApplication`
- Dependencies: PostgreSQL, Redis

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/seats/availability?shift=&date=` | Availability grid for all 110 seats (Redis-cached) |
| `POST` | `/api/seats/book` | Book a seat for a membership period |
| `DELETE` | `/api/seats/release/{membershipId}` | Release a booking (admin — cancellation) |
| `GET` | `/api/seats/my` | Caller's active bookings |
| `GET` | `/api/seats/admin/bookings?shift=&date=` | All bookings for a shift+date (admin view) |

## Seat Layout

110 seats across 4 rows: **A (28), B (28), C (28), D (26)**. Seat numbers are `<row><index>` e.g. `A1`, `D26`. This layout is hardcoded in `AdminService.getSeatMap()` (admin-service) and in the frontend `SeatGrid` component — it is not stored in any config table.

Shifts: `MORNING`, `EVENING`, `FULL_DAY`. The `resolveShift()` helper in `SeatService` maps any unrecognised value to `FULL_DAY`.

## Redis Caching

- **Key pattern:** `seats:availability:<SHIFT>:<yyyy-MM-dd>`
- **TTL:** 5 minutes (configurable: `app.seat.cache-ttl`, default `300` seconds)
- **Serialisation:** JSON via `GenericJackson2JsonRedisSerializer` (set in `RedisConfig`) — values are human-readable in `redis-cli`
- Cache is **written** after the first DB fetch in `getAvailability()`
- Cache is **busted** on every `bookSeat()` and `releaseSeat()` call for each date in the booking range
- A `FULL_DAY` booking also busts `MORNING` and `EVENING` keys for the same dates, since a full-day seat counts as occupied for both sub-shifts

## Booking Conflict Check

`SeatService.bookSeat()` uses a JPA derived query before inserting:
```
existsBySeatIdAndShiftAndStatusAndBookingDateLessThanEqualAndEndDateGreaterThanEqual(
    seatId, shift, ACTIVE, endDate, startDate
)
```
This detects any date-range overlap for the same seat+shift. Throws `SeatAlreadyBookedException` (→ HTTP 409) on conflict. There is also a DB-level unique constraint on `(seat_id, shift, booking_date)` as a safety net.

## Shift Cross-Booking Logic

Both the DB query and the Redis queries treat `FULL_DAY` as overlapping both `MORNING` and `EVENING`:
```sql
AND (sb.shift = :shift OR sb.shift = 'FULL_DAY' OR :shift = 'FULL_DAY')
```
This means: a MORNING booking blocks a FULL_DAY seat request, and a FULL_DAY booking blocks both MORNING and EVENING requests for the same seat.

## `SeatBookingRepository` Custom Queries

- `findBookedSeatIds(shift, date)` — returns only UUIDs (not full entities) for the availability grid to minimise data transfer
- `findActiveBookingsForShiftAndDate(shift, date)` — used by admin endpoint, returns full `SeatBooking` objects
- The conflict check method uses a Spring Data derived query — the method name encodes the full predicate

## Key Config

| Property | Env var | Default |
|---|---|---|
| `app.seat.cache-ttl` | _(yml only)_ | `300` (seconds) |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` |
| `spring.data.redis.port` | `REDIS_PORT` | `6379` |
