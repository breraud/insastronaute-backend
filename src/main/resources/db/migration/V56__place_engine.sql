ALTER TABLE users
    ADD COLUMN place_bonus_pixels_used BIGINT NOT NULL DEFAULT 0;

CREATE TABLE place_configs (
    id SMALLINT PRIMARY KEY,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    start_date TIMESTAMP WITH TIME ZONE,
    end_date TIMESTAMP WITH TIME ZONE,
    canvas_width INTEGER NOT NULL,
    canvas_height INTEGER NOT NULL,
    base_cooldown_ms BIGINT NOT NULL,
    challenge_cooldown_ms BIGINT NOT NULL,
    challenge_ratio INTEGER NOT NULL,
    CONSTRAINT ck_place_configs_singleton CHECK (id = 1),
    CONSTRAINT ck_place_configs_dimensions CHECK (canvas_width > 0 AND canvas_height > 0),
    CONSTRAINT ck_place_configs_cooldowns CHECK (base_cooldown_ms > 0 AND challenge_cooldown_ms > 0),
    CONSTRAINT ck_place_configs_ratio CHECK (challenge_ratio > 0),
    CONSTRAINT ck_place_configs_window CHECK (start_date IS NULL OR end_date IS NULL OR end_date > start_date)
);
