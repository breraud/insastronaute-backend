ALTER TABLE challenge_submissions
    ADD COLUMN bonus_points INT NOT NULL DEFAULT 0;

ALTER TABLE challenge_submissions
    ADD CONSTRAINT ck_challenge_submissions_bonus_points_non_negative
        CHECK (bonus_points >= 0);
