ALTER TABLE users
    ADD COLUMN hidden_from_leaderboards BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_users_hidden_from_leaderboards
    ON users (hidden_from_leaderboards);

CREATE TABLE leaderboard_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope VARCHAR(20) NOT NULL,
    metric VARCHAR(50) NOT NULL,
    refresh_delay_minutes INTEGER NOT NULL DEFAULT 60,
    auto_refresh_enabled BOOLEAN NOT NULL DEFAULT false,
    last_released_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_leaderboard_configs_scope_metric UNIQUE (scope, metric),
    CONSTRAINT ck_leaderboard_configs_scope CHECK (scope IN ('USER', 'DEPARTMENT')),
    CONSTRAINT ck_leaderboard_configs_metric CHECK (metric IN (
        'TOKENS',
        'PHYSICAL_COLLECTION_POINTS',
        'VIRTUAL_COLLECTION_POINTS',
        'CHALLENGE_POINTS'
    )),
    CONSTRAINT ck_leaderboard_configs_refresh_delay CHECK (refresh_delay_minutes >= 1)
);

CREATE TABLE leaderboard_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id UUID NOT NULL REFERENCES leaderboard_configs(id) ON DELETE CASCADE,
    released_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_leaderboard_snapshots_config_released
    ON leaderboard_snapshots (config_id, released_at DESC);

CREATE TABLE leaderboard_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES leaderboard_snapshots(id) ON DELETE CASCADE,
    rank INTEGER NOT NULL,
    subject_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    department VARCHAR(50),
    score BIGINT NOT NULL,
    CONSTRAINT ck_leaderboard_entries_rank CHECK (rank >= 1),
    CONSTRAINT ck_leaderboard_entries_score CHECK (score >= 0)
);

CREATE INDEX idx_leaderboard_entries_snapshot_rank
    ON leaderboard_entries (snapshot_id, rank ASC);

INSERT INTO leaderboard_configs (scope, metric)
VALUES
    ('USER', 'TOKENS'),
    ('USER', 'PHYSICAL_COLLECTION_POINTS'),
    ('USER', 'VIRTUAL_COLLECTION_POINTS'),
    ('USER', 'CHALLENGE_POINTS'),
    ('DEPARTMENT', 'TOKENS'),
    ('DEPARTMENT', 'PHYSICAL_COLLECTION_POINTS'),
    ('DEPARTMENT', 'VIRTUAL_COLLECTION_POINTS'),
    ('DEPARTMENT', 'CHALLENGE_POINTS');
