-- Users accept the privacy policy together with the terms of use. Versions refer to legal_documents.version
-- of the PRIVACY_POLICY type.
ALTER TABLE registration_requests ADD COLUMN privacy_version INT;

CREATE TABLE user_privacy_acceptances (
    user_id BIGINT NOT NULL REFERENCES school_users(id) ON DELETE CASCADE,
    version INT NOT NULL,
    PRIMARY KEY (user_id, version)
);
