-- L'historique partagé des cartes jouées d'un duel.
--
-- Il est stocké plutôt que dérivé, et c'est le fond du problème qu'il résout : un client
-- pouvait déjà reconstituer la liste en accumulant les CardPlayed qu'il reçoit, mais cette
-- liste-là ne survit ni à une reconnexion ni à un RESYNC — le snapshot ne rejoue aucun
-- événement. Deux joueurs dont l'un s'est reconnecté verraient donc deux historiques
-- différents d'un historique dit « partagé ».
--
-- Rien de privé n'y entre : une carte jouée est publique, chaque CardPlayed part déjà aux
-- deux joueurs sans filtrage (StsPvpCombatBroadcaster.visibleTo ne retient que ce qui nomme
-- une carte d'une pile cachée). L'ordre de pioche et la graine, eux, restent hors de portée.
--
-- Le défaut '[]' vaut pour les duels déjà en base : ils reprennent avec un historique vide
-- plutôt qu'avec une colonne nulle que le service aurait à distinguer d'une liste vide.
alter table sts_pvp_battles
    add column card_history jsonb not null default '[]'::jsonb;
