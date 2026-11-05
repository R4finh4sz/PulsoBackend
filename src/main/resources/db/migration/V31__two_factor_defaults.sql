ALTER TABLE school_users ALTER COLUMN two_factor_enabled SET DEFAULT FALSE;
UPDATE school_users SET two_factor_enabled = TRUE WHERE role = 'ADMIN';
