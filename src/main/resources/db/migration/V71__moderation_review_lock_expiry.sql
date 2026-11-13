ALTER TABLE challenge_submissions
    ADD COLUMN review_expires_at TIMESTAMP WITH TIME ZONE;

UPDATE challenge_submissions
SET review_expires_at = review_started_at + INTERVAL '15 minutes'
WHERE review_locked_by IS NOT NULL AND review_started_at IS NOT NULL;
