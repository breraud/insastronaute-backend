-- PLAYER_DELETED : un geste qui ne nomme pas sa cible par une clé étrangère.
--
-- Les autres actions pointent vers le joueur concerné. Celle-ci ne le peut pas : la
-- ligne du joueur disparaît parfois avec le geste, et une cible en ON DELETE RESTRICT
-- empêcherait précisément la suppression qu'elle enregistre — la trace interdirait ce
-- qu'elle trace.
--
-- Le nom et l'e-mail voyagent donc dans les détails, ce qui a l'avantage de rester
-- lisible même quand la ligne du joueur n'existe plus. C'est la raison pour laquelle
-- l'action rejoint la liste sans cible, aux côtés des gestes de portée globale.
--
-- Postgres ne sait pas étendre un CHECK en place : les deux contraintes se suppriment
-- et se recréent entières.

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED',
    'CHALLENGE_DELETED',
    'PLAYER_DELETED'
));

ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_target_presence;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED', 'CHALLENGE_DELETED', 'PLAYER_DELETED'))
        = (target_user_id IS NULL)
);
