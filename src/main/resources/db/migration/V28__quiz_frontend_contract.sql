ALTER TABLE quiz_activities ADD COLUMN closes_at TIMESTAMP WITH TIME ZONE;
-- Keep historical titles and descriptions while accepting the new payload without them.
ALTER TABLE quiz_activities ALTER COLUMN title DROP NOT NULL;
ALTER TABLE quiz_activities ALTER COLUMN description DROP NOT NULL;
ALTER TABLE quiz_questions ADD COLUMN theme VARCHAR(40);
