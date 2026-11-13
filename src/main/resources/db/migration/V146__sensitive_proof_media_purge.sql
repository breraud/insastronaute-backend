-- Les preuves des defis sensibles quittent le stockage des que la moderation a tranche.
--
-- Un defi sensible recoit des photos que personne n'a de raison de garder une fois la
-- decision prise. Les laisser dormir dans le S3 les expose sans les servir : la seule
-- duree de conservation qui se defend, c'est celle de l'examen.
--
-- La date de purge est conservee parce que la soumission, elle, reste. Sans elle, une
-- preuve purgee serait indistinguable d'une preuve qui n'a jamais eu de fichier, et les
-- ecrans afficheraient « aucun fichier » la ou il faut dire ce qui s'est passe.
ALTER TABLE challenge_submissions ADD COLUMN proof_media_purged_at TIMESTAMPTZ;

COMMENT ON COLUMN challenge_submissions.proof_media_purged_at IS
    'Quand les fichiers de preuve ont ete effaces du stockage, apres arbitrage sur un defi sensible.';
