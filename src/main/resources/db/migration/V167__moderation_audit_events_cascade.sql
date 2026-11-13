-- Supprimer un defi butait sur son propre audit de moderation.
--
-- `DELETE FROM challenges` se propage en base jusqu'aux soumissions, et de la a
-- tout ce qui les reference : participants, messages, reclamations, progression,
-- graces. Six contraintes, cinq en ON DELETE CASCADE -- et celle de V72, ecrite
-- sans clause, donc RESTRICT par defaut. Consequence : tout defi dont une preuve
-- a ete moderee ou seulement consultee devenait indestructible, avec une 500 pour
-- seule explication.
--
-- Le choix de la cascade plutot que d'un SET NULL : ces lignes ne se lisent que
-- par `submission_id` (getSubmissionAudit). Detachees de leur soumission, elles ne
-- sont plus atteignables par personne -- du poids mort, pas une trace. Ce qui
-- reste de la suppression elle-meme est ailleurs, dans admin_action_events, avec
-- le nombre de joueurs qu'elle a touches.
--
-- A noter : supprimer un defi efface donc desormais le detail de qui a consulte
-- quel media de ses preuves. C'est la contrepartie assumee -- l'autre branche
-- etait de ne jamais pouvoir supprimer le defi.
--
-- Le nom est celui que PostgreSQL a genere pour la contrainte implicite de V72.
ALTER TABLE moderation_audit_events
    DROP CONSTRAINT IF EXISTS moderation_audit_events_submission_id_fkey;

ALTER TABLE moderation_audit_events
    ADD CONSTRAINT moderation_audit_events_submission_id_fkey
    FOREIGN KEY (submission_id) REFERENCES challenge_submissions(id) ON DELETE CASCADE;
