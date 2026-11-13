CREATE TABLE card_types (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE cards ADD COLUMN type_id UUID;
ALTER TABLE cards
    ADD CONSTRAINT fk_cards_type
    FOREIGN KEY (type_id) REFERENCES card_types(id) ON DELETE SET NULL;
CREATE INDEX idx_cards_type_id ON cards(type_id);
