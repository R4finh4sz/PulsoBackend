ALTER TABLE school_users ADD COLUMN first_login BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE school_users SET first_login = TRUE WHERE role <> 'ADMIN';
