-- Supprimer definitivement une precommande laisse une trace.
--
-- Une commande annulee reste lisible : elle dit qu'un joueur avait demande quelque chose
-- puis s'est ravise. La supprimer efface cela, et c'est parfois ce qu'on veut — une
-- commande de test, un doublon. Mais une commande peut aussi porter de l'argent du, et
-- « qu'est-il arrive a ma commande ? » doit rester une question a laquelle on sait
-- repondre.
--
-- Nominative : elle nomme le joueur dont la commande disparait.
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
    'PHYSICAL_CARDS_CREATED',
    'ONBOARDING_VALIDATED', 'ONBOARDING_REJECTED',
    'SHOP_ORDER_DELETED'
));

-- La liste des gestes sans cible ne bouge pas : celui-ci en a une.
