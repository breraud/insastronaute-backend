-- Legacy AUTO meant immediate validation on player submission. That behavior no
-- longer exists, so existing challenges are deliberately retained as ADMIN.
UPDATE challenges
SET validation_mode = 'ADMIN'
WHERE validation_mode = 'AUTO';

ALTER TABLE challenges ADD COLUMN auto_challenge_type VARCHAR(100);
ALTER TABLE challenges ADD COLUMN auto_challenge_parameters JSONB;
ALTER TABLE challenges ADD CONSTRAINT chk_challenge_auto_config CHECK (
  (validation_mode = 'ADMIN' AND auto_challenge_type IS NULL AND auto_challenge_parameters IS NULL)
  OR
  (validation_mode = 'AUTO' AND auto_challenge_type IS NOT NULL AND auto_challenge_parameters IS NOT NULL)
);

CREATE TABLE challenge_active_memberships (
  challenge_id UUID NOT NULL,
  user_id UUID NOT NULL,
  submission_id UUID NOT NULL,
  PRIMARY KEY (challenge_id, user_id),
  CONSTRAINT fk_challenge_active_memberships_challenge
    FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE CASCADE,
  CONSTRAINT fk_challenge_active_memberships_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_challenge_active_memberships_submission
    FOREIGN KEY (submission_id) REFERENCES challenge_submissions(id) ON DELETE CASCADE
);

-- Greedily keep blocking submissions in deterministic order. A losing
-- submission reserves no member, so later submissions are compared only with
-- winners that actually remain blocking.
DO $$
DECLARE
  blocking_submission RECORD;
  member_ids UUID[];
BEGIN
  FOR blocking_submission IN
    SELECT submission.id, submission.challenge_id
    FROM challenge_submissions submission
    WHERE submission.status IN ('WAITING_CONFIRMATION', 'PENDING', 'VALIDATED')
    ORDER BY submission.created_at, submission.id
  LOOP
    SELECT ARRAY(
      SELECT members.user_id
      FROM (
        SELECT submission.user_id
        FROM challenge_submissions submission
        WHERE submission.id = blocking_submission.id

        UNION

        SELECT participant.user_id
        FROM challenge_submission_participants participant
        WHERE participant.submission_id = blocking_submission.id
      ) members
      ORDER BY members.user_id
    ) INTO member_ids;

    IF EXISTS (
      SELECT 1
      FROM challenge_active_memberships membership
      WHERE membership.challenge_id = blocking_submission.challenge_id
        AND membership.user_id = ANY(member_ids)
    ) THEN
      UPDATE challenge_submissions
      SET status = 'REJECTED'
      WHERE id = blocking_submission.id;
    ELSE
      INSERT INTO challenge_active_memberships (challenge_id, user_id, submission_id)
      SELECT blocking_submission.challenge_id, member.user_id, blocking_submission.id
      FROM unnest(member_ids) AS member(user_id);
    END IF;
  END LOOP;
END;
$$;

CREATE INDEX idx_challenge_submission_active_owner
ON challenge_submissions(challenge_id, user_id)
WHERE status IN ('WAITING_CONFIRMATION', 'PENDING', 'VALIDATED');

CREATE OR REPLACE FUNCTION sync_challenge_active_submission_memberships()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM challenge_active_memberships WHERE submission_id = OLD.id;
    RETURN OLD;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    DELETE FROM challenge_active_memberships WHERE submission_id = OLD.id;
  END IF;

  IF NEW.status IN ('WAITING_CONFIRMATION', 'PENDING', 'VALIDATED') THEN
    INSERT INTO challenge_active_memberships (challenge_id, user_id, submission_id)
    VALUES (NEW.challenge_id, NEW.user_id, NEW.id);

    INSERT INTO challenge_active_memberships (challenge_id, user_id, submission_id)
    SELECT NEW.challenge_id, participant.user_id, NEW.id
    FROM challenge_submission_participants participant
    WHERE participant.submission_id = NEW.id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_challenge_active_memberships_submissions
AFTER INSERT OR UPDATE OF status, challenge_id, user_id OR DELETE
ON challenge_submissions
FOR EACH ROW
EXECUTE FUNCTION sync_challenge_active_submission_memberships();

CREATE OR REPLACE FUNCTION sync_challenge_active_participant_memberships()
RETURNS TRIGGER AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    DELETE FROM challenge_active_memberships
    WHERE submission_id = OLD.submission_id
      AND user_id = OLD.user_id;
    RETURN OLD;
  END IF;

  INSERT INTO challenge_active_memberships (challenge_id, user_id, submission_id)
  SELECT submission.challenge_id, NEW.user_id, submission.id
  FROM challenge_submissions submission
  WHERE submission.id = NEW.submission_id
    AND submission.status IN ('WAITING_CONFIRMATION', 'PENDING', 'VALIDATED');

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_challenge_active_memberships_participants
AFTER INSERT OR DELETE
ON challenge_submission_participants
FOR EACH ROW
EXECUTE FUNCTION sync_challenge_active_participant_memberships();

CREATE INDEX idx_users_scanned_id
ON users(id)
WHERE scanned = true;
