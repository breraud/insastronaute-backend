-- One-time normalization: the previous default kept discussions closed until a
-- moderator opened them. Start every existing discussion from the new open state.
UPDATE challenge_submissions
SET messages_enabled = TRUE
WHERE messages_enabled = FALSE;

ALTER TABLE challenge_submissions
    ALTER COLUMN messages_enabled SET DEFAULT TRUE;
