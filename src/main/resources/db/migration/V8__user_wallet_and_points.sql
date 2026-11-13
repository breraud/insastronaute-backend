ALTER TABLE users
    ADD COLUMN tokens BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN virtual_collection_points BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN physical_collection_points BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN challenge_points BIGINT NOT NULL DEFAULT 0;
