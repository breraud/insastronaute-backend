-- Supprimer un defi devient reversible.
--
-- Le geste effacait le defi, et la base cascadait : preuves, participants,
-- conversations, reclamations, graces, progression. Rien ne pouvait revenir, et
-- les points des joueurs credites partaient avec -- sur un clic de trop, il n'y
-- avait aucune sortie.
--
-- `deleted_at` remplace l'effacement. Rien ne part, tout redevient visible en
-- remettant ce champ a NULL. Les points se reglent tout seuls : la requete de
-- recalcul joint le defi de chaque preuve validee et ecarte desormais les defis
-- supprimes, donc les points tombent a la suppression et reviennent a la
-- restauration, sans qu'aucune ligne n'ait bouge.
--
-- `deleted_by_keycloak_sub` dit qui a supprime. Le registre d'administration le
-- dit aussi, mais la liste des defis supprimes doit pouvoir l'afficher sans aller
-- fouiller une autre table.
--
-- L'index est partiel : la corbeille se lit sur une poignee de lignes, et un index
-- plein couterait sur chaque ecriture de la table pour les indexer toutes a NULL.
ALTER TABLE challenges
    ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deleted_by_keycloak_sub VARCHAR(64);

CREATE INDEX idx_challenges_deleted_at ON challenges(deleted_at) WHERE deleted_at IS NOT NULL;

-- La restauration se trace, comme la suppression. Les deux contraintes enumerent
-- les gestes en dur, et en ajouter un sans reprendre la liste entiere effacerait
-- ceux ajoutes entre-temps.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'CHALLENGE_GRACE_GRANTED', 'CHALLENGE_BONUS_ADJUSTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED', 'CHALLENGE_RESTORED', 'PLAYER_DELETED',
    'PLACE_CANVAS_CENSORED', 'PLACE_BANNED', 'PLACE_UNBANNED',
    'PHYSICAL_CARDS_CREATED',
    'ONBOARDING_VALIDATED', 'ONBOARDING_REJECTED',
    'SHOP_ORDER_DELETED'
));

-- La restauration s'ecrit sans cible, comme la suppression : elle porte sur un
-- defi, pas sur un joueur.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED',
                'CHALLENGE_RESTORED', 'PLAYER_DELETED', 'PLACE_CANVAS_CENSORED',
                'PHYSICAL_CARDS_CREATED')) = (target_user_id IS NULL)
);
