-- The shop only sold packs. Selling a card on its own is the same purchase with a
-- single tier dropping it at 100%, so decks gained a discriminator rather than a
-- parallel entity: the balance check, the token debit and the card grant stay shared.
ALTER TABLE decks
    ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'PACK';

ALTER TABLE decks
    ADD CONSTRAINT decks_kind_check CHECK (kind IN ('PACK', 'CARD'));
