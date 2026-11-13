CREATE TABLE challenge_submission_messages (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES challenge_submissions(id) ON DELETE CASCADE,
    author_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    author_keycloak_sub VARCHAR(255) NOT NULL,
    author_display_name VARCHAR(255) NOT NULL,
    author_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_challenge_submission_messages_author_type
        CHECK (author_type IN ('PLAYER', 'MODERATOR'))
);

CREATE INDEX idx_challenge_submission_messages_submission_created
    ON challenge_submission_messages (submission_id, created_at);
