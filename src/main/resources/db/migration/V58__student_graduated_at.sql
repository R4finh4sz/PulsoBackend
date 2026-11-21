ALTER TABLE school_users ADD COLUMN graduated_at TIMESTAMP WITH TIME ZONE;
UPDATE school_users u SET graduated_at = g.performed_at
FROM (SELECT s.student_id, MAX(d.performed_at) AS performed_at
      FROM classroom_deliberation_students s
      JOIN classroom_deliberations d ON d.id = s.deliberation_id
      WHERE d.action = 'GRADUATE' GROUP BY s.student_id) g
WHERE u.id = g.student_id AND u.role = 'STUDENT' AND u.classroom_id IS NULL;
