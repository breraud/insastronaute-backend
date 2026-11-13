CREATE TABLE proof_uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    object_key TEXT NOT NULL UNIQUE,
    proof_type VARCHAR(20) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_proof_uploads_type CHECK (proof_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_proof_uploads_status CHECK (status IN ('DRAFT', 'ATTACHED')),
    CONSTRAINT ck_proof_uploads_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_proof_uploads_user_challenge_status
    ON proof_uploads (user_id, challenge_id, status);
