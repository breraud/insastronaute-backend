-- L'audit de moderation survit a ce qu'il raconte.
--
-- V167 vient de poser une cascade sur cette cle etrangere pour debloquer la
-- suppression d'un defi. Elle debloquait, mais au prix de la trace : supprimer un
-- defi effacait qui avait ouvert quel media de ses preuves, et a quelle heure.
-- C'est precisement ce que cette table existe pour retenir.
--
-- La bonne forme pour une table d'audit n'est ni RESTRICT ni CASCADE, c'est
-- l'absence de cle etrangere. Une trace ne doit pas dependre de la survie de ce
-- qu'elle trace -- ni l'empecher de disparaitre, ni disparaitre avec lui.
-- `submission_id` reste, comme identifiant historique plutot que comme reference
-- vivante : c'est par lui que ces lignes se lisent, et c'est la seule chose qui
-- les relie entre elles.
--
-- `submission_deleted_at` porte la mention. Sans elle, une ligne orpheline ne se
-- distingue pas d'une ligne vivante, et personne ne peut dire en la lisant que la
-- soumission n'existe plus. Elle est posee par le code avant la suppression, pas
-- par la base : PostgreSQL ne sait pas estampiller ce qu'il cascade.
--
-- Le meme blocage valait pour la suppression definitive d'un joueur -- users ->
-- challenge_submissions -> audit cascadent aussi -- et renvoyait la meme 500, sans
-- que l'ecran puisse meme en nommer la cause.
ALTER TABLE moderation_audit_events
    DROP CONSTRAINT IF EXISTS moderation_audit_events_submission_id_fkey;

ALTER TABLE moderation_audit_events
    ADD COLUMN submission_deleted_at TIMESTAMP WITH TIME ZONE;
