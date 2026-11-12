DELETE FROM auth_sessions;
ALTER TABLE auth_sessions ADD COLUMN two_factor_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE auth_sessions ADD COLUMN code_hash VARCHAR(255);
ALTER TABLE auth_sessions ADD COLUMN code_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auth_sessions ADD COLUMN resend_available_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE auth_sessions ADD COLUMN code_attempts INTEGER NOT NULL DEFAULT 0;
