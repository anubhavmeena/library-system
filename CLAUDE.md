# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repo Layout — Three Parallel Backend Implementations

This repo contains **three independent backend implementations of the same API** plus a shared frontend and two mobile clients. They are not layers of one system — each is a complete, standalone rewrite:

| Dir | Stack | Status | Deep-dive doc |
|---|---|---|---|
| `backend/` | Java 17 / Spring Boot 3.2, one microservice per domain | **Primary — the one built by `docker-compose.services.yml` and `k8s/`** | `backend/CLAUDE.md` (+ one per service, e.g. `backend/auth-service/CLAUDE.md`) |
| `go-backend/` | Go, mirrors the same microservice split (`go.work` multi-module) | Alternate implementation, run via `go-backend/run.sh`; not wired into `docker-compose.services.yml` or `k8s/` | none yet |
| `rust-backend/` | Rust / axum, **single binary** replacing the whole stack | Alternate implementation; not wired into `docker-compose.services.yml` or `k8s/` | `rust-backend/CLAUDE.md` |
| `frontend/` | React + Vite + Redux Toolkit + Tailwind | Shared by all three backends (talks to whichever gateway runs on :8080) | `frontend/CLAUDE.md` |
| `android/` | Kotlin / Jetpack Compose | Student + admin app | — |
| `ios/` | Swift | — | — |

**When asked to fix or extend backend behavior, confirm which backend the user means** — the Java version under `backend/` is the one actually deployed; Go and Rust are parallel ports and can drift from it. Don't assume a fix in one carries over to the others.

Root-level `*.py` scripts (`test_admin.py`, `test_booking_notification.py`, `selenium_login_test.py`, etc.) and `tests/test_auth_selenium.py` are ad-hoc/manual Selenium & requests scripts against a running stack, not a wired-up pytest suite — there's no `requirements.txt`/`pytest.ini` at the root.

## Build & Run Commands

### Java backend (primary — each service is an independent Maven project)
```bash
cd backend/<service-name>
./mvnw clean package -DskipTests   # build fat JAR
./mvnw spring-boot:run             # run locally
./mvnw test                        # all tests
./mvnw test -Dtest=SeatServiceTest # single test class
```

### Go backend (alternate)
```bash
cd go-backend
./run.sh              # builds every service with `go build` and launches them all against local Postgres/Redis
```

### Rust backend (alternate)
```bash
cd rust-backend
cargo run              # single process, needs Postgres + Redis
cargo test              # cargo test <module::path> for a subset
```

### Frontend
```bash
cd frontend
npm install
npm run dev        # dev server on :3000, proxies /api → :8080
npm run build      # production build
npm run preview    # preview production build
```

### Everything at once (Java backend + infra)
```bash
docker-compose -f docker-compose.infra.yml -f docker-compose.services.yml up -d
```
Starts Postgres, Redis, Zookeeper, Kafka, all seven Java services, and the frontend. See `Run.md` for variants (rebuild, tail logs, teardown).

### Docker / Kubernetes
```bash
# Build a single image (each service has its own Dockerfile)
docker build -t target-zone/<service>:latest backend/<service>

# Deploy to Kubernetes
kubectl apply -f k8s/

# Namespace: library-system
kubectl -n library-system get pods
```

## Dev Mode Shortcuts

When external credentials are absent, the system falls back gracefully (true across all three backends):
- **OTP**: Twilio/Meta WhatsApp credentials empty → any OTP input is accepted as `123456`
- **Payments**: active gateway's key/app-id env var empty → a `dev_order_*` ID is generated and signature/status verification is skipped on verify
- **Notifications**: SendGrid/Twilio empty → logged but not sent

Copy `.env` to set up local environment. Java services pick it up via Spring `@Value` / `application.yml` placeholders; Go/Rust read it directly via `os.Getenv` / `Config::from_env()`.

## Architecture (applies to all three backend implementations)

### Request Flow
```
Browser → Nginx Ingress → API Gateway (:8080)
                              ↓
                  AuthFilter (JWT validation on all routes)
                  AdminRoleFilter (guards /api/admin/**)
                              ↓
          auth(:8081) / user(:8082) / membership(:8083)
          seat(:8084) / notification(:8085) / admin(:8086)
```

