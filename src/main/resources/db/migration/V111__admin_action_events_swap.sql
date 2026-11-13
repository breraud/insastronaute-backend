-- Postgres ne sait pas etendre une contrainte CHECK en place : il faut la
-- supprimer et la recreer avec la liste complete.
--
-- PHYSICAL_CARD_SWAPPED : repointer une instance physique vers une autre
-- definition de carte retire une carte au proprietaire et lui en donne une
-- autre. Le geste n'a pas de contraire, d'ou une valeur seule.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED'
));
