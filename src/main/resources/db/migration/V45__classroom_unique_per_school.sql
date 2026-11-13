ALTER TABLE classrooms DROP CONSTRAINT uk_classroom_name_identifier;
ALTER TABLE classrooms ADD CONSTRAINT uk_classroom_school_name_identifier
    UNIQUE (school_id, name, identifier);
