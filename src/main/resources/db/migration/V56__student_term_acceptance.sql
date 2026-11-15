ALTER TABLE registration_requests ADD COLUMN student_term_version INTEGER;
CREATE TABLE user_student_term_acceptances (
    user_id BIGINT NOT NULL REFERENCES school_users(id),
    version INTEGER NOT NULL,
    PRIMARY KEY (user_id, version)
);
-- General terms previously accepted are history, not acceptance of the student term.
UPDATE school_users SET terms_accepted = FALSE WHERE role = 'STUDENT' AND deleted_at IS NULL;
