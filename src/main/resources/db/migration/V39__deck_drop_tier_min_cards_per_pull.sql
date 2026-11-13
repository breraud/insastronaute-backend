ALTER TABLE deck_drop_tiers
    ADD COLUMN min_cards_per_pull INTEGER;

ALTER TABLE deck_drop_tiers
    ADD CONSTRAINT ck_deck_drop_tiers_min_cards_non_negative
        CHECK (min_cards_per_pull IS NULL OR min_cards_per_pull >= 0);

ALTER TABLE deck_drop_tiers
    ADD CONSTRAINT ck_deck_drop_tiers_min_cards_before_max
        CHECK (
            min_cards_per_pull IS NULL
            OR max_cards_per_pull IS NULL
            OR min_cards_per_pull <= max_cards_per_pull
        );
