# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Manages membership plans, student memberships, and payments via Razorpay. Owns the `membership_plans`, `memberships`, and `payments` tables. Publishes Kafka events after payment confirmation.

## Port & Entry

- Port: **8083**
- Main class: `MembershipServiceApplication`
- Dependencies: PostgreSQL, Kafka (producer only)

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/plans` | List all active plans (public — no auth) |
| `GET` | `/api/memberships/my` | Get caller's active membership (null if none) |
| `GET` | `/api/memberships/my/all` | Get caller's full membership history |
| `GET` | `/api/payments/my` | Get caller's payment history |
| `POST` | `/api/payments/create-order` | Create Razorpay order + PENDING membership record |
| `POST` | `/api/payments/verify` | Verify payment signature → activate membership |

All protected endpoints read `X-User-Id` from the gateway-injected header.

## Payment Flow

**Step 1 — `create-order`:**
1. Validates plan exists; for `HALF_DAY` plans, requires `shift = MORNING` or `EVENING`
2. Creates `Membership` with `status = PENDING`, `startDate = today`, `endDate = today + plan.durationDays`
3. If `RAZORPAY_KEY_ID` is configured: creates real Razorpay order (amount in paise = price × 100)
4. If `RAZORPAY_KEY_ID` is blank: generates `dev_order_<8chars>` — no real payment gateway call
5. Creates `Payment` with `status = PENDING` and stores `gatewayOrderId`
6. Returns `orderId`, `membershipId`, `amount`, `razorpayKeyId` to frontend

**Step 2 — `verify`:**
1. HMAC-SHA256 signature check: `HMAC(orderId + "|" + paymentId, RAZORPAY_KEY_SECRET)` — skipped if order ID starts with `dev_` or if `razorpayKeySecret` is blank
2. Sets `Payment.status = SUCCESS` and `Membership.status = ACTIVE`
3. Publishes `booking-confirmed` Kafka event (topic: `booking-confirmed`, key: `userId`)

## Kafka Event

`BookingConfirmedEvent` published on `booking-confirmed` after successful payment:
- Contains `userId`, `membershipId`, `planName`, `planType`, `seatNumber`, `shift`, `startDate`, `endDate`, `amountPaid`
- Does **not** include `userName`/`userMobile`/`userEmail` — notification-service fetches those separately via `GET /api/users/{userId}` to avoid coupling this service to user-service

## Plan Types & Shifts

- `FULL_DAY` plan → `shift` is automatically set to `"FULL_DAY"` regardless of what the request sends
- `HALF_DAY` plan → `shift` must be `"MORNING"` or `"EVENING"` — validation throws `IllegalArgumentException` otherwise

## `MembershipService` — Notable Behaviour

`getUserActiveMembership()` returns `null` (not a 404) when the student has no active membership. The frontend checks for `null` and renders a "Get a plan" CTA. Do not change this to throw an exception.

## Key Config

| Property | Env var | Default |
|---|---|---|
| `razorpay.key-id` | `RAZORPAY_KEY_ID` | _(blank = dev mode)_ |
| `razorpay.key-secret` | `RAZORPAY_KEY_SECRET` | _(blank)_ |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `library-kafka:9092` |

Note: the default Kafka bootstrap server in this service's `application.yml` is `library-kafka:9092` (the Kubernetes service name), not `localhost:9092`. Change `KAFKA_BOOTSTRAP_SERVERS` when running locally.
