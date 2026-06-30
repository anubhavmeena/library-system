-- Add father_name to users (may be absent if table was created by Java backend)
ALTER TABLE users ADD COLUMN IF NOT EXISTS father_name TEXT;

-- Ensure seat_number exists in memberships (Java seat-service may not have stored it here)
ALTER TABLE memberships ADD COLUMN IF NOT EXISTS seat_number TEXT;
