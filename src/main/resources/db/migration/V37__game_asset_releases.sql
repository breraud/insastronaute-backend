CREATE TABLE game_asset_releases (
    id BIGINT PRIMARY KEY,
    version VARCHAR(80) NOT NULL,
    object_prefix VARCHAR(512) NOT NULL,
    base_url VARCHAR(1024) NOT NULL,
    file_count INTEGER NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
