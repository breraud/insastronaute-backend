CREATE TABLE cards (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'DEFAULT',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_card BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    image_object_key VARCHAR(512) NOT NULL,
    thumbnail_object_key VARCHAR(512) NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    thumbnail_url VARCHAR(1024) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX ux_cards_single_default
    ON cards (default_card)
    WHERE default_card = TRUE;

CREATE INDEX ix_cards_deck_order
    ON cards (enabled, category, display_order, name);

CREATE TABLE card_collections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1,
    first_discovered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ux_card_collections_user_card UNIQUE (user_id, card_id),
    CONSTRAINT ck_card_collections_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_card_collections_user
    ON card_collections (user_id);
