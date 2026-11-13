-- Valider ou refuser une inscription laisse une trace.
--
-- C'est le geste qui donne le rôle STUDENT, c'est-à-dire l'accès au jeu. Il se prend à la
-- main, un compte à la fois, et rien n'en gardait la trace ailleurs que dans la fiche du
-- joueur — qui ne dit que la dernière décision, jamais la suite.
--
-- Le registre se définit comme « ce qui change ce qu'un joueur possède ». Un accès est
-- ce qu'un joueur possède de plus fondamental : sans lui, aucun des autres gestes n'a
-- de sens. Et le refus reprend ce que la validation a donné, donc les deux s'écrivent —
-- un registre où manquent les retraits laisse passer exactement ce qu'on voudrait y
-- retrouver.
--
-- Nominatives toutes les deux : elles nomment le joueur admis ou refusé.
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
    'ONBOARDING_VALIDATED', 'ONBOARDING_REJECTED'
));

-- La liste des gestes sans cible ne bouge pas : ces deux-là en ont une.
