ALTER TABLE challenges
    ADD COLUMN min_group_size INT NOT NULL DEFAULT 1;

UPDATE challenges
SET max_group_size = 1
WHERE max_group_size IS NULL;

ALTER TABLE challenges
    ALTER COLUMN max_group_size SET DEFAULT 1,
    ALTER COLUMN max_group_size SET NOT NULL,
    DROP COLUMN is_group_challenge,
    DROP COLUMN max_completions_per_user;

ALTER TABLE challenges
    ADD CONSTRAINT chk_challenge_group_sizes_positive
        CHECK (min_group_size >= 1 AND max_group_size >= min_group_size);

CREATE UNIQUE INDEX ux_challenge_submissions_validated_once
    ON challenge_submissions (challenge_id, user_id)
    WHERE status = 'VALIDATED';
