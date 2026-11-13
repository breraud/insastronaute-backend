CREATE TABLE site_availability (
    id SMALLINT PRIMARY KEY,
    player_access_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    message TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by_user_id UUID REFERENCES users(id),
    CONSTRAINT ck_site_availability_singleton CHECK (id = 1)
);

INSERT INTO site_availability (id, player_access_enabled, updated_at)
VALUES (1, TRUE, NOW());
