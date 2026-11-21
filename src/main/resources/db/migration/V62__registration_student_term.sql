ALTER TABLE registration_requests DROP CONSTRAINT registration_requests_terms_version_required;

ALTER TABLE registration_requests ADD CONSTRAINT registration_requests_terms_version_required
    CHECK (terms_version IS NOT NULL OR
        (role = 'STUDENT' AND student_term_version IS NOT NULL) OR
        (ra LIKE 'DEMO-%' AND email LIKE '%@example.invalid' AND status = 'APPROVED'));
