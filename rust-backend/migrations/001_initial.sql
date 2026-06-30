-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ── Users ────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile        TEXT         UNIQUE,
    email         TEXT         UNIQUE,
    name          TEXT         NOT NULL DEFAULT '',
    address       TEXT,
    photo_url     TEXT,
    aadhaar_url   TEXT,
    date_of_birth DATE,
    gender        TEXT,
    password_hash TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    role          TEXT         NOT NULL DEFAULT 'STUDENT',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Membership plans ─────────────────────────────────────────────────────────
CREATE TABLE membership_plans (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name          TEXT         NOT NULL,
    plan_type     TEXT         NOT NULL,   -- HALF_DAY | FULL_DAY
    price         NUMERIC(10,2) NOT NULL,
    duration_days INTEGER      NOT NULL,
    description   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT true
);

-- ── Seats (110 physical seats) ───────────────────────────────────────────────
CREATE TABLE seats (
    id          UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_number TEXT    UNIQUE NOT NULL,
    row_label   TEXT    NOT NULL,
    seat_index  INTEGER NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true
);

-- ── Memberships ──────────────────────────────────────────────────────────────
CREATE TABLE memberships (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id),
    plan_id       UUID         NOT NULL REFERENCES membership_plans(id),
    seat_id       UUID         REFERENCES seats(id),
    seat_number   TEXT,
    shift         TEXT         NOT NULL,  -- MORNING | EVENING | FULL_DAY
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    status        TEXT         NOT NULL DEFAULT 'PENDING',  -- PENDING | ACTIVE | QUEUED | EXPIRED | CANCELLED
    reminder_sent BOOLEAN      NOT NULL DEFAULT false,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Payments ─────────────────────────────────────────────────────────────────
