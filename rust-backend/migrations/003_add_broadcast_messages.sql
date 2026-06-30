-- Rust-only table: not present in the Java backend schema
CREATE TABLE IF NOT EXISTS broadcast_messages (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    message         TEXT        NOT NULL,
    recipient_count INTEGER     NOT NULL DEFAULT 0,
    sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
