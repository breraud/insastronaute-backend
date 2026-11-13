CREATE TABLE card_instances (
    id UUID PRIMARY KEY,
    card_id UUID NOT NULL REFERENCES cards(id) ON DELETE RESTRICT,
    public_id VARCHAR(17) NOT NULL UNIQUE,
    owner_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    assigned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_card_instances_public_id_format
        CHECK (public_id ~ '^[A-NP-Z1-9]{5}-[A-NP-Z1-9]{5}-[A-NP-Z1-9]{5}$'),
    CONSTRAINT ck_card_instances_assignment_consistent
        CHECK (
            (owner_user_id IS NULL AND assigned_at IS NULL)
            OR (owner_user_id IS NOT NULL AND assigned_at IS NOT NULL)
        )
);

CREATE INDEX ix_card_instances_card
    ON card_instances (card_id);

CREATE INDEX ix_card_instances_owner
    ON card_instances (owner_user_id);
