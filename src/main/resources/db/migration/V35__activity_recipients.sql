ALTER TABLE quiz_activities ADD COLUMN recipient_history_complete BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE activity_recipients (
    activity_id BIGINT NOT NULL REFERENCES quiz_activities(id),
    student_id BIGINT NOT NULL REFERENCES school_users(id),
    PRIMARY KEY (activity_id, student_id)
);
CREATE INDEX idx_activity_recipients_student ON activity_recipients(student_id);

-- Entregas comprovam recebimento; os demais destinatários antigos são desconhecidos.
INSERT INTO activity_recipients (activity_id, student_id)
SELECT activity_id, student_id FROM quiz_submissions;
