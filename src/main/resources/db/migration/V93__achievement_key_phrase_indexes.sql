-- Indexes for the second batch of achievement key phrases. Each one backs a
-- catch-up scan predicate; the per-player progress queries are covered by
-- existing indexes on the same leading columns.

-- "Validate N treasure hunt checkpoints": the table had no per-user index.
CREATE INDEX IF NOT EXISTS idx_player_checkpoint_progress_user
    ON player_checkpoint_progress (user_id);

-- "Reach the top N of a leaderboard" looks a player up across every released
-- snapshot; the existing index leads on snapshot_id, which does not serve it.
CREATE INDEX IF NOT EXISTS idx_leaderboard_entries_subject
    ON leaderboard_entries (subject_id, rank ASC);

-- Scalar sweeps over users. Partial where it pays: only scanners matter for the
-- QR phrase, and only non-zero counters for the bonus pixel one.
CREATE INDEX IF NOT EXISTS idx_users_scanned
    ON users (id) WHERE scanned;

CREATE INDEX IF NOT EXISTS idx_users_tokens
    ON users (tokens DESC);

CREATE INDEX IF NOT EXISTS idx_users_place_bonus_pixels_used
    ON users (place_bonus_pixels_used DESC) WHERE place_bonus_pixels_used > 0;

-- "Validate a challenge N days in a row" reads every validation date of one
-- player, so the predicate is status plus user, ordered by date.
CREATE INDEX IF NOT EXISTS idx_challenge_submissions_validated
    ON challenge_submissions (user_id, validated_at)
    WHERE status = 'VALIDATED' AND validated_at IS NOT NULL;

-- Friendship and event-registration counts already ride on
-- idx_friendships_requester_status / idx_friendships_addressee_status and
-- ix_event_registrations_user_created_at.
