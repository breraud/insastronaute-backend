ALTER TABLE challenge_submission_participants
    ADD COLUMN confirmation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN invited_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN responded_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_challenge_submission_participants_confirmation
    ON challenge_submission_participants(submission_id, confirmation_status);
