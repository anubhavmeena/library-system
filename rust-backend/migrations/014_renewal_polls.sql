-- Tracks each "3-days-before-renewal" WhatsApp poll sent via the
-- seat_renewal_confirmation template: one row per send, so the admin panel
-- can show every poll ever sent plus its Yes/No response. An audit-log-style
-- table (modeled on notification_logs), not a boolean flag on memberships,
-- because a poll needs to track both "was it sent" and "how/when did they
-- respond" -- a single flag can't express that.
CREATE TABLE renewal_polls (
    id             UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    membership_id  UUID    NOT NULL REFERENCES memberships(id),
    user_id        UUID    NOT NULL REFERENCES users(id),
    -- Snapshot of memberships.end_date at send time. renew_seat/clear_dues
    -- extend end_date on the SAME membership row every cycle, so gating on
    -- membership_id alone would permanently block re-sending after cycle 1.
    -- Gate on the (membership_id, end_date) pair instead.
    end_date       DATE    NOT NULL,
    -- Meta's wamid, populated after the async send completes; NULL in dev
    -- mode (no META_WHATSAPP_TOKEN) or if the send fails. Correlation key
    -- for the inbound webhook reply (matched against context.id).
    wa_message_id  TEXT,
    sent_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    response       TEXT,     -- 'YES' | 'NO' | NULL (unanswered)
    responded_at   TIMESTAMP
);

CREATE INDEX idx_renewal_polls_wa_message_id ON renewal_polls(wa_message_id);
CREATE INDEX idx_renewal_polls_membership_end_date ON renewal_polls(membership_id, end_date);
