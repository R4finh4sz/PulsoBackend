-- A guardian process can start before the student informs the guardian (birth date corrected by the school).
ALTER TABLE guardian_confirmations ALTER COLUMN guardian_name DROP NOT NULL;
ALTER TABLE guardian_confirmations ALTER COLUMN guardian_email DROP NOT NULL;

ALTER TABLE registration_requests ADD COLUMN identity_checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE registration_requests ADD COLUMN birth_date_corrected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE registration_requests ADD COLUMN birth_date_corrected_by_id BIGINT REFERENCES school_users(id);
ALTER TABLE registration_requests ADD COLUMN birth_date_correction_reason VARCHAR(2000);
