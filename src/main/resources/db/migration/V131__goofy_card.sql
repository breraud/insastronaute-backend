-- Une carte « goofy » cachée derrière un code : qui le saisit la reçoit, une
-- seule fois, avec une animation d'obtention à part. Le code se fixe côté
-- administration, comme la carte qu'il donne.
CREATE TABLE goofy_card_settings (
    id SMALLINT PRIMARY KEY DEFAULT 1,
    card_id UUID REFERENCES cards(id) ON DELETE SET NULL,
    code VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT goofy_card_settings_singleton CHECK (id = 1)
);

INSERT INTO goofy_card_settings (id, enabled) VALUES (1, FALSE);

-- La clé primaire porte la règle « une fois par personne » : deux saisies
-- simultanées ne peuvent pas donner deux cartes, la seconde insertion échoue.
CREATE TABLE goofy_card_redemptions (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    redeemed_at TIMESTAMPTZ NOT NULL
);
