-- Le disque des fichiers, a cote du disque systeme.
--
-- SeaweedFS a quitte la racine pour un disque a lui : la carte « Disque » ne voyait
-- plus que le systeme, et un stockage plein serait passe sans alerte. Les colonnes
-- restent nulles tant qu'aucun chemin n'est configure, comme sur un poste de dev.
ALTER TABLE health_samples
    ADD COLUMN storage_free_bytes  BIGINT,
    ADD COLUMN storage_total_bytes BIGINT;
