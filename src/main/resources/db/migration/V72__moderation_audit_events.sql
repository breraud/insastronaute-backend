CREATE TABLE moderation_audit_events (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES challenge_submissions(id),
    moderator_hash VARCHAR(64) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    metadata_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_moderation_audit_submission_created
    ON moderation_audit_events(submission_id, created_at);
