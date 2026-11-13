-- Tournament points replace the token metric on leaderboards and are earned by
-- attending a TOURNAMENT event, plus optional victory bonuses.

ALTER TABLE users
    ADD COLUMN tournament_points BIGINT NOT NULL DEFAULT 0;

-- The token-based rankings no longer describe the renamed metric, so the stale
-- snapshots are dropped instead of being silently relabelled. Entries cascade.
DELETE FROM leaderboard_snapshots
WHERE config_id IN (SELECT id FROM leaderboard_configs WHERE metric = 'TOKENS');

ALTER TABLE leaderboard_configs
    DROP CONSTRAINT ck_leaderboard_configs_metric;

UPDATE leaderboard_configs
SET metric = 'TOURNAMENT_POINTS'
WHERE metric = 'TOKENS';

ALTER TABLE leaderboard_configs
    ADD CONSTRAINT ck_leaderboard_configs_metric CHECK (metric IN (
        'TOURNAMENT_POINTS',
        'PHYSICAL_COLLECTION_POINTS',
        'VIRTUAL_COLLECTION_POINTS',
        'CHALLENGE_POINTS'
    ));

-- Attendance is tri-state: NULL means the organiser has not checked the player
-- in yet, which is different from an explicit absence.
ALTER TABLE event_registrations
    ADD COLUMN attended BOOLEAN,
    ADD COLUMN attendance_marked_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_event_registrations_event_attended
    ON event_registrations (event_id, attended);

CREATE TABLE tournament_settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    participation_points INTEGER NOT NULL DEFAULT 0,
    settled_at TIMESTAMP WITH TIME ZONE,
    settled_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_tournament_settlements_event UNIQUE (event_id),
    CONSTRAINT ck_tournament_settlements_participation CHECK (participation_points >= 0)
);

CREATE TABLE tournament_victory_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_id UUID NOT NULL REFERENCES tournament_settlements(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    label VARCHAR(80) NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    team_name VARCHAR(120),
    CONSTRAINT ck_tournament_victory_tiers_points CHECK (points >= 0),
    CONSTRAINT ck_tournament_victory_tiers_position CHECK (position >= 0)
);

CREATE INDEX idx_tournament_victory_tiers_settlement
    ON tournament_victory_tiers (settlement_id, position ASC);

CREATE TABLE tournament_tier_members (
    tier_id UUID NOT NULL REFERENCES tournament_victory_tiers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT pk_tournament_tier_members PRIMARY KEY (tier_id, user_id)
);

-- Ledger of what was actually granted, so re-settling a tournament applies the
-- difference instead of stacking points a second time.
CREATE TABLE tournament_point_awards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    participation_points INTEGER NOT NULL DEFAULT 0,
    victory_points INTEGER NOT NULL DEFAULT 0,
    total_points INTEGER NOT NULL DEFAULT 0,
    tier_label VARCHAR(80),
    team_name VARCHAR(120),
    awarded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_tournament_point_awards_event_user UNIQUE (event_id, user_id),
    CONSTRAINT ck_tournament_point_awards_total CHECK (total_points >= 0)
);

CREATE INDEX idx_tournament_point_awards_user
    ON tournament_point_awards (user_id);
