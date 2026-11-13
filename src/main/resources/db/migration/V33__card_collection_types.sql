ALTER TABLE card_collections
    ADD COLUMN collection_type VARCHAR(20) NOT NULL DEFAULT 'VIRTUAL';

ALTER TABLE card_collections
    DROP CONSTRAINT ux_card_collections_user_card;

ALTER TABLE card_collections
    ADD CONSTRAINT ux_card_collections_user_card_type
        UNIQUE (user_id, card_id, collection_type);

ALTER TABLE card_collections
    ADD CONSTRAINT ck_card_collections_type
        CHECK (collection_type IN ('VIRTUAL', 'PHYSICAL'));
