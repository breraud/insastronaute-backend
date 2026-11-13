-- Les succès ont disparu (V123), mais les notifications qu'ils avaient créées
-- sont restées avec leur type `ACHIEVEMENT_UNLOCKED`. Le type est stocké en
-- clair et relu en enum : une valeur qui n'existe plus fait échouer la lecture,
-- et c'est toute la liste de notifications qui tombe en 400 — pas seulement la
-- ligne fautive. On supprime ces lignes : le succès qu'elles annonçaient n'a
-- plus de page où mener.
DELETE FROM notifications WHERE type = 'ACHIEVEMENT_UNLOCKED';
