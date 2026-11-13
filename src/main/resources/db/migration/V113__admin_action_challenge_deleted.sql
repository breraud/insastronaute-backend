-- Supprimer un defi retire ses points a chacun de ses participants : le
-- recalcul qui suit la suppression recompte le score de chacun sans lui. Le
-- geste appauvrit N joueurs d'un seul clic, et il est sans reparation - le
-- defi et ses soumissions disparaissent avec lui, il ne reste rien pour
-- reconstruire la ligne apres coup. D'ou CHALLENGE_DELETED, de portee globale
-- comme les deux valeurs arrivees en V112.
--
-- Postgres ne sait pas etendre une contrainte CHECK en place : il faut
-- supprimer les deux contraintes et les recreer avec leurs listes completes.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED'
));

-- L'egalite entre les deux predicats dit les deux sens d'un coup : cible
-- obligatoire sur les dix types nominatifs, interdite sur les trois types de
-- portee globale.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED')) = (target_user_id IS NULL)
);
