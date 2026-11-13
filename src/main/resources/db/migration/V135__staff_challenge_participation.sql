-- Fermer les défis au staff, et pouvoir retirer une soumission à la main.
--
-- Le staff joue sur le même site que les étudiants et peut donc soumettre des défis,
-- alors qu'il est souvent celui qui les valide. L'interrupteur se pose à côté de
-- `hidden` : c'est le même geste d'administration, sur le même écran, et il ne modifie
-- aucun défi — le rouvrir rend exactement ce qu'il y avait.
--
-- Rien n'est retiré rétroactivement. Les soumissions déjà déposées par du staff restent,
-- avec leurs points : les effacer d'un clic ferait disparaître un travail réel sans que
-- personne l'ait décidé cas par cas. C'est justement à quoi sert la suppression manuelle
-- ajoutée en même temps.
ALTER TABLE challenge_visibility_settings
    ADD COLUMN staff_participation_blocked BOOLEAN NOT NULL DEFAULT FALSE;

-- Retirer une soumission à la main retire aussi les points qu'elle avait rapportés à
-- chacun de ses participants. Le registre doit donc en garder trace, comme de tout geste
-- qui change ce qu'un joueur possède.
--
-- Postgres ne sait pas étendre une contrainte CHECK en place : les deux se suppriment et
-- se recréent entières.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED', 'PLAYER_DELETED',
    'PLACE_CANVAS_CENSORED', 'PLACE_BANNED', 'PLACE_UNBANNED',
    'CHALLENGE_SUBMISSION_DELETED'
));

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED',
                'PLAYER_DELETED', 'PLACE_CANVAS_CENSORED')) = (target_user_id IS NULL)
);
