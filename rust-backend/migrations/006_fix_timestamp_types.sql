-- Pre-existing schema/model mismatch: 001_initial.sql declared these columns
-- as TIMESTAMPTZ, but every corresponding Rust struct field is `NaiveDateTime`
-- (matching Java's plain-TIMESTAMP schema.sql, which this table set was meant
-- to mirror) — sqlx's chrono support only decodes NaiveDateTime from
-- TIMESTAMP, not TIMESTAMPTZ, so `SELECT *` on any of these tables errors out
-- against a byte-fresh database created from this backend's own migrations.
-- `USING col::timestamp` truncates the (implicit UTC) offset rather than
-- converting to local time, since these were always stored/read as naive.
ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp,
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at::timestamp;

ALTER TABLE memberships
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp;

ALTER TABLE payments
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp,
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at::timestamp;

ALTER TABLE seat_bookings
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp;

ALTER TABLE feedbacks
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp,
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at::timestamp;

ALTER TABLE gallery_photos
    ALTER COLUMN uploaded_at TYPE TIMESTAMP USING uploaded_at::timestamp;

ALTER TABLE notification_logs
    ALTER COLUMN sent_at TYPE TIMESTAMP USING sent_at::timestamp,
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp;

ALTER TABLE visitor_events
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp;

ALTER TABLE monthly_expenses
    ALTER COLUMN created_at TYPE TIMESTAMP USING created_at::timestamp,
    ALTER COLUMN updated_at TYPE TIMESTAMP USING updated_at::timestamp;

ALTER TABLE broadcast_messages
    ALTER COLUMN sent_at TYPE TIMESTAMP USING sent_at::timestamp;
