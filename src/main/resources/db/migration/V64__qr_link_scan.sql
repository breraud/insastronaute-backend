ALTER TABLE users
    ADD COLUMN scanned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE qr_link_config (
    id SMALLINT PRIMARY KEY,
    redirect_url TEXT NOT NULL,
    challenge_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT qr_link_config_singleton CHECK (id = 1)
);

INSERT INTO qr_link_config (id, redirect_url, challenge_mode_enabled)
VALUES (1, 'https://frontend.example.com/', FALSE);
