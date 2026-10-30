ALTER TABLE school_courses RENAME TO subjects;
ALTER TABLE subjects ADD COLUMN syllabus VARCHAR(5000) NOT NULL DEFAULT '';

CREATE TABLE subject_topics (
    subject_id BIGINT NOT NULL REFERENCES subjects(id),
    topic_order INTEGER NOT NULL,
    title VARCHAR(40) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    reference_text VARCHAR(4000) NOT NULL,
    PRIMARY KEY (subject_id, topic_order)
);

CREATE TABLE subject_themes (
    subject_id BIGINT NOT NULL REFERENCES subjects(id),
    name VARCHAR(40) NOT NULL,
    PRIMARY KEY (subject_id, name)
);
