CREATE TABLE terms_versions (
    version BIGINT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL
);
INSERT INTO terms_versions (version, title, content)
SELECT version, title, content FROM terms_of_use WHERE version > 0;
CREATE TABLE user_terms_acceptances (
    user_id BIGINT NOT NULL REFERENCES school_users(id) ON DELETE CASCADE,
    version BIGINT NOT NULL REFERENCES terms_versions(version),
    PRIMARY KEY (user_id, version)
);
INSERT INTO user_terms_acceptances (user_id, version)
SELECT u.id, t.version FROM school_users u CROSS JOIN terms_of_use t
WHERE u.terms_accepted = TRUE AND t.version > 0;
UPDATE school_users SET terms_accepted = FALSE WHERE NOT EXISTS (SELECT 1 FROM terms_versions);
