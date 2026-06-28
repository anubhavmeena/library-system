-- Go backend migrations — safe to run multiple times (all idempotent)

-- Backfill NULLs in timestamp columns so Go's time.Time can scan them
UPDATE users        SET created_at = now() WHERE created_at IS NULL;
UPDATE users        SET updated_at = now() WHERE updated_at IS NULL;
UPDATE memberships  SET created_at = now() WHERE created_at IS NULL;
UPDATE payments     SET created_at = now() WHERE created_at IS NULL;
UPDATE payments     SET updated_at = now() WHERE updated_at IS NULL;
UPDATE feedbacks    SET created_at = now() WHERE created_at IS NULL;
UPDATE feedbacks    SET updated_at = now() WHERE updated_at IS NULL;
UPDATE seat_bookings SET created_at = now() WHERE created_at IS NULL;

ALTER TABLE users         ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE payments      ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE payments      ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE memberships   ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE feedbacks     ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE feedbacks     ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE seat_bookings ALTER COLUMN created_at SET DEFAULT now();

-- payments: pending_amount column (Go services track partial cash payments)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pending_amount NUMERIC(10,2) NOT NULL DEFAULT 0;

-- gallery_photos (user-service)
CREATE TABLE IF NOT EXISTS gallery_photos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url         VARCHAR(255) NOT NULL,
    caption     VARCHAR(255),
    uploaded_by UUID,
    uploaded_at TIMESTAMP
);

-- visitor_events (admin-service dashboard)
CREATE TABLE IF NOT EXISTS visitor_events (
    id         BIGSERIAL PRIMARY KEY,
    page       VARCHAR(255),
    created_at TIMESTAMP
);

-- monthly_expenses (admin-service)
CREATE TABLE IF NOT EXISTS monthly_expenses (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    year               INTEGER       NOT NULL,
    month              INTEGER       NOT NULL,
    water_tanker_qty   INTEGER       NOT NULL DEFAULT 0,
    water_tanker_price NUMERIC(10,2) NOT NULL DEFAULT 0,
    electricity_bill   NUMERIC(10,2) NOT NULL DEFAULT 0,
    internet_bill      NUMERIC(10,2) NOT NULL DEFAULT 0,
    miscellaneous      NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at         TIMESTAMP,
    updated_at         TIMESTAMP,
    UNIQUE (year, month)
);

-- misc_expense_items (admin-service — itemised misc expenses)
CREATE TABLE IF NOT EXISTS misc_expense_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    monthly_expense_id UUID NOT NULL REFERENCES monthly_expenses(id) ON DELETE CASCADE,
    description       VARCHAR(255) NOT NULL,
    amount            NUMERIC(10,2) NOT NULL DEFAULT 0,
    sort_order        INTEGER NOT NULL DEFAULT 0
);
