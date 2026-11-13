ALTER TABLE card_categories
    ADD COLUMN first_acquisition_points INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN duplicate_points INTEGER NOT NULL DEFAULT 0;

ALTER TABLE cards
    ADD COLUMN first_acquisition_points INTEGER,
    ADD COLUMN duplicate_points INTEGER;

ALTER TABLE card_categories
    ADD CONSTRAINT ck_card_categories_first_acquisition_points_non_negative
        CHECK (first_acquisition_points >= 0),
    ADD CONSTRAINT ck_card_categories_duplicate_points_non_negative
        CHECK (duplicate_points >= 0);

ALTER TABLE cards
    ADD CONSTRAINT ck_cards_first_acquisition_points_non_negative
        CHECK (first_acquisition_points IS NULL OR first_acquisition_points >= 0),
    ADD CONSTRAINT ck_cards_duplicate_points_non_negative
        CHECK (duplicate_points IS NULL OR duplicate_points >= 0);
