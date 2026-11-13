-- Les plans de fresque : dessiner a cote, puis poser sur le vrai canevas.
--
-- Poser un pixel coute un delai, et une fresque a plusieurs mains se decide
-- avant de se poser. Jusqu'ici ce travail se faisait ailleurs — un tableur, une
-- capture d'ecran annotee, un fichier passe de main en main — et se traduisait
-- au moment de poser, case par case, de tete.
--
-- Un plan est un dessin hors canevas, de la taille qu'on veut, que l'on peut
-- ensuite superposer au vrai canevas pour savoir quoi poser ou. Il se partage,
-- parce qu'une fresque de groupe n'a d'interet que si le groupe voit le meme
-- plan.
--
-- Le dessin est range en palette + indices plutot qu'en couleurs repetees :
-- une case ne coute alors qu'un caractere, et un plan de 128x128 tient en
-- seize kilo-octets au lieu de cent.
CREATE TABLE place_blueprints (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    -- Les couleurs employees, en #RRGGBB, dans l'ordre ou les indices les visent.
    palette TEXT NOT NULL,
    -- Une case par caractere, ligne par ligne. Le premier caractere de
    -- l'alphabet marque le vide : un plan n'est presque jamais un rectangle
    -- plein, et une case vide ne doit rien recouvrir.
    cells TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_place_blueprints_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_place_blueprints_dimensions
        CHECK (width BETWEEN 1 AND 128 AND height BETWEEN 1 AND 128)
);

CREATE INDEX idx_place_blueprints_owner ON place_blueprints(owner_user_id, updated_at DESC);

-- Le partage entre amis. La ligne dit qui a partage : un plan qui apparait sans
-- qu'on sache de qui il vient ressemble a une erreur.
CREATE TABLE place_blueprint_shares (
    blueprint_id UUID NOT NULL,
    user_id UUID NOT NULL,
    shared_by_user_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    PRIMARY KEY (blueprint_id, user_id),
    CONSTRAINT fk_place_blueprint_shares_blueprint
        FOREIGN KEY (blueprint_id) REFERENCES place_blueprints(id) ON DELETE CASCADE,
    CONSTRAINT fk_place_blueprint_shares_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_place_blueprint_shares_sharer
        FOREIGN KEY (shared_by_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_place_blueprint_shares_user ON place_blueprint_shares(user_id, created_at DESC);
