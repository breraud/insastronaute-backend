-- Retirer tous les défis d'un coup, le temps d'une refonte ou d'une pause dans
-- l'intégration, sans avoir à toucher aux deux cents défis un par un ni à
-- risquer d'en oublier un en les rallumant.
CREATE TABLE challenge_visibility_settings (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_challenge_visibility_singleton CHECK (id)
);

INSERT INTO challenge_visibility_settings (id, hidden) VALUES (TRUE, FALSE);
