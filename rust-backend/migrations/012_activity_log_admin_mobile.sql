-- The activity log's "Admin" column shows the admin's mobile number
-- alongside their name, so a shared name (e.g. multiple "Admin" accounts)
-- can still be told apart.
ALTER TABLE activity_log ADD COLUMN IF NOT EXISTS admin_mobile TEXT;
