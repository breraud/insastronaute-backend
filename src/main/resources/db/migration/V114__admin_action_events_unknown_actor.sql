-- Un auteur peut manquer, mais seulement sur une ligne reprise.
--
-- La reprise des decisions de defi passees identifie le moderateur en
-- re-hachant le keycloak_sub de chaque utilisateur et en comparant le resultat
-- a moderation_audit_events.moderator_hash. Un moderateur parti, dont la ligne
-- users a disparu, ne hache plus vers rien : sa decision n'a plus d'auteur
-- nommable. La specification veut qu'elle soit reprise avec un auteur inconnu
-- plutot qu'omise - une ligne sans auteur reste une information, son absence
-- n'en est pas une.
ALTER TABLE admin_action_events ALTER COLUMN actor_user_id DROP NOT NULL;

-- Rendre la colonne nullable ouvrirait la porte a une ligne ecrite en direct
-- sans auteur, ce qui viderait le registre de sa raison d'etre. On la referme
-- dans le schema : l'absence d'auteur n'est permise que sur une ligne reprise.
--
-- Le predicat est total - il ne peut jamais valoir NULL et passer par
-- accident. backfilled est NOT NULL, donc (backfilled = TRUE) vaut TRUE ou
-- FALSE, jamais NULL ; et (actor_user_id IS NOT NULL) vaut toujours TRUE ou
-- FALSE, y compris sur une colonne nulle. Un CHECK qui s'evalue a NULL est
-- accepte par Postgres : ecrire actor_user_id <> NULL, par exemple, laisserait
-- passer exactement les lignes qu'on veut refuser.
ALTER TABLE admin_action_events ADD CONSTRAINT ck_admin_action_events_actor_presence CHECK (
    (backfilled = TRUE) OR (actor_user_id IS NOT NULL)
);
