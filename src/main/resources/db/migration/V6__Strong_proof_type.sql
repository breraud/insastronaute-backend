ALTER TABLE challenge_submissions DROP COLUMN proof_text;
ALTER TABLE challenge_submissions ADD COLUMN proof_type VARCHAR(50) NOT NULL DEFAULT 'TEXT';
ALTER TABLE challenge_submissions ADD COLUMN proof_data TEXT NOT NULL DEFAULT '';