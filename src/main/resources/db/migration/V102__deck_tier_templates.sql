-- Rebuilding the same tier structure card by card for every new pack is the
-- slowest part of opening a shop. A template records one arrangement so it can
-- be poured into another pack, and it lives beside decks rather than inside one:
-- deleting the pack it came from must not take the template with it.
CREATE TABLE deck_tier_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    cards_per_pull INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_deck_tier_templates_cards_per_pull CHECK (cards_per_pull > 0)
);

-- Two templates sharing a name would be indistinguishable in the picker, which
-- is the only place they are ever chosen from.
CREATE UNIQUE INDEX ux_deck_tier_templates_name
    ON deck_tier_templates (LOWER(name));

CREATE TABLE deck_tier_template_tiers (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES deck_tier_templates(id) ON DELETE CASCADE,
    label VARCHAR(120),
    drop_chance_percent NUMERIC(7, 4) NOT NULL,
    max_cards_per_pull INTEGER,
    min_cards_per_pull INTEGER,
    display_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_deck_tier_template_tiers_chance_positive CHECK (drop_chance_percent > 0),
    CONSTRAINT ck_deck_tier_template_tiers_chance_max CHECK (drop_chance_percent <= 100),
    CONSTRAINT ck_deck_tier_template_tiers_max_cards CHECK (max_cards_per_pull IS NULL OR max_cards_per_pull > 0),
    CONSTRAINT ck_deck_tier_template_tiers_min_cards CHECK (min_cards_per_pull IS NULL OR min_cards_per_pull > 0)
);

CREATE INDEX ix_deck_tier_template_tiers_template_order
    ON deck_tier_template_tiers (template_id, display_order);

-- A card removed from the catalogue simply leaves the templates that named it,
-- unlike a live pack, which refuses the deletion. A template is a draft: losing
-- one card from it costs a re-pick, not a broken shop.
CREATE TABLE deck_tier_template_cards (
    template_tier_id UUID NOT NULL REFERENCES deck_tier_template_tiers(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE CASCADE,
    card_order INTEGER NOT NULL,
    PRIMARY KEY (template_tier_id, card_order),
    CONSTRAINT ux_deck_tier_template_cards_card UNIQUE (template_tier_id, card_id)
);

CREATE INDEX ix_deck_tier_template_cards_card
    ON deck_tier_template_cards (card_id);
