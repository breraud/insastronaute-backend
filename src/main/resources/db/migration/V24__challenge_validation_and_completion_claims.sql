UPDATE challenges
SET validation_mode = CASE
    WHEN UPPER(TRIM(validation_mode)) = 'AUTO' THEN 'AUTO'
    ELSE 'ADMIN'
END
WHERE validation_mode <> CASE
    WHEN UPPER(TRIM(validation_mode)) = 'AUTO' THEN 'AUTO'
    ELSE 'ADMIN'
END;

UPDATE challenges
SET points_reward = 0
WHERE points_reward < 0;

UPDATE challenges
SET global_quota = 0
WHERE global_quota < 0;

UPDATE challenges
SET end_date = start_date
WHERE start_date IS NOT NULL
  AND end_date IS NOT NULL
  AND end_date < start_date;

ALTER TABLE challenges
    ADD CONSTRAINT ck_challenges_validation_mode
        CHECK (validation_mode IN ('ADMIN', 'AUTO')),
    ADD CONSTRAINT ck_challenges_points_reward_non_negative
        CHECK (points_reward >= 0),
    ADD CONSTRAINT ck_challenges_global_quota_non_negative
        CHECK (global_quota IS NULL OR global_quota >= 0),
    ADD CONSTRAINT ck_challenges_date_window
        CHECK (start_date IS NULL OR end_date IS NULL OR end_date >= start_date);

CREATE TABLE challenge_completion_claims (
    challenge_id UUID NOT NULL,
    user_id UUID NOT NULL,
    submission_id UUID NOT NULL,
    PRIMARY KEY (challenge_id, user_id),
    CONSTRAINT fk_challenge_completion_claims_challenge
        FOREIGN KEY (challenge_id)
        REFERENCES challenges(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_challenge_completion_claims_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_challenge_completion_claims_submission
        FOREIGN KEY (submission_id)
        REFERENCES challenge_submissions(id)
        ON DELETE CASCADE
);

INSERT INTO challenge_completion_claims (challenge_id, user_id, submission_id)
SELECT claim.challenge_id,
       claim.user_id,
       claim.submission_id
FROM (
    SELECT challenge_id, user_id, id AS submission_id
    FROM challenge_submissions
    WHERE status = 'VALIDATED'

    UNION ALL

    SELECT submission.challenge_id, participant.user_id, submission.id AS submission_id
    FROM challenge_submissions submission
    JOIN challenge_submission_participants participant
      ON participant.submission_id = submission.id
    WHERE submission.status = 'VALIDATED'
) claim;

CREATE OR REPLACE FUNCTION sync_challenge_completion_claims()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM challenge_completion_claims
        WHERE submission_id = OLD.id;
        RETURN OLD;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        DELETE FROM challenge_completion_claims
        WHERE submission_id = OLD.id;
    END IF;

    IF NEW.status = 'VALIDATED' THEN
        INSERT INTO challenge_completion_claims (challenge_id, user_id, submission_id)
        VALUES (NEW.challenge_id, NEW.user_id, NEW.id);

        INSERT INTO challenge_completion_claims (challenge_id, user_id, submission_id)
        SELECT NEW.challenge_id, participant.user_id, NEW.id
        FROM challenge_submission_participants participant
        WHERE participant.submission_id = NEW.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_challenge_completion_claims_submissions
AFTER INSERT OR UPDATE OF status, challenge_id, user_id OR DELETE
ON challenge_submissions
FOR EACH ROW
EXECUTE FUNCTION sync_challenge_completion_claims();

CREATE OR REPLACE FUNCTION sync_challenge_completion_participant_claims()
RETURNS TRIGGER AS $$
DECLARE
    submission_challenge_id UUID;
    submission_status VARCHAR(20);
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM challenge_completion_claims
        WHERE submission_id = OLD.submission_id
          AND user_id = OLD.user_id;
        RETURN OLD;
    END IF;

    SELECT challenge_id, status
    INTO submission_challenge_id, submission_status
    FROM challenge_submissions
    WHERE id = NEW.submission_id;

    IF submission_status = 'VALIDATED' THEN
        INSERT INTO challenge_completion_claims (challenge_id, user_id, submission_id)
        VALUES (submission_challenge_id, NEW.user_id, NEW.submission_id);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_challenge_completion_claims_participants
AFTER INSERT OR DELETE
ON challenge_submission_participants
FOR EACH ROW
EXECUTE FUNCTION sync_challenge_completion_participant_claims();
