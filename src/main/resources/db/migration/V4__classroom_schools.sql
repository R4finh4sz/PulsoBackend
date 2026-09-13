ALTER TABLE classrooms ADD COLUMN school_id BIGINT REFERENCES schools(id);
CREATE INDEX idx_classrooms_school ON classrooms(school_id);

UPDATE classrooms c
SET school_id = COALESCE(
    (SELECT MIN(u.school_id) FROM school_users u
     WHERE u.classroom_id = c.id AND u.school_id IS NOT NULL),
    (SELECT MIN(u.school_id) FROM school_users u
     JOIN classroom_teachers ct ON ct.teacher_id = u.id
     WHERE ct.classroom_id = c.id AND u.school_id IS NOT NULL)
)
WHERE c.school_id IS NULL;