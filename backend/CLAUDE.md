# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Each service is an independent Maven project. There is no parent/aggregator POM — run commands from within each service directory.

```bash
cd backend/<service-name>

./mvnw clean package -DskipTests   # build fat JAR
./mvnw spring-boot:run             # run locally
./mvnw test                        # all tests
./mvnw test -Dtest=ClassName       # single test class
```

All services target **Java 17** and Spring Boot **3.2.0**.

## Service Map

| Service | Port | Package root |
|---|---|---|
| `api-gateway` | 8080 | `com.library.gateway` |
| `auth-service` | 8081 | `com.library.auth` |
| `user-service` | 8082 | `com.library.user` |
| `membership-service` | 8083 | `com.library.membership` |
| `seat-service` | 8084 | `com.library.seat` |
| `notification-service` | 8085 | `com.library.notification` |
| `admin-service` | 8086 | `com.library.admin` |

`common-lib/` directory exists but is currently empty — no shared JAR is published yet.

## Cross-Cutting Patterns

### How user identity reaches downstream services
`AuthFilter` (api-gateway) validates the JWT using the shared `JWT_SECRET`, extracts `sub` (userId) and `role` claim, then **mutates the request** to add `X-User-Id` and `X-User-Role` headers before forwarding. Downstream services trust these headers directly — they never re-verify the JWT. Controllers declare `@RequestHeader("X-User-Id") String userId`.

`AdminRoleFilter` runs after `AuthFilter` on `/api/admin/**` routes and rejects requests where `X-User-Role != ADMIN` with 403.

### Public paths (no JWT required)
Hardcoded in `AuthFilter.PUBLIC_PATHS`: `/api/auth/send-otp`, `/api/auth/verify-otp`, `/api/auth/register`, `/api/auth/login`, `/api/plans`.

### API response envelope
Every controller wraps its return value in `ApiResponse<T>`, a generic DTO with `success`, `message`, and `data` fields. Each service has its own copy of this class (no shared lib yet). Frontend reads `response.data.data`.

### Exception handling
Every service has a `GlobalExceptionHandler` (`@RestControllerAdvice`) that catches `ResourceNotFoundException` → 404, `IllegalArgumentException` → 400, and general `Exception` → 500, all wrapped in the `ApiResponse` envelope.

## OTP & Auth Dev Mode

`AuthService.generateOtp()` returns the hardcoded string `"123456"` when `SPRING_PROFILES_ACTIVE=dev`. OTPs are stored in Redis under key `otp:<contact>` with a 5-minute TTL. After successful OTP verification, a UUID `sessionToken` is stored under `session:<token>` with a 15-minute TTL — register/login endpoints exchange this token for a JWT.

Admin login (`POST /api/auth/admin/login`) does **not** use OTP — it accepts the contact and OTP fields but actually skips OTP check and just validates that `user.role == ADMIN`. In practice, admins log in by knowing the registered admin contact.

## Kafka Topics

| Topic | Producer | Consumer | Event DTO |
|---|---|---|---|
| `booking-confirmed` | `membership-service` (after payment verify) | `notification-service` | `BookingConfirmedEvent` |
| `user-registered` | `auth-service` (after register) | `notification-service` | `BookingConfirmedEvent` (reused shape) |
| `renewal-reminder` | `admin-service` scheduler + bulk send | `notification-service` | `RenewalReminderEvent` |

`notification-service` uses two separate `KafkaListenerContainerFactory` beans (`bookingKafkaListenerContainerFactory`, `reminderKafkaListenerContainerFactory`) configured in `KafkaConfig`. All notification delivery is logged to the `notification_logs` table (`NotificationLog` entity).

`notification-service` does **not** expose any HTTP endpoints — it is purely event-driven.

## Redis Usage

| Service | Key pattern | Purpose | TTL |
|---|---|---|---|
| `auth-service` | `otp:<contact>` | OTP storage | 5 min |
| `auth-service` | `session:<token>` | Post-OTP session | 15 min |
| `seat-service` | `seats:availability:<SHIFT>:<date>` | Seat grid cache | 5 min (configurable via `app.seat.cache-ttl`) |

`SeatService` busts the cache on every `bookSeat()` and `releaseSeat()` call. A `FULL_DAY` booking also invalidates `MORNING` and `EVENING` keys for the same date range.

## Seat Layout

The library has **110 fixed seats**: rows A(28), B(28), C(28), D(26). Seat numbers are `<row><index>` e.g. `A1`, `D26`. This layout is hardcoded in `AdminService.getSeatMap()` and `SeatGrid` on the frontend — it is not stored in a config table.

Shifts: `MORNING` (6AM–2PM), `EVENING` (2PM–10PM), `FULL_DAY` (6AM–10PM). A `FULL_DAY` booking occupies the seat for both sub-shifts. The unique constraint on `seat_bookings` is `(seat_id, shift, booking_date)`.

## Payment Flow (membership-service)

1. `POST /api/payments/create-order` — creates a `Membership` (status `PENDING`) + `Payment` (status `PENDING`) record, creates a Razorpay order. If `RAZORPAY_KEY_ID` is blank, generates a `dev_order_*` ID instead.
2. `POST /api/payments/verify` — verifies Razorpay HMAC signature (`orderId|paymentId` signed with `RAZORPAY_KEY_SECRET`). Skips HMAC if the order ID starts with `dev_` or if `razorpayKeySecret` is blank. On success: sets `Payment.status = SUCCESS`, sets `Membership.status = ACTIVE`, publishes `booking-confirmed` Kafka event.

## admin-service Design

`admin-service` duplicates the `User`, `Membership`, `Plan`, and `Payment` entities from other services (same table names, read-only JPA access). This is intentional — the admin service is a read model that aggregates across domains without HTTP calls to sibling services.

`ExpiryReminderScheduler` runs daily at `0 0 9 * * *`. It only fires Kafka events at the **7-day and 3-day** thresholds (`daysLeft == 7 || daysLeft == 3`). After firing, it sets `Membership.reminderSent = true` so the record is excluded from future queries. The service is deployed at **replicas: 1** to prevent duplicate scheduler firings — if you scale it, add ShedLock.
