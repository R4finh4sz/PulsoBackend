UPDATE school_users SET active_school_id = school_id WHERE role = 'STUDENT';

DELETE FROM user_schools
WHERE user_id IN (SELECT id FROM school_users WHERE role = 'STUDENT')
  AND NOT EXISTS (
    SELECT 1 FROM school_users u
    WHERE u.id = user_schools.user_id AND u.school_id = user_schools.school_id
  );

INSERT INTO user_schools (user_id, school_id)
SELECT id, school_id FROM school_users
WHERE role = 'STUDENT' AND school_id IS NOT NULL
ON CONFLICT DO NOTHING;
