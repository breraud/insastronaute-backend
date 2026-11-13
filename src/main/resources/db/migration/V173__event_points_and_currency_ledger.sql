-- Les points Events, et le registre de ce qui fait bouger les deux monnaies.
--
-- Les PE sont une monnaie a part, depensee sur les evenements comme Place. Ils
-- ne s'achetent pas : ils suivent les tokens gagnes, un pour un, et deux pour un
-- quand les tokens viennent d'un defi — le geste qu'on veut le plus encourager.
--
-- Le registre, lui, manquait pour equilibrer quoi que ce soit : le solde d'un
-- joueur disait combien il avait, jamais d'ou ca venait. Sans lui, impossible
-- de savoir si le jeu donne trop face aux defis, ni de reperer un joueur qui
-- gagne en une heure ce que les autres gagnent en une semaine.
ALTER TABLE users
    ADD COLUMN event_points BIGINT NOT NULL DEFAULT 0;

ALTER TABLE users
    ADD CONSTRAINT ck_users_event_points_non_negative CHECK (event_points >= 0);

CREATE TABLE currency_movements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source VARCHAR(40) NOT NULL,
    -- Ce qui a reellement bouge, apres plafonds et arrondis : un gain ramene a
    -- zero par un solde deja vide ne doit pas gonfler les statistiques.
    token_delta BIGINT NOT NULL,
    event_points_delta BIGINT NOT NULL,
    -- Ce qui a declenche le mouvement quand il existe : partie, pack, carte.
    reference_id UUID,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_currency_movements_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_currency_movements_source CHECK (source IN (
        'CHALLENGE', 'GAME_RUN', 'PVP_BATTLE', 'COLLECTION_REWARD',
        'ADMIN_ADJUSTMENT', 'PACK_PURCHASE', 'PLACE_BONUS_PIXEL'
    )),
    CONSTRAINT ck_currency_movements_not_empty CHECK (token_delta <> 0 OR event_points_delta <> 0)
);

-- Les lectures de l'onglet stats : une fenetre de temps, puis par source ou par joueur.
CREATE INDEX idx_currency_movements_occurred ON currency_movements(occurred_at);
CREATE INDEX idx_currency_movements_source_occurred ON currency_movements(source, occurred_at);
CREATE INDEX idx_currency_movements_user_occurred ON currency_movements(user_id, occurred_at);
