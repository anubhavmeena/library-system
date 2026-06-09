# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Handles all authentication: OTP generation/verification via Twilio SMS, user registration, student login, admin login, and JWT issuance. Owns the `users` table.

## Port & Entry

- Port: **8081**
- Main class: `AuthServiceApplication`
- Dependencies: PostgreSQL (users), Redis (OTP + session tokens)

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/send-otp` | Public | Send OTP to mobile or email |
| `POST` | `/api/auth/verify-otp` | Public | Verify OTP → returns `sessionToken` + `isNewUser` |
| `POST` | `/api/auth/register` | Public | Register new student (requires valid `sessionToken`) |
| `POST` | `/api/auth/login` | Public | Student OTP login (requires verified OTP still in Redis) |
| `POST` | `/api/auth/admin/login` | Public | Admin login — no OTP, contact + role check only |
| `POST` | `/api/auth/refresh` | Public | **Not implemented** — throws `UnsupportedOperationException` |

## OTP Flow (2 Redis keys)

1. `send-otp` → stores `otp:<contact>` in Redis with **5-minute TTL**
2. `verify-otp` → reads & deletes `otp:<contact>`, checks match, creates `session:<token>` with **15-minute TTL**, returns `sessionToken` + `isNewUser` flag
3. `register` → reads & deletes `session:<token>`, creates `User`, issues JWT
4. `login` → re-reads `otp:<contact>` (still present from step 1 if not yet consumed), deletes it, issues JWT

The login flow deliberately re-uses the OTP from Redis rather than requiring a second `send-otp` call — the frontend calls `send-otp` → `verify-otp` → `login` without a second send.

## Dev Mode

`AuthService.generateOtp()` returns `"123456"` when `SPRING_PROFILES_ACTIVE=dev`.

`OtpService.sendSms()` logs the OTP to the console instead of sending when `TWILIO_ACCOUNT_SID` is blank — no code change needed for local dev.

Email OTP delivery is not implemented — the `contactType=EMAIL` path only logs to console regardless of environment.

## Admin Login

`POST /api/auth/admin/login` does **not** use OTP. It looks up the user by mobile/email, asserts `role == ADMIN`, and issues a JWT. There is no password field — admins are seeded directly in the DB.

## JWT Structure

Signed with HMAC-SHA256 using `JWT_SECRET`. Claims:
```
sub   → user UUID (string)
role  → "STUDENT" or "ADMIN"
name  → display name
email → email (may be null)
mobile → mobile (may be null)
```
Expiry: 24 hours (`86400000 ms`). The API Gateway parses this same token — both services must share the identical `JWT_SECRET`.

## Key Config

| Property | Env var | Default |
|---|---|---|
| `app.jwt.secret` | `JWT_SECRET` | `library-jwt-secret-key-2024-change-in-production` |
| `twilio.account-sid` | `TWILIO_ACCOUNT_SID` | _(blank = dev mode)_ |
| `twilio.auth-token` | `TWILIO_AUTH_TOKEN` | _(blank)_ |
| `twilio.phone-number` | `TWILIO_PHONE_NUMBER` | _(blank)_ |
| `spring.data.redis.host` | `REDIS_HOST` | `localhost` |

## Mobile Number Formatting

`OtpService.sendSms()` auto-prefixes `+91` if the number doesn't start with `+`. Numbers should be stored and passed as 10-digit strings without country code for Indian numbers.
