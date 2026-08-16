-- Audit trail of every mutating action an admin takes from the admin panel
-- (student CRUD, seat/membership changes, payments cleared, broadcasts,
-- settings, etc.) — backs the admin "Activity Logs" page.
CREATE TABLE IF NOT EXISTS activity_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id    UUID        NOT NULL REFERENCES users(id),
    admin_name  TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    entity_type TEXT,
    entity_id   TEXT,
    description TEXT        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_activity_log_created_at ON activity_log (created_at DESC);
