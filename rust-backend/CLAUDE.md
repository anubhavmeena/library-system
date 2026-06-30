# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build release binary
cargo build --release

# Run (requires PostgreSQL + Redis)
cargo run

# Run all tests (no DB/Redis required)
cargo test

# Run a single test by name
cargo test jwt::tests::create_and_decode_roundtrip

# Run all tests in a module
cargo test services::jwt::tests

# Run with output visible
cargo test -- --nocapture

# Check without building
cargo check
```

The release binary lands at `target/release/library-backend`.

## Architecture

This Rust binary is a drop-in replacement for the entire Java microservices stack (api-gateway + auth/user/membership/seat/admin/notification services) in a single process. It connects to the same shared PostgreSQL database and Redis instance.

### Request path

```
HTTP request
  → axum Router (routes.rs)
  → FromRequestParts extractors: AuthUser / AdminUser (middleware/auth.rs)
      reads Authorization: Bearer <JWT>, decodes claims, rejects if invalid/missing
  → handler fn (handlers/<domain>.rs)
  → service fn (services/<domain>.rs)  ← all DB/Redis/HTTP logic lives here
  → sqlx / redis / reqwest calls
```

`AuthUser` and `AdminUser` implement `FromRequestParts` — they read only headers, never the body, so they compose cleanly with `Multipart` extractors.

### Module layout

| Path | Purpose |
|---|---|
| `src/handlers/` | Thin axum handlers: deserialize request, call service, return ApiResponse |
| `src/services/` | Business logic: DB queries, Redis ops, external HTTP calls |
| `src/models/` | `FromRow` structs (DB), serde structs (JSON), request DTOs |
| `src/middleware/auth.rs` | `AuthUser` / `AdminUser` extractors |
| `src/error.rs` | `AppError` enum → HTTP status mapping via `IntoResponse` |
| `src/response.rs` | `ApiResponse<T>` envelope for all successful responses |
| `src/config.rs` | `Config::from_env()` — all env vars with defaults |
| `src/routes.rs` | Full route table + `DefaultBodyLimit` overrides for upload routes |

### Error handling

All service functions return `crate::error::Result<T>` (alias for `std::result::Result<T, AppError>`). `AppError` implements `IntoResponse`, so the `?` operator in handlers propagates errors directly to HTTP responses:

- `NotFound` → 404, `BadRequest` → 400, `Unauthorized` → 401, `Forbidden` → 403, `Conflict` → 409, `Internal` / `Database` / `Redis` → 500

### Authentication

JWT is HS256, validated entirely in-process (no auth-service HTTP call). Claims carry `sub` (user UUID), `role`, `name`, `email`, `mobile`. The `AuthUser` extractor decodes the token and provides these fields to handlers via `user.user_id`, `user.role`, etc.

### Payments

Supports both Razorpay and Cashfree, switched by `PAYMENT_GATEWAY` env var (default `CASHFREE`). When the relevant key/app-id env var is empty, dev mode activates: order IDs are `dev_order_*` and payment verification is skipped. Razorpay uses HMAC-SHA256; Cashfree uses server-side GET `/pg/orders/{id}`.

### File uploads

Files saved to `UPLOAD_DIR/{user_id}/{kind}_{sanitized_filename}`. URLs stored in DB as `/uploads/...` and served via `tower_http::ServeDir` at `/uploads`. The upload routes have a 10 MB `DefaultBodyLimit` override; all other routes keep the axum default (2 MB).

### Seat booking

Seat bookings use `(seat_id, shift, booking_date)` as a unique key — one record per seat per shift per start-date, not one per day. The `ON CONFLICT ... DO UPDATE ... WHERE status != 'ACTIVE'` pattern reclaims released slots without silently swallowing the insert. The Redis availability cache key is `seats:availability:{SHIFT}:{date}` with 5-minute TTL; invalidated on any book/release/seat-change.

### Dev-mode shortcuts

| Dependency | Empty credential | Behaviour |
|---|---|---|
| Twilio / Meta WhatsApp | empty | OTP is always `123456`, logged to stdout |
| Razorpay | `RAZORPAY_KEY_ID` empty | `dev_order_*` IDs, HMAC skip |
| Cashfree | `CASHFREE_APP_ID` empty | `dev_order_*` IDs, verify skip |
| SendGrid | `SENDGRID_API_KEY` empty | email logged, not sent |

### Scheduled job

`ExpiryReminderScheduler` runs at 09:00 daily via `tokio-cron-scheduler`. It calls `send_renewal_reminders` (same as the admin API endpoint) which sends WhatsApp/email reminders for memberships expiring in ≤ 7 days.

## Key Invariants

- `seat_bookings.booking_date` is the membership start date; `end_date` is the membership end date. The date range is **inclusive**. The seat map query uses `booking_date <= :date AND end_date >= :date`.
- `change_membership_seat` and `update_membership_plan` must both update `seat_bookings` **and** invalidate the Redis cache — forgetting either causes the seat map to show stale data.
- `STUDENT_SELECT` (admin student list) wraps `seat_number` in `CASE WHEN m.status = 'ACTIVE' THEN ... END` so PENDING memberships don't show a seat as claimed.
- The `uploads/` directory is in `.gitignore` — never commit user files.
