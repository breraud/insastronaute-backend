CREATE TABLE proof_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    challenge_id UUID NOT NULL REFERENCES challenges(id) ON DELETE CASCADE,
    proof_type VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_proof_drafts_type CHECK (proof_type IS NULL OR proof_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_proof_drafts_status CHECK (status IN ('DRAFT', 'ATTACHED', 'FAILED'))
);

CREATE UNIQUE INDEX uk_proof_drafts_one_active_draft
    ON proof_drafts (user_id, challenge_id)
    WHERE status = 'DRAFT';

ALTER TABLE proof_uploads
    ADD COLUMN draft_id UUID REFERENCES proof_drafts(id) ON DELETE CASCADE;

ALTER TABLE proof_uploads
    DROP CONSTRAINT ck_proof_uploads_status;

ALTER TABLE proof_uploads
    ADD CONSTRAINT ck_proof_uploads_status
        CHECK (status IN ('UPLOADING', 'DRAFT', 'ATTACHED', 'FAILED'));

INSERT INTO proof_drafts (user_id, challenge_id, proof_type, status, created_at, updated_at)
SELECT user_id,
       challenge_id,
       MIN(proof_type),
       status,
       MIN(created_at),
       now()
FROM proof_uploads
WHERE draft_id IS NULL
GROUP BY user_id, challenge_id, status;

UPDATE proof_uploads pu
SET draft_id = pd.id
FROM proof_drafts pd
WHERE pu.draft_id IS NULL
  AND pu.user_id = pd.user_id
  AND pu.challenge_id = pd.challenge_id
  AND pu.status = pd.status;

ALTER TABLE proof_uploads
    ALTER COLUMN draft_id SET NOT NULL;

CREATE INDEX idx_proof_uploads_draft_status
    ON proof_uploads (draft_id, status);
