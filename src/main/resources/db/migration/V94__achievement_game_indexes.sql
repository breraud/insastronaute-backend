-- Indexes for the Aventure (STS) achievement key phrases.

-- "Finish N runs" and "reach floor N" both sweep runs grouped by owner.
CREATE INDEX IF NOT EXISTS idx_sts_runs_owner_status
    ON sts_runs (owner_id, status);

CREATE INDEX IF NOT EXISTS idx_sts_runs_owner_floor
    ON sts_runs (owner_id, current_floor DESC);

-- "Finish a run with N points" only ever looks at runs that banked a score.
CREATE INDEX IF NOT EXISTS idx_sts_runs_retirement_score
    ON sts_runs (retirement_score DESC)
    WHERE retirement_score IS NOT NULL;

-- The PvP profile is one row per player, already aggregated, so the sweeps for
-- wins and elo only need a covering order on the counters themselves.
CREATE INDEX IF NOT EXISTS idx_sts_pvp_profiles_elo
    ON sts_pvp_profiles (elo DESC);

CREATE INDEX IF NOT EXISTS idx_sts_pvp_profiles_wins
    ON sts_pvp_profiles (wins_ranked DESC);
