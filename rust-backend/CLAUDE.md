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
| apitxt SMS | `APITXT_AUTH_KEY` empty | OTP falls through to Twilio SMS |

### Scheduled jobs

Two daily `tokio-cron-scheduler` jobs, registered in `main.rs::start_scheduler`:
- **09:00 UTC** — `run_expiry_reminder_job` → `send_renewal_reminders`: WhatsApp/email reminders for memberships expiring in ≤ 7 days. Uses the scheduler's default UTC timezone deliberately — matches Java's `sendExpiryReminders`, which runs in the JVM-default (container) timezone, itself UTC.
- **05:00 IST** (`Asia/Kolkata`, fixed `+05:30` offset via `Job::new_async_tz` — India has no DST) — `run_mark_expired_job` → `mark_expired_and_start_grace`: for every `ACTIVE` membership whose `end_date` has passed, either activates a queued renewal (happy path) or transitions the membership to `GRACE` with `dues_amount` set and the seat held via the far-future sentinel. Matches Java's `markExpiredAndStartGrace`, which pins `zone = "Asia/Kolkata"` explicitly rather than trusting the container's default. Also reachable on demand via `POST /api/admin/memberships/run-expiry-check` (backs the admin "Cron Jobs" page).

### Membership grace/dues workflow

`memberships.status` can be `PENDING | ACTIVE | QUEUED | GRACE | EXPIRED | CANCELLED`. `GRACE` never auto-resolves in the DB — `services::membership::resolve_display_status` computes a *display* status (`NEW/PAID/PENDING/GRACE/EXPIRED/RELEASED`) from the DB status + `grace_days` (from `app_settings`, default 10): a `GRACE` row reads as `EXPIRED` once `today - end_date > grace_days`, but the row itself stays `GRACE` until an admin releases the seat or the dues are cleared.

`services::membership::find_current_membership` orders `WHERE status IN ('ACTIVE','GRACE') ORDER BY CASE WHEN status='GRACE' THEN 0 ELSE 1 END, end_date DESC LIMIT 1` — **GRACE always outranks ACTIVE** regardless of date, so an unresolved dues row is never hidden behind a newer admin-created ACTIVE booking.

A `GRACE` membership's linked `seat_bookings.end_date` is pushed to the sentinel `9999-12-31` to hold the seat indefinitely. **Never pass that sentinel as the upper bound to `seat::invalidate_seat_cache`** — it loops day-by-day and would hang. Always cap invalidation at `today` (or the real end date) when touching a GRACE-adjacent booking.

Two distinct dues-clearing code paths intentionally differ (mirrors the Java backend): `admin::clear_dues` extends `end_date` by **+1 month from the membership's existing (stale) end_date**; `membership::verify_and_pay_dues` (student self-serve) extends by the **plan's `duration_days`** from that same stale end_date. Don't "fix" this inconsistency without checking both call sites' assumptions.

`create_order`/`create_cash_membership` block if the user has an unresolved `GRACE` row or an existing `QUEUED` row. `create_cash_membership` also enforces `paid_amount + pending_amount == plan.price` exactly.

### App / notification settings

`app_settings` is a singleton row (`id = 1`, lazily created on first read). `notification_settings` has one row per key from the in-code catalog (`models::settings::NOTIFICATION_CATALOG`) — also lazily created. Both collapse what the Java backend had to duplicate across 2-3 services into one table + one module, since this is a single binary. `settings::setting_for(state, key)` is fail-open: if the row read fails, it falls back to catalog defaults rather than erroring.

### PDF generation & WhatsApp document/image attachments

ID cards (`services/idcard.rs`) and payment receipts (`services/receipt.rs`) are both rendered with `printpdf` (feature `embedded_images` enabled for photo embedding via `printpdf::image_crate`, re-exported `image` 0.24). Photos are cover-scaled (cropped to a centered square, then resized) — never letterboxed. Generated files are written directly under `UPLOAD_DIR/{receipts,id-cards}/` (served by the same `/uploads` `ServeDir` everything else uses) and referenced via `FRONTEND_URL + /uploads/...` links in Meta template messages — there's no pod-to-pod upload step like Java's, since this is one process.

