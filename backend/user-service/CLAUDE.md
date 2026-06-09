# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

Manages student profile data and photo uploads. Owns its own read/write access to the `users` table (same DB as auth-service, different service boundary). Contains no auth logic — identity comes from `X-User-Id` / `X-User-Role` headers injected by the API Gateway.

## Port & Entry

- Port: **8082**
- Main class: `UserServiceApplication`
- Dependencies: PostgreSQL, filesystem (PersistentVolumeClaim in Kubernetes at `/app/uploads`)

## Build & Run

```bash
./mvnw clean package -DskipTests
./mvnw spring-boot:run
```

## Endpoints

All routes are protected by `AuthFilter` in the gateway. `X-User-Id` is always present when these handlers are reached.

| Method | Path | Who can call | Description |
|---|---|---|---|
| `GET` | `/api/users/me` | Any authenticated user | Get own profile |
| `PATCH` | `/api/users/me` | Any authenticated user | Update own profile |
| `POST` | `/api/users/me/photo` | Any authenticated user | Upload profile photo (`multipart/form-data`) |
| `DELETE` | `/api/users/me/photo` | Any authenticated user | Remove profile photo |
| `GET` | `/api/users/{userId}` | Any authenticated user | Get any user by ID (used by notification-service lookup) |

## Photo Upload

- Accepted MIME types: `image/jpeg`, `image/png`, `image/webp` (configured via `app.upload.allowed-types`)
- Max size: **5 MB** (enforced in `UserService.uploadPhoto`)
- Files stored at `${UPLOAD_DIR}/photos/user_<userId>_<8-char-uuid>.<ext>`
- Photo URL stored in `User.photoUrl` as `/uploads/photos/<filename>` — served directly by Nginx ingress
- Uploading a new photo deletes the previous file from disk before saving the new one
- `UPLOAD_DIR` must be the same path mounted by the PVC in Kubernetes (`uploads-pvc`, `ReadWriteMany`)

## Profile Update Rules

`UserService.updateProfile()` applies partial updates — only non-null/non-blank fields are changed. The email uniqueness check queries the DB and rejects the update if the new email belongs to a different user (`existing.getId() != user.getId()`). Date of birth must be `yyyy-MM-dd` format.

## Key Config

| Property | Env var | Default |
|---|---|---|
| `app.upload.dir` | `UPLOAD_DIR` | `/app/uploads` |
| `app.upload.allowed-types` | _(hardcoded in yml)_ | `image/jpeg,image/png,image/webp` |

## Shared Table

`user-service` and `auth-service` both map to the same `users` table in PostgreSQL. `auth-service` owns writes for `role`, `isActive`, `mobile`, `email`, and `name` at registration. `user-service` owns writes for `name`, `address`, `gender`, `dateOfBirth`, `email`, and `photoUrl` after registration. There is no cross-service ownership enforcement — coordinate carefully if changing column semantics.
