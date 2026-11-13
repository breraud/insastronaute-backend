-- Une récompense partagée entre les membres d'une équipe.
--
-- La collection physique est mutualisée par équipe : l'objectif d'un défi de collection
-- est donc atteint collectivement. Aujourd'hui chaque membre reçoit la récompense
-- entière, si bien qu'une équipe de quatre encaisse quatre fois la mise pour un seul
-- accomplissement.
--
-- Un drapeau plutôt qu'une règle générale : un défi individuel que plusieurs personnes
-- réussissent le même jour n'a aucune raison d'être divisé. Le partage est une propriété
-- du défi, pas de la façon dont il se trouve accompli.
--
-- Un point de défi valant un token, partager les points partage les tokens : il n'y a
-- qu'un réglage, pas deux.
ALTER TABLE challenges
    ADD COLUMN reward_shared BOOLEAN NOT NULL DEFAULT FALSE;
