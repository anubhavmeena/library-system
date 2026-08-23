-- Grace-dues and pending-fee reminder sends were switched from awaiting each
-- WhatsApp/email call in sequence (which could run past the frontend's 30s
-- axios timeout once there were enough recipients) to firing them off in
-- background tasks so the HTTP response returns immediately. This table is
-- the only place that background outcome is recorded, so the admin panel can
-- show "last sent: <time> — N/M delivered" after the fact instead of just an
-- immediate "sending..." toast.
CREATE TABLE IF NOT EXISTS reminder_jobs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type       VARCHAR(32) NOT NULL,
    total_count    INT NOT NULL,
    success_count  INT NOT NULL DEFAULT 0,
    failure_count  INT NOT NULL DEFAULT 0,
    status         VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at     TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at   TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reminder_jobs_type_started ON reminder_jobs (job_type, started_at DESC);