`notification::send_document_template`/`send_image_template` send Meta WhatsApp template messages with a `document`/`image` header `link` (not the `/media` upload endpoint). The receipt template's language code (`META_WHATSAPP_RECEIPT_LANGUAGE`, default `en`) is deliberately separate from the general notification template's language (`META_WHATSAPP_LANGUAGE`, default `en_US`) — Meta requires an exact match per approved template.

### OTP channel routing

`POST /api/auth/send-otp` accepts an optional `channel` field. `channel: "SMS"` skips Meta WhatsApp entirely; otherwise the chain is Meta WhatsApp → apitxt SMS → Twilio SMS, each falling through only on failure. The Redis cooldown key `otp:cooldown:<contact>` has a **10s** TTL (not 30s) — short enough for the frontend to offer both "Resend OTP" and, after a second wait, "Send via SMS instead".

### Admin mailbox (IMAP)

`services/mailbox.rs` backs the admin "Inbox" page (`GET/DELETE /api/admin/inbox/:messageNumber`, `POST .../reply`) — a thin IMAP client reading the same mailbox Java's `MailboxService` (JavaMail) reads, so both must point at the same account. All IMAP/SMTP calls are synchronous (the `imap`/`lettre` crates are blocking) and run inside `tokio::task::spawn_blocking`; nothing here is async internally.

- **Message numbers are IMAP sequence numbers**, not UIDs — stable only within one mailbox session as long as nothing gets expunged concurrently. Each request opens a fresh `Session`, matching Java's per-call `openStore()`.
- **List vs. read** use different fetch items on purpose: list uses `BODY.PEEK[]` (never marks read), the single-message getter uses `BODY[]` plus an explicit `+FLAGS (\Seen)` STORE — mirrors Java relying on JavaMail's implicit read-marks-seen behavior while also setting it explicitly.
- Body extraction prefers `text/html`, falls back to `text/plain` wrapped in a `<pre>` (for the admin's sandboxed iframe preview), recurses into `multipart/*`, otherwise a "no readable content" placeholder — see `extract_body`.
- Replies go out over `SMTP_HOST:SMTP_PORT` via `lettre`'s `builder_dangerous` (no TLS, no auth) — this assumes a **trusted local relay** (Postfix on the same host in production), exactly like Java's plain `spring.mail.*` config. Don't add TLS/auth here without changing that assumption on both backends.
- Config env vars (`IMAP_HOST`, `IMAP_PORT`, `IMAP_SSL`, `ADMIN_IMAP_USER`, `ADMIN_IMAP_PASS`, `SMTP_HOST`, `SMTP_PORT`, `FROM_EMAIL`, `FROM_NAME`) share the exact same names as the Java stack's `.env` — copy them through as-is rather than renaming.

## Key Invariants

- `seat_bookings.booking_date` is the membership start date; `end_date` is the membership end date. The date range is **inclusive**. The seat map query uses `booking_date <= :date AND end_date >= :date`.
- `change_membership_seat` and `update_membership_plan` must both update `seat_bookings` **and** invalidate the Redis cache — forgetting either causes the seat map to show stale data.
- `STUDENT_SELECT` (admin student list) wraps `seat_number` in `CASE WHEN m.status IN ('ACTIVE', 'GRACE') THEN ... END` so PENDING memberships don't show a seat as claimed, while GRACE memberships (whose seat is still held) do.
- The `uploads/` directory is in `.gitignore` — never commit user files.
- Every timestamp column in `migrations/` is plain `TIMESTAMP`, not `TIMESTAMPTZ` — every corresponding Rust struct field is `NaiveDateTime`, and sqlx's chrono support only decodes that from `TIMESTAMP`. `001_initial.sql`/`003_add_broadcast_messages.sql` originally declared several columns `TIMESTAMPTZ`; `006_fix_timestamp_types.sql` corrects this. If you add a new timestamp column, keep it plain `TIMESTAMP` unless you also switch the Rust field to `DateTime<Utc>`.
- Never call `seat::invalidate_seat_cache` with the `9999-12-31` GRACE sentinel as the upper bound — it iterates day-by-day and will hang. Cap at `today` for any GRACE-adjacent invalidation.
