-- notification_logs existed since 001 but nothing wrote to it. It now records
-- every student-facing notification for the admin Reminders-tab and Student
-- page send logs, which need to distinguish scheduler sends from ones an
-- admin explicitly triggered, and by whom.
ALTER TABLE notification_logs ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(10) NOT NULL DEFAULT 'AUTO';  -- AUTO | MANUAL
ALTER TABLE notification_logs ADD COLUMN IF NOT EXISTS sent_by      TEXT;                                -- admin name for MANUAL

CREATE INDEX IF NOT EXISTS idx_notification_logs_user_sent  ON notification_logs (user_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_notification_logs_event_sent ON notification_logs (event, sent_at DESC);