The gateway reads the JWT secret directly — it does **not** call auth-service to validate tokens. User ID and role are extracted from the JWT and forwarded downstream as `X-User-Id` and `X-User-Role` headers, so individual services trust those headers without re-validating. (In `rust-backend`, this collapses into per-request `AuthUser`/`AdminUser` extractors since there's no separate gateway process.)

### Async Event Flow (Kafka)
Three Kafka topics drive all notifications:

1. **`booking-confirmed`** — published by `membership-service` after payment verification. `notification-service` consumes it and sends WhatsApp + email to the student, plus an admin alert.
2. **`user-registered`** — published by `auth-service` after registration, reusing the `BookingConfirmedEvent` shape, to send a welcome message.
3. **`renewal-reminder`** — published by `ExpiryReminderScheduler` in `admin-service` at the 7-day and 3-day thresholds (daily 9AM job), or on-demand via bulk admin send. `notification-service` consumes and sends renewal reminders.

`admin-service` intentionally runs at **replicas: 1** to prevent the daily scheduler from firing multiple times. If you scale it, add ShedLock.

### Seat Availability Caching (Redis)
`SeatService.getAvailability()` caches the full seat grid per `(shift, date)` in Redis with a 5-minute TTL (key: `seats:availability:<SHIFT>:<date>`). Any `bookSeat()` or `releaseSeat()` call busts the cache for the affected date range. A `FULL_DAY` booking also invalidates `MORNING` and `EVENING` cache keys for the same dates. The library has **112 fixed seats**: rows A(28), B(28), C(28), D(28) — seeded once into the `seats` table (`common-lib/src/main/resources/db/seed.sql`), not stored in a config table.

### Database Strategy
All services/backends share one **PostgreSQL** instance but use separate tables per domain — no cross-service foreign keys; `userId`, `membershipId`, etc. are bare UUIDs resolved in application code. The schema is created once by `backend/common-lib/src/main/resources/db/schema.sql` (executed by Postgres on first container init); the Java services' `ddl-auto: update` is a safe no-op after that, so schema changes should generally go through that file rather than relying on Hibernate to add columns.

### Authentication Flow
Students log in via OTP (not password):
1. `POST /api/auth/send-otp` — OTP sent via Twilio/Meta WhatsApp SMS, stored in Redis with TTL
2. `POST /api/auth/verify-otp` — returns a short-lived `sessionToken` and `isNewUser` flag
3. New users: `POST /api/auth/register` (with `sessionToken`) → JWT issued
4. Returning users: `POST /api/auth/login` (with `sessionToken`) → JWT issued

Admins use `POST /api/auth/admin/login` with `{ contact, otp }` — **no password field and no real OTP check**: it looks up the user by mobile/email and grants access if `role == ADMIN` in the DB *or* the contact is in the `ADMIN_PHONES` env var allowlist, then issues a JWT. Admins are seeded directly in the DB / via `ADMIN_PHONES`, not through the registration flow.

JWT is stored in `localStorage` and attached by the Axios interceptor in `frontend/src/services/api.js`. On 401, the interceptor clears storage and redirects to `/login`.

### Frontend State
Redux store has three slices:
- `authSlice` — user, JWT token, OTP flow state (`otpSent`, `otpVerified`, `sessionToken`, `isNewUser`)
- `membershipSlice` — current membership, plans, payment state
- `seatSlice` — seat availability grid, selected seat

Route protection is handled by `ProtectedRoute` in `App.jsx`, which checks both `token` presence and `user.role` (STUDENT | ADMIN).

### Payment Flow (Cashfree default, Razorpay switchable)
Gateway is chosen per-request by the `PAYMENT_GATEWAY` env var (`CASHFREE` default, or `RAZORPAY`) — not hardcoded to one provider.
1. Frontend calls `POST /api/payments/create-order` → `membership-service` creates a PENDING `Membership` + PENDING `Payment` record, creates an order with the active gateway (or a `dev_order_*` id if that gateway's credentials are blank)
2. Frontend opens the corresponding checkout widget (Cashfree JS SDK or Razorpay checkout)
3. On success, frontend calls `POST /api/payments/verify` → `membership-service` verifies the payment — Razorpay via HMAC-SHA256 signature, Cashfree via a server-side `GET /pg/orders/{id}` status poll — sets `Membership.status = ACTIVE`, publishes `booking-confirmed` Kafka event

### Service Port Map
| Service | Port |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| user-service | 8082 |
| membership-service | 8083 |
| seat-service | 8084 |
| notification-service | 8085 (Kafka consumer only, no HTTP routing) |
| admin-service | 8086 |
| frontend (Nginx) | 80 |

`rust-backend` serves all of the above from a single process on one port instead.
