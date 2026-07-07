-- Membership grace/dues workflow: a GRACE-status membership tracks how much
-- is owed (dues_amount) and the in-flight checkout used to pay it off
-- (gateway_order_id/checkout_amount), mirroring the Java backend's schema.
ALTER TABLE memberships
    ADD COLUMN IF NOT EXISTS dues_amount      NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS gateway_order_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS checkout_amount  NUMERIC(10,2);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS invoice_id VARCHAR(64);

-- Singleton row (id always 1) holding admin-configurable settings.
CREATE TABLE IF NOT EXISTS app_settings (
    id                BIGINT PRIMARY KEY DEFAULT 1,
    wifi_name         VARCHAR(255),
    wifi_password     VARCHAR(255),
    grace_days        INT NOT NULL DEFAULT 10,
    convenience_fee   NUMERIC(10,2) NOT NULL DEFAULT 0,
    water_tanker_rate NUMERIC(10,2) NOT NULL DEFAULT 0,
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT app_settings_singleton CHECK (id = 1)
);

-- One row per notification key, lazily created from the in-code catalog.
CREATE TABLE IF NOT EXISTS notification_settings (
    notification_key   VARCHAR(64) PRIMARY KEY,
    send_to_student     BOOLEAN NOT NULL DEFAULT TRUE,
    send_to_admin       BOOLEAN NOT NULL DEFAULT TRUE,
    hindi_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    hindi_text_student  TEXT,
    hindi_text_admin    TEXT,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);
