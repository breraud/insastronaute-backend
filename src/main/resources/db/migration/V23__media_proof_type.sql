UPDATE challenge_submissions
SET proof_type = 'MEDIA'
WHERE proof_type IN ('IMAGE', 'VIDEO');

ALTER TABLE proof_uploads
    DROP CONSTRAINT ck_proof_uploads_type;

UPDATE proof_uploads
SET proof_type = 'MEDIA'
WHERE proof_type IN ('IMAGE', 'VIDEO');

ALTER TABLE proof_uploads
    ADD CONSTRAINT ck_proof_uploads_type
        CHECK (proof_type = 'MEDIA');

ALTER TABLE proof_drafts
    DROP CONSTRAINT ck_proof_drafts_type;

UPDATE proof_drafts
SET proof_type = 'MEDIA'
WHERE proof_type IN ('IMAGE', 'VIDEO');

ALTER TABLE proof_drafts
    ADD CONSTRAINT ck_proof_drafts_type
        CHECK (proof_type IS NULL OR proof_type = 'MEDIA');
