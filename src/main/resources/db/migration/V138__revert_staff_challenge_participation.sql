-- Défaire V135 : la demande portait sur les events, pas sur les défis.
--
-- V135 n'est pas supprimée mais annulée. Elle a pu être appliquée sur un
-- environnement — la CI déploie le test à chaque passage sur `dev` — et Flyway refuse
-- de démarrer quand une migration enregistrée en base n'existe plus dans le jar.
-- Effacer le fichier casserait donc précisément l'environnement qui l'a reçue.

ALTER TABLE challenge_visibility_settings
    DROP COLUMN IF EXISTS staff_participation_blocked;

-- CHALLENGE_SUBMISSION_DELETED quitte la liste avec le geste qu'il enregistrait. Un test
-- vérifie que la contrainte et l'énumération Java disent exactement la même chose : le
-- laisser ici ferait échouer la suite au premier passage.
--
-- Aucune ligne ne peut le porter : le seul code qui l'écrivait est retiré dans le même
-- commit, et il n'a jamais été déployé.
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
