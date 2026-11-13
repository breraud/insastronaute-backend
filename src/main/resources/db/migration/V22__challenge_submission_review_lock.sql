ALTER TABLE challenge_submissions
    ADD COLUMN review_locked_by VARCHAR(255),
    ADD COLUMN review_started_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_challenge_submissions_review_lock
    ON challenge_submissions (review_locked_by, review_started_at);
