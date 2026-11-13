CREATE TABLE decks (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    price BIGINT NOT NULL,
    cards_per_pull INTEGER NOT NULL DEFAULT 1,
    image_url VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_decks_price_positive CHECK (price >= 0),
    CONSTRAINT ck_decks_cards_per_pull_positive CHECK (cards_per_pull > 0)
);

CREATE TABLE deck_drop_tiers (
    id UUID PRIMARY KEY,
    deck_id UUID NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    label VARCHAR(120),
    drop_chance_percent NUMERIC(7, 4) NOT NULL,
    max_cards_per_pull INTEGER,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_deck_drop_tiers_chance_positive CHECK (drop_chance_percent > 0),
    CONSTRAINT ck_deck_drop_tiers_chance_max CHECK (drop_chance_percent <= 100),
    CONSTRAINT ck_deck_drop_tiers_max_cards_positive CHECK (max_cards_per_pull IS NULL OR max_cards_per_pull > 0)
);

CREATE INDEX ix_deck_drop_tiers_deck_order
    ON deck_drop_tiers (deck_id, display_order);

CREATE TABLE deck_drop_tier_cards (
    drop_tier_id UUID NOT NULL REFERENCES deck_drop_tiers(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE RESTRICT,
    card_order INTEGER NOT NULL,
    PRIMARY KEY (drop_tier_id, card_order),
    CONSTRAINT ux_deck_drop_tier_cards_card UNIQUE (drop_tier_id, card_id)
);

CREATE INDEX ix_deck_drop_tier_cards_card
    ON deck_drop_tier_cards (card_id);
