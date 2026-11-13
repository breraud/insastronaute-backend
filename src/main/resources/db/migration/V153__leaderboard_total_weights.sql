ALTER TABLE leaderboard_configs
    ADD COLUMN total_weight_percent INTEGER NOT NULL DEFAULT 20;

ALTER TABLE leaderboard_configs
    ADD CONSTRAINT ck_leaderboard_configs_total_weight_percent
    CHECK (total_weight_percent >= 0 AND total_weight_percent <= 100);
