ALTER TABLE card_collections
    DROP CONSTRAINT card_collections_card_id_fkey;

ALTER TABLE card_collections
    ADD CONSTRAINT card_collections_card_id_fkey
        FOREIGN KEY (card_id) REFERENCES cards(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE;

UPDATE cards
SET id = '00000000-0000-0000-0000-000000000000'
WHERE default_card = TRUE
  AND id <> '00000000-0000-0000-0000-000000000000'
  AND NOT EXISTS (
      SELECT 1
      FROM cards fixed_default_card
      WHERE fixed_default_card.id = '00000000-0000-0000-0000-000000000000'
  );
