-- Keep existing quizzes in their original order, including attempts already in progress.
ALTER TABLE quiz_activities ADD COLUMN questions_shuffled BOOLEAN NOT NULL DEFAULT FALSE;
