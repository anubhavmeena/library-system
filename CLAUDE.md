# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

### Backend (each service is an independent Maven project)
```bash
# Build a single service (from its directory)
cd backend/<service-name>
./mvnw clean package -DskipTests

# Run a service locally
./mvnw spring-boot:run

# Run tests for a service
./mvnw test

# Run a single test class
./mvnw test -Dtest=SeatServiceTest
```

### Frontend
```bash
cd frontend
npm install
npm run dev        # dev server on :3000
npm run build      # production build
npm run preview    # preview production build
```

### Docker / Kubernetes
```bash
# Build all images (run from repo root, each service has its own Dockerfile)
docker build -t target-zone/<service>:latest backend/<service>

# Deploy to Kubernetes
kubectl apply -f k8s/

# Namespace: library-system
kubectl -n library-system get pods
```

## Dev Mode Shortcuts

When external credentials are absent, the system falls back gracefully:
- **OTP**: Twilio credentials empty → any OTP input is accepted as `123456`
- **Payments**: `RAZORPAY_KEY_ID` empty → a `dev_order_*` ID is generated and HMAC verification is skipped on verify
- **Notifications**: SendGrid/Twilio empty → `notification-service` will log but not send

Copy `.env` to set up local environment. Services pick up env vars via Spring `@Value` and `application.yml` placeholders.

## Architecture

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

The API Gateway is a Spring Cloud Gateway instance. It reads the JWT secret directly — it does **not** call auth-service to validate tokens. User ID and role are extracted from the JWT and forwarded downstream as `X-User-Id` and `X-User-Role` headers, so individual services trust those headers without re-validating.

### Async Event Flow (Kafka)
Two Kafka topics drive all notifications:

1. **`booking-confirmed`** — published by `membership-service` after Razorpay payment verification. `notification-service` consumes it and sends WhatsApp + email to the student.
2. **`renewal-reminder`** — published by `ExpiryReminderScheduler` in `admin-service` at the 7-day and 3-day thresholds. `notification-service` consumes and sends renewal reminders.

`admin-service` intentionally runs at **replicas: 1** to prevent the daily scheduler from firing multiple times. If you scale it, add ShedLock.

### Seat Availability Caching (Redis)
`SeatService.getAvailability()` caches the full seat grid per `(shift, date)` in Redis with a 5-minute TTL (key: `seats:availability:<SHIFT>:<date>`). Any `bookSeat()` or `releaseSeat()` call busts the cache for the affected date range. A `FULL_DAY` booking also invalidates `MORNING` and `EVENING` cache keys for the same dates.

### Database Strategy
All services share one **PostgreSQL** instance but use separate schemas/tables. There is no foreign-key relationship across service boundaries — `userId`, `membershipId`, etc. are stored as bare UUIDs and resolved in application code. This keeps services independently deployable.

### Authentication Flow
Students log in via OTP (not password). The flow is:
1. `POST /api/auth/send-otp` — OTP sent via Twilio SMS/WhatsApp, stored in Redis with TTL
2. `POST /api/auth/verify-otp` — returns a short-lived `sessionToken` and `isNewUser` flag
3. New users: `POST /api/auth/register` (with `sessionToken`) → JWT issued
4. Returning users: `POST /api/auth/login` (with `sessionToken`) → JWT issued

Admins use email + password (`POST /api/auth/admin/login`).

JWT is stored in `localStorage` and attached by the Axios interceptor in `frontend/src/services/api.js`. On 401, the interceptor clears storage and redirects to `/login`.

### Frontend State
Redux store has three slices:
- `authSlice` — user, JWT token, OTP flow state (`otpSent`, `otpVerified`, `sessionToken`, `isNewUser`)
- `membershipSlice` — current membership, plans, payment state
- `seatSlice` — seat availability grid, selected seat

Route protection is handled by `ProtectedRoute` in `App.jsx`, which checks both `token` presence and `user.role` (STUDENT | ADMIN).

### Payment Flow (Razorpay)
1. Frontend calls `POST /api/payments/create-order` → `membership-service` creates a PENDING `Membership` + PENDING `Payment` record, returns Razorpay order ID
2. Frontend opens Razorpay checkout widget
3. On success, frontend calls `POST /api/payments/verify` → `membership-service` verifies HMAC signature, sets `Membership.status = ACTIVE`, publishes `booking-confirmed` Kafka event

### Service Port Map
| Service | Port |
|---|---|
| api-gateway | 8080 |
| auth-service | 8081 |
| user-service | 8082 |
| membership-service | 8083 |
| seat-service | 8084 |
| notification-service | 8085 |
| admin-service | 8086 |
| frontend (Nginx) | 80 |
