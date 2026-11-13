-- Qui a posé quel pixel.
--
-- Le canevas vit dans Redis sous forme d'octets RGB : il dit ce qui est affiché,
-- jamais par qui. Une image choquante ne pouvait donc ni être imputée ni être
-- sanctionnée — seulement recouverte, ce qui n'empêche personne de recommencer.
--
-- L'historique est en ajout seul plutôt qu'un simple « dernier propriétaire » :
-- recouvrir un pixel effacerait sinon la trace de qui l'avait posé, et il
-- suffirait de repasser dessus pour disparaître.
CREATE TABLE place_pixel_placements (
    id BIGSERIAL PRIMARY KEY,
    generation VARCHAR(64) NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    color CHAR(7) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    placed_at TIMESTAMPTZ NOT NULL
);

-- La recherche courante est « qui a posé ce pixel », du plus récent au plus
-- ancien : l'index porte donc sur la case et l'instant.
CREATE INDEX place_pixel_placements_cell_idx
    ON place_pixel_placements (generation, x, y, placed_at DESC);
CREATE INDEX place_pixel_placements_user_idx
    ON place_pixel_placements (user_id, placed_at DESC);

-- Interdire le canevas à quelqu'un sans lui fermer le reste du site : la
-- sanction porte sur ce qui a été enfreint.
ALTER TABLE users
    ADD COLUMN place_banned BOOLEAN NOT NULL DEFAULT FALSE;

-- Montrer l'auteur au survol est un choix d'organisation, pas une fatalité :
-- l'information est collectée pour la modération dans tous les cas.
ALTER TABLE place_configs
    ADD COLUMN show_pixel_authors BOOLEAN NOT NULL DEFAULT TRUE;

-- Deux gestes de modération de plus au registre : effacer une zone du canevas,
-- et fermer le canevas à quelqu'un. Le premier ne nomme personne — il efface un
-- dessin, pas un joueur —, le second nomme sa cible.
--
-- Postgres ne sait pas étendre une contrainte CHECK en place : il faut supprimer
-- les deux et les recréer avec leurs listes complètes.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED', 'PLAYER_DELETED',
    'PLACE_CANVAS_CENSORED', 'PLACE_BANNED', 'PLACE_UNBANNED'
));

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED',
                'PLAYER_DELETED', 'PLACE_CANVAS_CENSORED')) = (target_user_id IS NULL)
);
