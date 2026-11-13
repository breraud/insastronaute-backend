CREATE TABLE place_reset_outbox (
    id BIGSERIAL PRIMARY KEY,
    revision VARCHAR(19) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_place_reset_outbox_created_at ON place_reset_outbox(created_at);
