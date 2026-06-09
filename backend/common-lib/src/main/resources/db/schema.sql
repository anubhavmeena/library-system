-- =============================================================================
-- Target Zone Library — Database Schema
-- Executed once by PostgreSQL on first container initialisation (before any
-- Spring service starts). Hibernate ddl-auto: update is a safe no-op after this.
-- =============================================================================

-- users (shared by auth-service and user-service)
CREATE TABLE IF NOT EXISTS users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile        VARCHAR(255) UNIQUE,
    email         VARCHAR(255) UNIQUE,
    name          VARCHAR(255) NOT NULL,
    address       TEXT,
    father_name   VARCHAR(255),
    photo_url     VARCHAR(255),
    date_of_birth DATE,
    gender        VARCHAR(50),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    role          VARCHAR(50) NOT NULL DEFAULT 'STUDENT',
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);

-- membership_plans (owned by membership-service)
CREATE TABLE IF NOT EXISTS membership_plans (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255)  NOT NULL,
    plan_type     VARCHAR(50)   NOT NULL,    -- HALF_DAY | FULL_DAY
    price         NUMERIC(10,2) NOT NULL,
    duration_days INTEGER       NOT NULL,
    description   TEXT,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE
);

-- memberships (owned by membership-service)
CREATE TABLE IF NOT EXISTS memberships (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL,
    plan_id       UUID NOT NULL REFERENCES membership_plans(id),
    seat_id       UUID,
    seat_number   VARCHAR(10),
    shift         VARCHAR(20),               -- MORNING | EVENING | FULL_DAY
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP
);

-- payments (owned by membership-service)
CREATE TABLE IF NOT EXISTS payments (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id       UUID NOT NULL,
    user_id             UUID NOT NULL,
    amount              NUMERIC(10,2) NOT NULL,
    payment_gateway     VARCHAR(50)  DEFAULT 'RAZORPAY',
    gateway_order_id    VARCHAR(255),
    gateway_payment_id  VARCHAR(255),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMP,
    updated_at          TIMESTAMP
);

-- seats (owned by seat-service)
CREATE TABLE IF NOT EXISTS seats (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_number VARCHAR(10) UNIQUE NOT NULL,
    row_label   VARCHAR(5)  NOT NULL,
    seat_index  INTEGER     NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE
);

-- seat_bookings (owned by seat-service)
CREATE TABLE IF NOT EXISTS seat_bookings (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_id       UUID NOT NULL REFERENCES seats(id),
    user_id       UUID NOT NULL,
    membership_id UUID NOT NULL,
    shift         VARCHAR(20) NOT NULL,
    booking_date  DATE NOT NULL,
    end_date      DATE NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP,
    UNIQUE (seat_id, shift, booking_date)
);

-- notification_logs (owned by notification-service)
CREATE TABLE IF NOT EXISTS notification_logs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID,
    recipient     VARCHAR(255),
    message       TEXT,
    event         VARCHAR(100),
    channel       VARCHAR(20),    -- EMAIL | WHATSAPP | SMS
    status        VARCHAR(20),    -- PENDING | SENT | FAILED
    error_message TEXT,
    sent_at       TIMESTAMP,
    created_at    TIMESTAMP
);
