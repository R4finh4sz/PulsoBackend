ALTER TABLE school_users ADD COLUMN last_authenticated_at TIMESTAMP WITH TIME ZONE;
-- No complete activity history exists: give existing accounts a full six-month window.
UPDATE school_users SET last_authenticated_at = CURRENT_TIMESTAMP WHERE deleted_at IS NULL;