CREATE TABLE payments (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id      UUID          NOT NULL REFERENCES memberships(id),
    user_id            UUID          NOT NULL REFERENCES users(id),
    amount             NUMERIC(10,2) NOT NULL,
    pending_amount     NUMERIC(10,2),
    payment_gateway    TEXT          NOT NULL,  -- RAZORPAY | CASHFREE
    gateway_order_id   TEXT,
    gateway_payment_id TEXT,
    status             TEXT          NOT NULL DEFAULT 'PENDING',  -- PENDING | SUCCESS | FAILED | REFUNDED
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ── Seat bookings ────────────────────────────────────────────────────────────
CREATE TABLE seat_bookings (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_id       UUID        NOT NULL REFERENCES seats(id),
    user_id       UUID        NOT NULL REFERENCES users(id),
    membership_id UUID        NOT NULL REFERENCES memberships(id),
    shift         TEXT        NOT NULL,  -- MORNING | EVENING | FULL_DAY
    booking_date  DATE        NOT NULL,
    end_date      DATE        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | RELEASED | EXPIRED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (seat_id, shift, booking_date)
);

-- ── Feedback / complaints ────────────────────────────────────────────────────
CREATE TABLE feedbacks (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL REFERENCES users(id),
    feedback_type TEXT        NOT NULL,  -- FEEDBACK | COMPLAINT
    subject       TEXT        NOT NULL,
    description   TEXT        NOT NULL,
    status        TEXT        NOT NULL DEFAULT 'OPEN',  -- OPEN | UNDER_REVIEW | RESOLVED
    admin_notes   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Gallery ──────────────────────────────────────────────────────────────────
CREATE TABLE gallery_photos (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    url         TEXT        NOT NULL,
    caption     TEXT,
    uploaded_by TEXT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Notification audit log ───────────────────────────────────────────────────
CREATE TABLE notification_logs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID,
    recipient     TEXT        NOT NULL,
    message       TEXT        NOT NULL,
    event         TEXT        NOT NULL,
    channel       TEXT        NOT NULL,  -- EMAIL | WHATSAPP | SMS
    status        TEXT        NOT NULL,  -- SENT | FAILED
    error_message TEXT,
    sent_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Visitor analytics ────────────────────────────────────────────────────────
CREATE TABLE visitor_events (
    id         BIGSERIAL   PRIMARY KEY,
    page       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Monthly expenses ─────────────────────────────────────────────────────────
CREATE TABLE monthly_expenses (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    year                INTEGER       NOT NULL,
    month               INTEGER       NOT NULL,
    water_tanker_qty    INTEGER,
    water_tanker_price  NUMERIC(10,2),
    electricity_bill    NUMERIC(10,2),
    internet_bill       NUMERIC(10,2),
    miscellaneous       NUMERIC(10,2),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    UNIQUE (year, month)
);

CREATE TABLE misc_expense_items (
    id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    monthly_expense_id UUID          NOT NULL REFERENCES monthly_expenses(id) ON DELETE CASCADE,
    description        TEXT          NOT NULL,
    amount             NUMERIC(10,2) NOT NULL,
    sort_order         INTEGER
);

-- ── Broadcast messages ───────────────────────────────────────────────────────
CREATE TABLE broadcast_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    message         TEXT        NOT NULL,
    recipient_count INTEGER     NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX idx_users_mobile         ON users(mobile);
CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_role           ON users(role);
CREATE INDEX idx_memberships_user_id  ON memberships(user_id);
CREATE INDEX idx_memberships_status   ON memberships(status);
CREATE INDEX idx_memberships_end_date ON memberships(end_date);
CREATE INDEX idx_seat_bookings_seat   ON seat_bookings(seat_id);
CREATE INDEX idx_seat_bookings_date   ON seat_bookings(booking_date, end_date);
CREATE INDEX idx_payments_membership  ON payments(membership_id);
CREATE INDEX idx_payments_user        ON payments(user_id);
CREATE INDEX idx_feedbacks_user       ON feedbacks(user_id);

-- ── Seed: 110 seats ──────────────────────────────────────────────────────────
INSERT INTO seats (seat_number, row_label, seat_index, is_active) VALUES
  ('A1','A',1,true),('A2','A',2,true),('A3','A',3,true),('A4','A',4,true),
  ('A5','A',5,true),('A6','A',6,true),('A7','A',7,true),('A8','A',8,true),
  ('A9','A',9,true),('A10','A',10,true),('A11','A',11,true),('A12','A',12,true),
  ('A13','A',13,true),('A14','A',14,true),('A15','A',15,true),('A16','A',16,true),
  ('A17','A',17,true),('A18','A',18,true),('A19','A',19,true),('A20','A',20,true),
  ('A21','A',21,true),('A22','A',22,true),('A23','A',23,true),('A24','A',24,true),
  ('A25','A',25,true),('A26','A',26,true),('A27','A',27,true),('A28','A',28,true),
  ('B1','B',1,true),('B2','B',2,true),('B3','B',3,true),('B4','B',4,true),
  ('B5','B',5,true),('B6','B',6,true),('B7','B',7,true),('B8','B',8,true),
  ('B9','B',9,true),('B10','B',10,true),('B11','B',11,true),('B12','B',12,true),
  ('B13','B',13,true),('B14','B',14,true),('B15','B',15,true),('B16','B',16,true),
  ('B17','B',17,true),('B18','B',18,true),('B19','B',19,true),('B20','B',20,true),
  ('B21','B',21,true),('B22','B',22,true),('B23','B',23,true),('B24','B',24,true),
  ('B25','B',25,true),('B26','B',26,true),('B27','B',27,true),('B28','B',28,true),
  ('C1','C',1,true),('C2','C',2,true),('C3','C',3,true),('C4','C',4,true),
  ('C5','C',5,true),('C6','C',6,true),('C7','C',7,true),('C8','C',8,true),
  ('C9','C',9,true),('C10','C',10,true),('C11','C',11,true),('C12','C',12,true),
  ('C13','C',13,true),('C14','C',14,true),('C15','C',15,true),('C16','C',16,true),
  ('C17','C',17,true),('C18','C',18,true),('C19','C',19,true),('C20','C',20,true),
  ('C21','C',21,true),('C22','C',22,true),('C23','C',23,true),('C24','C',24,true),
  ('C25','C',25,true),('C26','C',26,true),('C27','C',27,true),('C28','C',28,true),
  ('D1','D',1,true),('D2','D',2,true),('D3','D',3,true),('D4','D',4,true),
  ('D5','D',5,true),('D6','D',6,true),('D7','D',7,true),('D8','D',8,true),
  ('D9','D',9,true),('D10','D',10,true),('D11','D',11,true),('D12','D',12,true),
  ('D13','D',13,true),('D14','D',14,true),('D15','D',15,true),('D16','D',16,true),
  ('D17','D',17,true),('D18','D',18,true),('D19','D',19,true),('D20','D',20,true),
  ('D21','D',21,true),('D22','D',22,true),('D23','D',23,true),('D24','D',24,true),
  ('D25','D',25,true),('D26','D',26,true);

-- ── Seed: default membership plans ───────────────────────────────────────────
INSERT INTO membership_plans (name, plan_type, price, duration_days, description, is_active) VALUES
  ('Morning 30 Days',  'HALF_DAY', 1200.00, 30,  '6 AM – 2 PM, 30 days',  true),
  ('Evening 30 Days',  'HALF_DAY', 1200.00, 30,  '2 PM – 10 PM, 30 days', true),
  ('Full Day 30 Days', 'FULL_DAY', 1800.00, 30,  '6 AM – 10 PM, 30 days', true),
  ('Morning 90 Days',  'HALF_DAY', 3200.00, 90,  '6 AM – 2 PM, 90 days',  true),
  ('Full Day 90 Days', 'FULL_DAY', 4800.00, 90,  '6 AM – 10 PM, 90 days', true);

-- ── Seed: admin user ─────────────────────────────────────────────────────────
-- Default admin: email=admin@targetzone.co.in  password=password
-- Hash is bcrypt cost=10 of the string "password". CHANGE THIS IN PRODUCTION.
-- To generate a new hash in Rust: bcrypt::hash("YourPassword", 10).unwrap()
INSERT INTO users (name, email, password_hash, role, is_active) VALUES
  ('Admin', 'admin@targetzone.co.in',
   '$2b$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi',
   'ADMIN', true);
