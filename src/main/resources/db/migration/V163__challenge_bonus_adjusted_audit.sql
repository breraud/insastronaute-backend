-- Les points bonus d'un defi deja valide, revus apres coup.
--
-- La part discretionnaire de la decision se posait a la validation, et jamais
-- apres : accorder un bonus oublie demandait de refuser la preuve pour la
-- revalider, donc d'annoncer au joueur un refus qui n'en etait pas un, et
-- parfois d'effacer sa preuve au passage. Le geste existe maintenant pour
-- lui-meme, et il se trace.
--
-- La contrainte se reecrit en entier : elle enumere les gestes, et en ajouter un
-- sans reprendre la liste effacerait ceux ajoutes entre-temps.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'CHALLENGE_GRACE_GRANTED', 'CHALLENGE_BONUS_ADJUSTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED', 'PLAYER_DELETED',
    'PLACE_CANVAS_CENSORED', 'PLACE_BANNED', 'PLACE_UNBANNED',
    'PHYSICAL_CARDS_CREATED',
    'ONBOARDING_VALIDATED', 'ONBOARDING_REJECTED',
    'SHOP_ORDER_DELETED'
));
