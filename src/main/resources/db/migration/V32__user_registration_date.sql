-- Existing users have no reliable registration timestamp; keep their date unknown.
ALTER TABLE school_users ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_users_created_at ON school_users(created_at);
