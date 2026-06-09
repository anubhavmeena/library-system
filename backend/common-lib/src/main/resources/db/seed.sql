-- =============================================================================
-- Target Zone Library — Seed Data
-- Executed after schema.sql on first container initialisation.
-- All INSERTs are idempotent: fixed UUIDs + ON CONFLICT DO NOTHING.
-- UPDATE the admin mobile/email below before going live.
-- =============================================================================

-- ─── Admin user ──────────────────────────────────────────────────────────────
-- Admin login: POST /api/auth/admin/login with this mobile or email.
-- Dev mode OTP: 123456 (any input accepted when SPRING_PROFILES_ACTIVE=dev).
INSERT INTO users (id, mobile, email, name, role, is_active, created_at, updated_at)
VALUES (
    'a0000000-0000-0000-0000-000000000001',
    '9999999999',             -- TODO: replace with real admin mobile before going live
    'admin@targetzone.co.in', -- TODO: replace with real admin email before going live
    'Admin',
    'ADMIN',
    TRUE,
    NOW(),
    NOW()
) ON CONFLICT (id) DO NOTHING;

-- ─── Membership plans ────────────────────────────────────────────────────────
INSERT INTO membership_plans (id, name, plan_type, price, duration_days, description, is_active)
VALUES
    (
        'b0000000-0000-0000-0000-000000000001',
        'Half Day',
        'HALF_DAY',
        400.00,
        30,
        'Morning or Evening shift access, 30 days validity',
        TRUE
    ),
    (
        'b0000000-0000-0000-0000-000000000002',
        'Full Day',
        'FULL_DAY',
        600.00,
        30,
        'Full day access 6AM–10PM, 30 days validity',
        TRUE
    )
ON CONFLICT (id) DO NOTHING;

-- ─── Seats (110 total: A×28, B×28, C×28, D×26) ───────────────────────────────
INSERT INTO seats (id, seat_number, row_label, seat_index, is_active)
SELECT gen_random_uuid(), 'A' || s, 'A', s, TRUE
FROM generate_series(1, 28) s
ON CONFLICT (seat_number) DO NOTHING;

INSERT INTO seats (id, seat_number, row_label, seat_index, is_active)
SELECT gen_random_uuid(), 'B' || s, 'B', s, TRUE
FROM generate_series(1, 28) s
ON CONFLICT (seat_number) DO NOTHING;

INSERT INTO seats (id, seat_number, row_label, seat_index, is_active)
SELECT gen_random_uuid(), 'C' || s, 'C', s, TRUE
FROM generate_series(1, 28) s
ON CONFLICT (seat_number) DO NOTHING;

INSERT INTO seats (id, seat_number, row_label, seat_index, is_active)
SELECT gen_random_uuid(), 'D' || s, 'D', s, TRUE
FROM generate_series(1, 26) s
ON CONFLICT (seat_number) DO NOTHING;
