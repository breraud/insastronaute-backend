-- Collection points (PC) were declared in V65 but never given a scale: every
-- category sat at 0, so no card has ever been worth anything. This seeds the
-- scale, adds the duplicate threshold a common card needs, and the settings
-- that make the economy adjustable from the admin panel.

-- A common card is only worth a point every N duplicates. Everything else is
-- worth its value per duplicate, hence the default of 1.
ALTER TABLE card_categories
    ADD COLUMN duplicates_per_point INTEGER NOT NULL DEFAULT 1;

ALTER TABLE card_categories
    ADD CONSTRAINT ck_card_categories_duplicates_per_point_positive
        CHECK (duplicates_per_point >= 1);

-- Per-card override, matching the two point columns V65 already made overridable.
ALTER TABLE cards
    ADD COLUMN duplicates_per_point INTEGER;

ALTER TABLE cards
    ADD CONSTRAINT ck_cards_duplicates_per_point_positive
        CHECK (duplicates_per_point IS NULL OR duplicates_per_point >= 1);

CREATE TABLE card_collection_settings (
    id SMALLINT PRIMARY KEY,
    -- A physical card is worth its first acquisition twice over, and nothing for
    -- duplicates: holding the object is the reward, not stacking it.
    physical_first_acquisition_multiplier INTEGER NOT NULL DEFAULT 2,
    physical_duplicates_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    -- One collection point pays one token, adjustable if the economy drifts.
    tokens_per_point INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT ck_card_collection_settings_singleton CHECK (id = 1),
    CONSTRAINT ck_card_collection_settings_multiplier CHECK (physical_first_acquisition_multiplier >= 0),
    CONSTRAINT ck_card_collection_settings_tokens CHECK (tokens_per_point >= 0)
);

INSERT INTO card_collection_settings (id) VALUES (1);

-- Seed the scale by category name. translate() rather than unaccent(): the
-- latter needs an extension that may not be installed, and would not run on the
-- H2 the migration tests use. A category whose name matches nothing keeps 0 and
-- is left for the admin to fill in.
UPDATE card_categories SET first_acquisition_points = 3,  duplicate_points = 1,  duplicates_per_point = 1
WHERE translate(lower(name), 'éèêëàâäîïôöûüç', 'eeeeaaaiioouuc') LIKE 'peu commun%';

UPDATE card_categories SET first_acquisition_points = 1,  duplicate_points = 1,  duplicates_per_point = 10
WHERE translate(lower(name), 'éèêëàâäîïôöûüç', 'eeeeaaaiioouuc') LIKE 'commun%';

UPDATE card_categories SET first_acquisition_points = 8,  duplicate_points = 2,  duplicates_per_point = 1
WHERE translate(lower(name), 'éèêëàâäîïôöûüç', 'eeeeaaaiioouuc') LIKE 'rare%';

UPDATE card_categories SET first_acquisition_points = 20, duplicate_points = 5,  duplicates_per_point = 1
WHERE translate(lower(name), 'éèêëàâäîïôöûüç', 'eeeeaaaiioouuc') LIKE 'epique%';

UPDATE card_categories SET first_acquisition_points = 50, duplicate_points = 10, duplicates_per_point = 1
WHERE translate(lower(name), 'éèêëàâäîïôöûüç', 'eeeeaaaiioouuc') LIKE 'legendaire%';
