-- Les succès disparaissent : leurs phrases clés vivent désormais comme défis à
-- validation automatique, où elles rapportent des jetons et déclenchent la même
-- popup que les autres défis. Deux systèmes parallèles pour un même geste — poser
-- une condition et la récompenser — n'en font pas un meilleur.
--
-- Les tables partent avec, sur décision explicite : les déblocages déjà obtenus
-- ne sont pas convertis, les défis équivalents sont recréés côté administration.
DROP TABLE IF EXISTS achievement_unlocks;
DROP TABLE IF EXISTS achievements;
