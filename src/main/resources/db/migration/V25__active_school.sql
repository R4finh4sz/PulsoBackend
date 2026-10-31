CREATE TABLE user_schools (
    user_id BIGINT NOT NULL REFERENCES school_users(id),
    school_id BIGINT NOT NULL REFERENCES schools(id),
    PRIMARY KEY (user_id, school_id)
);
CREATE INDEX idx_user_schools_school ON user_schools(school_id);
INSERT INTO user_schools (user_id, school_id)
SELECT id, school_id FROM school_users WHERE school_id IS NOT NULL;

ALTER TABLE school_users ADD COLUMN active_school_id BIGINT REFERENCES schools(id);
UPDATE school_users SET active_school_id = school_id;
