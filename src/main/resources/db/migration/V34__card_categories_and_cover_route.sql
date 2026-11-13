CREATE TABLE card_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO card_categories (id, name, display_order, created_at, updated_at)
SELECT
    md5(category)::uuid,
    category,
    ROW_NUMBER() OVER (ORDER BY lower(category)) - 1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT category
    FROM cards
    WHERE default_card = FALSE
) existing_categories;

ALTER TABLE cards
    ADD COLUMN category_id UUID REFERENCES card_categories(id);

UPDATE cards
SET category_id = card_categories.id
FROM card_categories
WHERE cards.default_card = FALSE
  AND lower(cards.category) = lower(card_categories.name);

UPDATE cards
SET category = 'COVER'
WHERE default_card = TRUE;
