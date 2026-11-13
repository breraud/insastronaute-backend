-- Fabriquer des cartes physiques laisse une trace.
--
-- Une carte physique est une denrée rare : elle est imprimée, elle circule, et elle ne
-- se recrée pas d'un clic. Or `createBatch` en fabrique jusqu'à mille d'un coup, et une
-- série d'impression bien davantage, sans que rien ne dise qui, quoi, ni combien.
--
-- Le registre ne couvrait pas ce geste parce qu'il se définit comme « ce qui change ce
-- qu'un joueur possède » : une instance sans propriétaire ne change ce que personne ne
-- possède, et la trace n'apparaissait qu'à l'attribution. C'est cohérent, mais ça laisse
-- la production elle-même hors de vue.
--
-- D'où une action de portée globale : elle a un auteur et aucune cible, puisque ces
-- exemplaires n'appartiennent à personne au moment où ils naissent.
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
    'PHYSICAL_CARDS_CREATED'
));

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED',
                'PLAYER_DELETED', 'PLACE_CANVAS_CENSORED', 'PHYSICAL_CARDS_CREATED')) = (target_user_id IS NULL)
);
