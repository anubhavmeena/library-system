# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Spring Cloud Gateway — the single entry point for all HTTP traffic. Validates JWTs, injects user identity headers, enforces admin role, and proxies requests to downstream services. This service contains **no business logic and no database**.

## Port & Entry

- Port: **8080**
- Main class: `ApiGatewayApplication`
- Reactive (WebFlux-based) — do not add `spring-boot-starter-web` or blocking code here.

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Route Table

Defined entirely in `application.yml`. All routes use `StripPrefix=0` (paths are forwarded as-is).

| Route ID | Path prefix | Target env var | Auth |
|---|---|---|---|
| `auth-service-public` | `/api/auth/**` | `AUTH_SERVICE_URL` | None |
| `user-service` | `/api/users/**` | `USER_SERVICE_URL` | `AuthFilter` |
| `membership-service` | `/api/memberships/**, /api/plans/**, /api/payments/**` | `MEMBERSHIP_SERVICE_URL` | `AuthFilter` |
| `seat-service` | `/api/seats/**` | `SEAT_SERVICE_URL` | `AuthFilter` |
| `admin-service` | `/api/admin/**` | `ADMIN_SERVICE_URL` | `AuthFilter` + `AdminRoleFilter` |

## Filters

### `AuthFilter`
- Runs on every route that declares `name: AuthFilter`.
- Hardcoded public paths that bypass the filter: `/api/auth/send-otp`, `/api/auth/verify-otp`, `/api/auth/register`, `/api/auth/login`, `/api/plans`.
- Parses the JWT with `jjwt` using the shared `JWT_SECRET`. Extracts `sub` (userId) and `role` claim.
- Mutates the request to add `X-User-Id` and `X-User-Role` headers before forwarding. Downstream services trust these headers without re-validating.
- Returns 401 on missing/invalid/expired token.

### `AdminRoleFilter`
- Runs only on `/api/admin/**`, after `AuthFilter` has already set `X-User-Role`.
- Reads `X-User-Role` from the (already-mutated) request and returns 403 if it is not `ADMIN`.

## Key Config

```yaml
app.jwt.secret        # Must match the secret used by auth-service to sign tokens
```

Default secret is `library-jwt-secret-key-2024-change-in-production` — override with `JWT_SECRET` env var. Both this service and `auth-service` must use the same value.

## CORS

Configured globally in `application.yml` under `spring.cloud.gateway.globalcors`. Allowed origins: `http://localhost:3000` and `http://frontend:3000`. To add a new origin, edit that YAML block.

## Adding a New Route

1. Add the route in `application.yml` with an `id`, `uri` (env var + localhost fallback), `predicates.Path`, and optionally `filters`.
2. If the route needs auth, add `- name: AuthFilter` to its filters list.
3. If the route is admin-only, additionally add `- name: AdminRoleFilter`.
4. Add the corresponding public path to `AuthFilter.PUBLIC_PATHS` if it must be unauthenticated.
