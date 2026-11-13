-- Quatre reglages autour des defis.

-- Un defi peut autoriser la perte de points.
--
-- L'ajustement d'une preuve refuse jusqu'ici de rendre la recompense finale
-- negative : un malus ne pouvait qu'annuler le gain, jamais mordre dessous. Le
-- cas existe pourtant -- une promo sanctionnee garde ses points d'un cote et en
-- perd de l'autre -- mais il ne doit pas etre le defaut : sur un defi ordinaire,
-- un malus mal saisi retirerait des points a qui n'a rien demande.
ALTER TABLE challenges
    ADD COLUMN allow_negative_reward BOOLEAN NOT NULL DEFAULT FALSE;

-- Une preuve que son auteur declare sensible.
--
-- Le defi sensible existait deja, decide par l'organisation pour tout le monde.
-- Celui qui envoie une photo, lui, est seul a savoir qu'elle le devient dans son
-- cas : meme mecanique, a l'echelle d'une preuve.
ALTER TABLE challenge_submissions
    ADD COLUMN sensitive BOOLEAN NOT NULL DEFAULT FALSE;

-- Un geste de moderation de plus au registre : effacer les preuves d'un defi.
--
-- Il ne nomme personne : il porte sur un defi, pas sur un joueur, comme la
-- censure du canevas. Postgres ne sait pas etendre une contrainte CHECK en
-- place, d'ou les deux ordres.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'CHALLENGE_GRACE_GRANTED', 'CHALLENGE_BONUS_ADJUSTED',
    'CHALLENGE_PROOFS_PURGED',
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

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED',
                'CHALLENGE_RESTORED', 'PLAYER_DELETED', 'PLACE_CANVAS_CENSORED',
                'PHYSICAL_CARDS_CREATED', 'CHALLENGE_PROOFS_PURGED')) = (target_user_id IS NULL)
);
