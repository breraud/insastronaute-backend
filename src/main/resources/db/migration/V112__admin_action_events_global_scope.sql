-- Deux gestes d'administration ont un auteur et N joueurs touches : changer le
-- bareme de points d'une rarete (ou les reglages globaux de collection), et
-- changer les points d'un defi. Une ligne par joueur ecrirait des milliers de
-- lignes pour un seul clic ; la ligne nomme donc le geste et sa portee plutot
-- qu'une personne, et n'a pas de cible.
ALTER TABLE admin_action_events ALTER COLUMN target_user_id DROP NOT NULL;

-- Postgres ne sait pas etendre une contrainte CHECK en place : il faut la
-- supprimer et la recreer avec la liste complete.
ALTER TABLE admin_action_events DROP CONSTRAINT ck_admin_action_events_action;

ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_action CHECK (action IN (
    'CHALLENGE_VALIDATED', 'CHALLENGE_REJECTED', 'CHALLENGE_GRANTED',
    'TOKENS_GRANTED', 'TOKENS_REVOKED',
    'VIRTUAL_CARDS_GRANTED', 'VIRTUAL_CARDS_REVOKED',
    'PHYSICAL_CARD_ASSIGNED', 'PHYSICAL_CARD_RELEASED',
    'PHYSICAL_CARD_SWAPPED',
    'COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED'
));

-- Rendre la colonne nullable affaiblit une garantie qui tenait jusqu'ici :
-- chaque ligne nommait un joueur. On la retablit dans le schema plutot que
-- dans le code, pour qu'une erreur de cablage echoue a l'insertion au lieu de
-- produire une ligne silencieuse et vide de sens. L'egalite entre les deux
-- predicats dit les deux sens d'un coup : cible obligatoire sur les dix types
-- nominatifs, interdite sur les deux types de portee globale.
ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_target_presence CHECK (
    (action IN ('COLLECTION_SCALE_CHANGED', 'CHALLENGE_POINTS_CHANGED')) = (target_user_id IS NULL)
);
